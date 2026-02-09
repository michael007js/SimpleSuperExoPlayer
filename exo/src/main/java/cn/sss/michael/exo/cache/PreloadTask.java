package cn.sss.michael.exo.cache;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheWriter;

import cn.sss.michael.exo.util.ExoLog;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * @author Michael by 61642
 * @date 2025/12/30 18:00
 * @Description 预加载任务
 */
@UnstableApi
public class PreloadTask {
    private final String url;
    private final CacheWriter cacheWriter;
    private final ExecutorService executor;
    private final ExoCacheConfig config;
    private final ExoPreloadCallback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean isCanceled = false;
    private Future<?> future; // 用于任务超时控制

    public PreloadTask(String url, CacheDataSource.Factory factory, ExecutorService executor,
                       ExoCacheConfig config, ExoPreloadCallback callback) {
        this.url = url;
        this.executor = executor;
        this.config = config != null ? config : ExoCacheConfig.getDefaultConfig();
        this.callback = callback;

        // 使用配置类的Key生成规则
        String cacheKey = this.config.getCacheKeyGenerator().generateKey(url);
        // 使用配置类的预加载大小
        DataSpec dataSpec = new DataSpec.Builder()
                .setUri(Uri.parse(url))
                .setPosition(0)
                .setLength(this.config.getPreloadSize())
                .setKey(cacheKey)
                .build();

        // 缓存进度监听器
        CacheWriter.ProgressListener progressListener = new CacheWriter.ProgressListener() {
            @Override
            public void onProgress(long requestLength, long bytesCached, long newBytesCached) {
                if (isCanceled) {
                    return;
                }
                // 主线程回调进度
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onPreloadProgress(url, bytesCached, requestLength);
                    }
                });
            }
        };

        this.cacheWriter = new CacheWriter(factory.createDataSource(), dataSpec, null, progressListener);
    }

    public void execute() {
        // 提交任务并记录Future，用于超时控制
        future = executor.submit(() -> {
            try {
                if (isCanceled) {
                    notifyCanceled();
                    return;
                }
                // 执行缓存
                cacheWriter.cache();
                if (isCanceled) {
                    notifyCanceled();
                    return;
                }
                // 主线程回调成功
                notifySuccess();
            } catch (Exception e) {
                if (isCanceled) {
                    notifyCanceled();
                    return;
                }
                // 主线程回调失败
                notifyFailed(e.getMessage());
            }
        });

        // 任务超时控制
        mainHandler.postDelayed(() -> {
            if (future != null && !future.isDone() && !isCanceled) {
                cancel();
                notifyFailed("预加载超时");
            }
        }, config.getTaskTimeout());
    }

    public void cancel() {
        isCanceled = true;
        cacheWriter.cancel();
        // 取消线程任务
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }

    // 主线程通知成功
    private void notifySuccess() {
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onPreloadSuccess(url);
            }
            ExoLog.log("缓存成功: " + url);
        });
    }

    // 主线程通知失败
    private void notifyFailed(String errorMsg) {
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onPreloadFailed(url, errorMsg);
            }
            ExoLog.log("缓存失败: " + errorMsg);
        });
    }

    // 主线程通知取消
    private void notifyCanceled() {
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onPreloadCanceled(url);
            }
            ExoLog.log("缓存被取消: " + url);
        });
    }

    public String getUrl() {
        return url;
    }
}