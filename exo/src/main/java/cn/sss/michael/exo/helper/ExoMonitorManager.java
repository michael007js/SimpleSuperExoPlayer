package cn.sss.michael.exo.helper;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import cn.sss.michael.exo.ExoConfig;
import cn.sss.michael.exo.util.ExoLog;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Michael by 61642
 * @date 2025/12/25 11:18
 * @Description 监控任务管理器
 */
public class ExoMonitorManager {
    // 弱引用持有页面
    private final WeakReference<Context> mContextRef;
    // 页面是否存活标记（避免页面销毁后执行任务）
    private final AtomicBoolean mIsContextAlive = new AtomicBoolean(false);
    // 任务运行状态（启动/停止）
    private final AtomicBoolean mIsRunning = new AtomicBoolean(false);
    // 任务暂停状态（暂停/恢复）
    private final AtomicBoolean mIsPaused = new AtomicBoolean(false);
    // 核心组件
    private ScheduledExecutorService mExecutor;
    private ScheduledFuture<?> mTaskFuture;
    private ExecutorService mTimeoutExecutor; // 复用超时保护线程池，避免重复创建
    private final MainThreadHandler mMainHandler; // 主线程Handler（避免内存泄漏）

    // 监控指标
    private final AtomicLong mTotalCount = new AtomicLong(0);    // 总执行次数
    private final AtomicLong mFailCount = new AtomicLong(0);     // 失败次数

    // 主线程任务回调（外部可设置，用于接收监控结果并更新UI）
    public interface MainThreadCallback {
        /**
         * 主线程回调方法（可直接操作UI）
         *
         * @param success  是否执行成功
         * @param costTime 执行耗时（ms）
         * @param extra    额外数据（可自定义传递）
         */
        void onMonitorResult(boolean success, long costTime, @Nullable Object extra);
    }

    private MainThreadCallback mMainThreadCallback;

    // 静态弱引用Handler，避免内存泄漏
    private static class MainThreadHandler extends Handler {
        private final WeakReference<ExoMonitorManager> mManagerRef;

        public MainThreadHandler(Looper looper, ExoMonitorManager manager) {
            super(looper);
            mManagerRef = new WeakReference<>(manager);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            ExoMonitorManager manager = mManagerRef.get();
            if (manager == null || !manager.mIsContextAlive.get()) {
                return;
            }

            if (msg.what == ExoConfig.MONITOR_MSG_MAIN_THREAD_TASK && msg.obj instanceof MainThreadCallback) {
                try {
                    // 解析回调参数
                    boolean success = msg.arg1 == 1;
                    long costTime = msg.arg2;
                    Object extra = msg.getData() != null ? msg.getData().getSerializable("extra") : null;
                    // 执行主线程回调（可直接操作UI）
                    ((MainThreadCallback) msg.obj).onMonitorResult(success, costTime, extra);
                } catch (Exception e) {
                    ExoLog.log("主线程回调执行失败", e);
                }
            }
        }
    }

    public ExoMonitorManager(@NonNull Context context) {
        this.mContextRef = new WeakReference<>(context);
        if (context.getApplicationContext() == context) {
            throw new IllegalArgumentException("禁止传入 Application Context，请传入页面级 Context（Activity/Fragment/View）");
        }
        // 初始化主线程Handler
        mMainHandler = new MainThreadHandler(Looper.getMainLooper(), this);
        // 初始化超时保护线程池（复用，避免每次创建新线程）
        mTimeoutExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, ExoConfig.MONITOR_THREAD_NAME_PREFIX + "Timeout-");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        });
    }

    /**
     * 设置主线程回调（用于更新UI）
     *
     * @param callback 主线程回调接口
     */
    public void setMainThreadCallback(MainThreadCallback callback) {
        this.mMainThreadCallback = callback;
    }

    /**
     * 启动页面监控任务
     *
     * @param monitorRunnable 自定义监控逻辑（子线程执行，不可直接操作UI）
     */
    public void startMonitor(@NonNull Runnable monitorRunnable) {
        Context context = mContextRef.get();
        // 校验：Context 已回收/页面已销毁/任务已运行 → 直接返回
        if (context == null || isPageDestroyed(context) || mIsRunning.get()) {
            ExoLog.log("监控任务启动失败：Context 已回收/页面已销毁/任务已运行");
            return;
        }

        mIsContextAlive.set(true);
        mIsRunning.set(true);
        mIsPaused.set(false); // 启动时重置暂停状态

        mExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, ExoConfig.MONITOR_THREAD_NAME_PREFIX + context.getClass().getSimpleName());
            thread.setDaemon(true); // 守护线程，页面销毁后不阻塞应用
            thread.setPriority(Thread.NORM_PRIORITY - 2); // 极低优先级，不影响页面UI
            return thread;
        });

        // scheduleWithFixedDelay 防任务堆积
        mTaskFuture = mExecutor.scheduleWithFixedDelay(
                () -> executeMonitorTask(monitorRunnable),
                0, // 初始延迟：立即执行
                ExoConfig.MONITOR_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );

        ExoLog.log("页面监控任务启动成功：" + context.getClass().getSimpleName());
    }

    /**
     * 暂停监控任务
     */
    public void pauseMonitor() {
        // 校验：任务未运行/已暂停 → 直接返回
        if (!mIsRunning.get() || mIsPaused.get()) {
            ExoLog.log("监控任务暂停失败：任务未运行/已处于暂停状态");
            return;
        }

        mIsPaused.set(true);
        ExoLog.log("监控任务已暂停：" + (mContextRef.get() != null ? mContextRef.get().getClass().getSimpleName() : "Context已回收"));
    }

    /**
     * 恢复监控任务
     * 从暂停状态恢复，继续执行监控逻辑，无需重新启动任务
     */
    public void resumeMonitor() {
        // 校验：任务未运行/未暂停 → 直接返回
        if (!mIsRunning.get() || !mIsPaused.get()) {
            ExoLog.log("监控任务恢复失败：任务未运行/未处于暂停状态，初次失败可能于Activity/Fragment的onResume中触发");
            return;
        }

        mIsPaused.set(false);
        ExoLog.log("监控任务已恢复：" + (mContextRef.get() != null ? mContextRef.get().getClass().getSimpleName() : "Context已回收"));
    }

    /**
     * 停止监控任务
     */
    public void stopMonitor() {
        if (!mIsRunning.get()) {
            return;
        }

        // 标记任务停止，重置所有状态
        mIsRunning.set(false);
        mIsContextAlive.set(false);
        mIsPaused.set(false);

        // 清空主线程Handler消息队列，避免内存泄漏
        mMainHandler.removeCallbacksAndMessages(null);

        // 取消定时任务（不中断正在执行的任务，避免数据异常）
        if (mTaskFuture != null) {
            mTaskFuture.cancel(false);
            mTaskFuture = null;
        }

        // 优雅关闭定时线程池
        if (mExecutor != null) {
            mExecutor.shutdown();
            try {
                if (!mExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    mExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                mExecutor.shutdownNow();
            }
            mExecutor = null;
        }

        // 优雅关闭超时保护线程池
        if (mTimeoutExecutor != null) {
            mTimeoutExecutor.shutdown();
            try {
                if (!mTimeoutExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    mTimeoutExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                mTimeoutExecutor.shutdownNow();
            }
            mTimeoutExecutor = null;
        }

        Context context = mContextRef.get();
        ExoLog.log("页面监控任务已停止：" + (context != null ? context.getClass().getSimpleName() : "Context已回收") +
                " | 执行统计：总次数=" + mTotalCount.get() + " 失败次数=" + mFailCount.get());
    }

    /**
     * 执行监控任务
     */
    private void executeMonitorTask(Runnable monitorRunnable) {
        // 前置校验Context 已回收/页面已销毁 → 停止任务
        Context context = mContextRef.get();
        if (context == null || !mIsContextAlive.get() || isPageDestroyed(context)) {
            stopMonitor();
            return;
        }

        // 前置校验任务已暂停 → 跳过执行，直接返回（暂停逻辑）
        if (mIsPaused.get()) {
            ExoLog.log("监控任务处于暂停状态，跳过本次执行");
            return;
        }

        long startTime = System.currentTimeMillis();
        mTotalCount.incrementAndGet();
        boolean isSuccess = false;
        Object extraData = null; // 可自定义传递到主线程的额外数据

        try {
            // 超时保护：单个任务执行超时强制终止（避免阻塞线程）
            mTimeoutExecutor.submit(() -> {
                try {
                    if (monitorRunnable != null) {
                        monitorRunnable.run();
                    }
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }).get(ExoConfig.MONITOR_TASK_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            isSuccess = true;
            long costTime = System.currentTimeMillis() - startTime;
//            ExoLog.log("监控任务执行成功，耗时(ms)=" + costTime);

        } catch (Exception e) {
            mFailCount.incrementAndGet();
            long costTime = System.currentTimeMillis() - startTime;
            ExoLog.log("监控任务执行失败，耗时(ms)=" + costTime, e);
            ExoLog.log("页面监控任务失败（主线程）", e);
        } finally {
            // 发送结果到主线程（更新UI）
            postToMainThread(isSuccess, System.currentTimeMillis() - startTime, extraData);
        }
    }

    /**
     * 发送监控结果到主线程（主线程更新UI的入口）
     *
     * @param success  是否成功
     * @param costTime 耗时
     * @param extra    额外数据
     */
    private void postToMainThread(boolean success, long costTime, @Nullable Object extra) {
        if (mMainThreadCallback == null || !mIsContextAlive.get()) {
            return;
        }

        Message msg = mMainHandler.obtainMessage(ExoConfig.MONITOR_MSG_MAIN_THREAD_TASK);
        msg.arg1 = success ? 1 : 0; // 1=成功，0=失败
        msg.arg2 = (int) costTime;
        msg.obj = mMainThreadCallback;

        if (extra != null) {
            android.os.Bundle bundle = new android.os.Bundle();
            bundle.putSerializable("extra", (java.io.Serializable) extra);
            msg.setData(bundle);
        }

        mMainHandler.sendMessage(msg);
    }

    /**
     * 直接提交主线程任务（外部可调用，手动更新UI）
     *
     * @param runnable 主线程执行的任务
     */
    public void postMainThreadTask(@NonNull Runnable runnable) {
        if (mIsContextAlive.get()) {
            mMainHandler.post(runnable);
        }
    }

    /**
     * 校验页面是否已销毁
     */
    private boolean isPageDestroyed(@NonNull Context context) {
        if (context instanceof android.app.Activity) {
            android.app.Activity activity = (android.app.Activity) context;
            // 适配低版本：Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 才支持 isDestroyed()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                return activity.isFinishing() || activity.isDestroyed();
            } else {
                return activity.isFinishing();
            }
        }
        // Fragment Context 校验（Fragment#getContext() 会返回宿主Activity，复用上面的逻辑）
        return false;
    }

    /**
     * 判断监控任务是否运行中（区分“运行中-未暂停”和“运行中-暂停”）
     */
    public boolean isRunning() {
        return mIsRunning.get() && mIsContextAlive.get() && mContextRef.get() != null;
    }

    /**
     * 判断监控任务是否处于暂停状态
     */
    public boolean isPaused() {
        return mIsRunning.get() && mIsPaused.get();
    }
}