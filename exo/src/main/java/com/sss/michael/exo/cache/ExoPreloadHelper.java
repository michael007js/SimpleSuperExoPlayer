package com.sss.michael.exo.cache;

import android.content.Context;
import android.text.TextUtils;

import androidx.media3.common.util.UnstableApi;

import com.sss.michael.exo.util.ExoLog;
import com.sss.michael.exo.util.ExoNetworkUtil;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Michael by 61642
 * @date 2025/12/30 16:52
 * @Description 预加载助手
 */
@UnstableApi
public class ExoPreloadHelper {
    private static volatile ExoPreloadHelper instance;
    private final WeakReference<Context> mContextRef;
    private ExecutorService mExecutorService;
    private final Map<String, PreloadTask> mPreloadTasks = new LinkedHashMap<>();
    private ExoCacheConfig mCacheConfig;
    private ExoPreloadCallback mGlobalPreloadCallback; // 全局预加载回调

    private ExoPreloadHelper(Context context, ExoCacheConfig cacheConfig) {
        if (context.getApplicationContext() != context) {
            throw new IllegalArgumentException("Application context is required.");
        }
        this.mContextRef = new WeakReference<>(context.getApplicationContext());
        this.mCacheConfig = cacheConfig != null ? cacheConfig : ExoCacheConfig.getDefaultConfig();
        this.mExecutorService = createExecutor(mCacheConfig);
    }

    /**
     * 获取单例（支持自定义配置）
     *
     * @param context     上下文
     * @param cacheConfig 缓存配置（可为null，使用默认配置）
     * @return 单例实例
     */
    public static ExoPreloadHelper getInstance(Context context, ExoCacheConfig cacheConfig) {
        if (instance == null) {
            synchronized (ExoPreloadHelper.class) {
                if (instance == null) {
                    instance = new ExoPreloadHelper(context, cacheConfig);
                }
            }
        }
        return instance;
    }

    /**
     * 简化获取单例（使用默认配置）
     */
    public static ExoPreloadHelper getInstance(Context context) {
        return getInstance(context, null);
    }

    /**
     * 更新缓存配置并重建执行器
     */
    public synchronized void updateCacheConfig(ExoCacheConfig newConfig) {
        if (newConfig == null) {
            return;
        }
        this.mCacheConfig = newConfig;
        // 重建线程池
        shutdownExecutor();
        this.mExecutorService = createExecutor(mCacheConfig);
    }

    private Context getContext() {
        return mContextRef.get();
    }

    /**
     * 设置全局预加载回调
     */
    public void setGlobalPreloadCallback(ExoPreloadCallback callback) {
        this.mGlobalPreloadCallback = callback;
    }

    /**
     * 更新预加载队列
     */
    public synchronized void resumePreload(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            ExoLog.log("Skip preload: urls is empty");
            return;
        }
        Context context = getContext();
        if (context == null) {
            ExoLog.log("Skip preload: context is null");
            return;
        }
        if (ExoCacheManager.getConfig().getCacheDir() == null) {
            ExoLog.log("Skip preload: cache dir is not configured");
            return;
        }

        // 如果执行器已经被销毁，则在真正派发任务前重新创建
        ensureExecutor();

        // 网络适配：仅WiFi下开启预加载
        if (!ExoNetworkUtil.isWifiConnected(context)) {
            ExoLog.log("Skip preload: wifi is unavailable");
            stopAll();
            return;
        }

        // 网络质量判断：网络差时减少并行任务数
        int actualMaxTaskCount = mCacheConfig.getMaxPreloadTaskCount();
        if (ExoNetworkUtil.isNetworkPoor(context)) {
            actualMaxTaskCount = Math.max(1, actualMaxTaskCount / 2); // 网络差时任务数减半
            ExoLog.log("Poor network detected, reduce max preload tasks to " + actualMaxTaskCount);
        }

        // 清理过期任务
        Iterator<Map.Entry<String, PreloadTask>> iterator = mPreloadTasks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, PreloadTask> entry = iterator.next();
            if (!urls.contains(entry.getKey())) {
                entry.getValue().cancel();
                iterator.remove();
                ExoLog.log("Stop outdated preload task: " + entry.getKey());
            }
        }

        // 添加并启动新任务（避免重复预加载）
        for (String url : urls) {
            if (TextUtils.isEmpty(url)) {
                continue;
            }
            // 缓存命中判断：已缓存完成，跳过预加载
            if (ExoCacheManager.isCacheCompleted(context, url)) {
                ExoLog.log("Cache hit, skip preload: " + url);
                // 回调缓存完成状态
                if (mGlobalPreloadCallback != null) {
                    mGlobalPreloadCallback.onPreloadSuccess(url);
                }
                continue;
            }
            // 避免重复任务
            if (mPreloadTasks.containsKey(url)) {
                continue;
            }
            // 控制最大并行任务数
            if (mPreloadTasks.size() >= actualMaxTaskCount) {
                ExoLog.log("Skip new preload task: max concurrent task count reached");
                break;
            }

            // 创建预加载任务，并在任务终态时自动从运行中任务列表移除
            PreloadTask task = createTrackedTask(context, url, mGlobalPreloadCallback);
            mPreloadTasks.put(url, task);
            task.execute();
            ExoLog.log("Start preload task: " + url);
        }
    }

    /**
     * 停止所有预加载任务
     */
    public synchronized void stopAll() {
        for (PreloadTask task : mPreloadTasks.values()) {
            task.cancel();
        }
        mPreloadTasks.clear();
        ExoLog.log("Stopped all preload tasks");
    }

    /**
     * 停止单个URL的预加载
     */
    public synchronized void stopPreload(String url) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        PreloadTask task = mPreloadTasks.get(url);
        if (task != null) {
            task.cancel();
            mPreloadTasks.remove(url);
            ExoLog.log("Stopped preload task: " + url);
        }
    }

    /**
     * 关闭线程池
     */
    public synchronized void shutdownExecutor() {
        if (mExecutorService == null) {
            return;
        }
        mExecutorService.shutdown();
        try {
            if (!mExecutorService.awaitTermination(1, TimeUnit.SECONDS)) {
                mExecutorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            mExecutorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        mExecutorService = null;
    }

    /**
     * 任务结束时回收任务引用，保证任务表只保存“仍在运行中的任务”
     */
    private synchronized void onTaskFinished(String url, PreloadTask task) {
        if (mPreloadTasks.get(url) == task) {
            mPreloadTasks.remove(url);
        }
    }

    /**
     * 确保执行器可用，避免单例在上一次释放后复用到已关闭的线程池
     */
    private void ensureExecutor() {
        if (mExecutorService == null || mExecutorService.isShutdown() || mExecutorService.isTerminated()) {
            mExecutorService = createExecutor(mCacheConfig);
        }
    }

    /**
     * 创建带任务回收能力的预加载任务
     * 对外仍然复用原有 ExoPreloadCallback，不新增公开回调接口
     */
    private PreloadTask createTrackedTask(Context context, String url, ExoPreloadCallback externalCallback) {
        final PreloadTask[] taskHolder = new PreloadTask[1];
        ExoPreloadCallback trackedCallback = new ExoPreloadCallback() {
            @Override
            public void onPreloadSuccess(String callbackUrl) {
                onTaskFinished(callbackUrl, taskHolder[0]);
                if (externalCallback != null) {
                    externalCallback.onPreloadSuccess(callbackUrl);
                }
            }

            @Override
            public void onPreloadFailed(String callbackUrl, String errorMsg) {
                onTaskFinished(callbackUrl, taskHolder[0]);
                if (externalCallback != null) {
                    externalCallback.onPreloadFailed(callbackUrl, errorMsg);
                }
            }

            @Override
            public void onPreloadProgress(String callbackUrl, long loadedBytes, long totalBytes) {
                if (externalCallback != null) {
                    externalCallback.onPreloadProgress(callbackUrl, loadedBytes, totalBytes);
                }
            }

            @Override
            public void onPreloadCanceled(String callbackUrl) {
                onTaskFinished(callbackUrl, taskHolder[0]);
                if (externalCallback != null) {
                    externalCallback.onPreloadCanceled(callbackUrl);
                }
            }
        };

        PreloadTask task = new PreloadTask(
                url,
                ExoCacheManager.getCacheDataSourceFactory(context),
                mExecutorService,
                mCacheConfig,
                trackedCallback
        );
        taskHolder[0] = task;
        return task;
    }

    /**
     * 创建预加载执行器
     * 仍然保留有限队列和丢弃策略，避免高并发下任务堆积导致内存膨胀
     */
    private ExecutorService createExecutor(ExoCacheConfig cacheConfig) {
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(10); // 有限队列，避免内存溢出
        return new ThreadPoolExecutor(
                cacheConfig.getCoreThreadCount(),
                cacheConfig.getMaxThreadCount(),
                60, // 空闲线程超时时间
                TimeUnit.SECONDS,
                workQueue,
                new ThreadPoolExecutor.DiscardPolicy() {
                    @Override
                    public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                        ExoLog.log("Preload queue is full, discard task: " + r.getClass().getSimpleName()
                                + ", queueSize=" + e.getQueue().size()
                                + ", activeCount=" + e.getActiveCount());
                        super.rejectedExecution(r, e);
                    }
                }
        );
    }
}
