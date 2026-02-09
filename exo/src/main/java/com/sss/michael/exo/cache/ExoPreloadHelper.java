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
            throw new IllegalArgumentException("禁止传入页面级 Context（Activity/Fragment/View），请传入 Application Context");
        }
        this.mContextRef = new WeakReference<>(context.getApplicationContext());
        this.mCacheConfig = cacheConfig != null ? cacheConfig : ExoCacheConfig.getDefaultConfig();
        // 替换为可配置的ThreadPoolExecutor（避免队列积压）
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(10); // 有限队列，避免内存溢出
        this.mExecutorService = new ThreadPoolExecutor(
                mCacheConfig.getCoreThreadCount(),
                mCacheConfig.getMaxThreadCount(),
                60, // 空闲线程超时时间
                TimeUnit.SECONDS,
                workQueue,
                new ThreadPoolExecutor.DiscardPolicy() // 队列满时丢弃新任务，避免OOM
        );
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

    public void updateCacheConfig(ExoCacheConfig newConfig) {
        if (newConfig != null) {
            this.mCacheConfig = newConfig;
            // 重建线程池
            shutdownExecutor();
            BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(10);
            this.mExecutorService = new ThreadPoolExecutor(
                    mCacheConfig.getCoreThreadCount(),
                    mCacheConfig.getMaxThreadCount(),
                    60,
                    TimeUnit.SECONDS,
                    workQueue,
                    new ThreadPoolExecutor.DiscardPolicy() {

                        @Override
                        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                            ExoLog.log("预加载任务队列已满，丢弃任务: " + r.getClass().getSimpleName() +
                                    "，当前队列大小: " + e.getQueue().size() +
                                    "，活跃线程数: " + e.getActiveCount());
                            super.rejectedExecution(r, e);
                        }

                    }
            );
        }
    }

    private Context getContext() {
        return mContextRef.get();
    }

    /**
     * 简化获取单例（使用默认配置）
     */
    public static ExoPreloadHelper getInstance(Context context) {
        return getInstance(context, null);
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
    public void resumePreload(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            ExoLog.log("预加载终止：urls为空");
            return;
        }
        Context context = getContext();
        if (context == null) {
            ExoLog.log("预加载终止：上下文context为空");
            return;
        }
        if (ExoCacheManager.getConfig().getCacheDir() == null) {
            ExoLog.log("预加载终止：缓存目录未配置，请在 Application 中初始化");
            return;
        }
        // 网络适配：仅WiFi下开启预加载
        if (!ExoNetworkUtil.isWifiConnected(context)) {
            ExoLog.log("非WiFi网络，不执行预加载");
            stopAll();
            return;
        }

        // 网络质量判断：网络差时减少并行任务数
        int actualMaxTaskCount = mCacheConfig.getMaxPreloadTaskCount();
        if (ExoNetworkUtil.isNetworkPoor(context)) {
            actualMaxTaskCount = Math.max(1, actualMaxTaskCount / 2); // 网络差时任务数减半
            ExoLog.log("网络质量较差，预加载最大并行任务数调整为: " + actualMaxTaskCount);
        }

        // 清理过期任务
        Iterator<Map.Entry<String, PreloadTask>> iterator = mPreloadTasks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, PreloadTask> entry = iterator.next();
            if (!urls.contains(entry.getKey())) {
                entry.getValue().cancel();
                iterator.remove();
                ExoLog.log("停止过期预加载: " + entry.getKey());
            }
        }

        // 添加并启动新任务（避免重复预加载）
        for (String url : urls) {
            if (TextUtils.isEmpty(url)) {
                continue;
            }
            // 缓存命中判断：已缓存完成，跳过预加载
            if (ExoCacheManager.isCacheCompleted(context, url)) {
                ExoLog.log("缓存命中，跳过加载/下载: " + url);
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
                ExoLog.log("已达最大并行预加载任务数，停止添加新任务");
                break;
            }

            // 创建预加载任务
            PreloadTask task = new PreloadTask(
                    url,
                    ExoCacheManager.getCacheDataSourceFactory(context),
                    mExecutorService,
                    mCacheConfig,
                    mGlobalPreloadCallback
            );
            mPreloadTasks.put(url, task);
            task.execute();
            ExoLog.log("启动新预加载任务: " + url);
        }
    }

    /**
     * 停止所有预加载任务
     */
    public void stopAll() {
        for (PreloadTask task : mPreloadTasks.values()) {
            task.cancel();
        }
        mPreloadTasks.clear();
        ExoLog.log("已停止所有预加载任务");
    }

    /**
     * 停止单个URL的预加载
     */
    public void stopPreload(String url) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        PreloadTask task = mPreloadTasks.get(url);
        if (task != null) {
            task.cancel();
            mPreloadTasks.remove(url);
            ExoLog.log("已停止预加载: " + url);
        }
    }

    /**
     * 关闭线程池
     */
    public void shutdownExecutor() {
        mExecutorService.shutdown();
        try {
            if (!mExecutorService.awaitTermination(1, TimeUnit.SECONDS)) {
                mExecutorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            mExecutorService.shutdownNow();
        }
    }
}