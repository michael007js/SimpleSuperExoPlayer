package com.sss.michael.exo.cache;

import android.content.Context;
import android.text.TextUtils;

import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheSpan;
import androidx.media3.datasource.cache.SimpleCache;

import com.sss.michael.exo.util.ExoLog;

import java.io.File;
import java.util.Iterator;
import java.util.NavigableSet;

/**
 * @author Michael by 61642
 * @date 2025/12/30 16:53
 * @Description 缓存管理器
 */
public class ExoCacheManager {
    private static SimpleCache sCache;
    private static ExoCacheConfig sCacheConfig;

    // 初始化配置
    public static void init(ExoCacheConfig cacheConfig) {
        sCacheConfig = cacheConfig != null ? cacheConfig : ExoCacheConfig.getDefaultConfig();
    }

    // 获取默认配置
    public static ExoCacheConfig getConfig() {
        if (sCacheConfig == null) {
            sCacheConfig = ExoCacheConfig.getDefaultConfig();
        }
        return sCacheConfig;
    }


    /**
     * 获取SimpleCache实例（单例）- 直接返回SimpleCache，避免Cache接口方法缺失
     */
    public static synchronized SimpleCache getCache(Context context) {
        if (sCache == null) {
            ExoCacheConfig config = getConfig();
            File cacheDir = config.getCacheDir();
            if (cacheDir == null) {
                throw new IllegalStateException("Cache dir is not configured.");
            }

            // 主动创建缓存目录，提高容错性
            if (!cacheDir.exists()) {
                boolean isCreated = cacheDir.mkdirs();
                if (!isCreated) {
                    ExoLog.log("Failed to create cache dir: " + cacheDir.getAbsolutePath());
                }
            }

            // 使用自定义淘汰器
            ExpirableLruCacheEvictor evictor = new ExpirableLruCacheEvictor(
                    config.getCacheSize(),
                    config.getCacheExpireTime(),
                    config.getMaxMetadataEntryCount()
            );
            sCache = new SimpleCache(cacheDir, evictor, new StandaloneDatabaseProvider(context));
        }
        return sCache;
    }

    /**
     * 创建支持缓存的DataSource工厂
     */
    public static CacheDataSource.Factory getCacheDataSourceFactory(Context context) {
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true);

        return new CacheDataSource.Factory()
                .setCache(getCache(context))
                .setUpstreamDataSourceFactory(httpFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    /**
     * 检查URL是否已缓存（且达到预加载大小）
     * 解决：无 Cache.Stats 问题，通过遍历 CacheSpan 计算总缓存大小
     */
    public static boolean isCacheCompleted(Context context, String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        if (getConfig().getCacheDir() == null) {
            ExoLog.log("Skip cache check: cache dir is not configured");
            return false;
        }
        ExoCacheConfig config = getConfig();
        String cacheKey = config.getCacheKeyGenerator().generateKey(url);
        SimpleCache cache = getCache(context);

        // 遍历该 key 对应的所有 CacheSpan，计算总缓存长度
        long totalCacheLength = 0;
        try {
            // 获取该 key 对应的所有缓存片段
            NavigableSet<CacheSpan> cacheSpans = cache.getCachedSpans(cacheKey);
            if (cacheSpans == null || cacheSpans.isEmpty()) {
                return false;
            }
            // 累加所有片段的长度
            for (CacheSpan span : cacheSpans) {
                totalCacheLength += span.length;
            }
        } catch (Exception e) {
            ExoLog.log("Cache completion query failed: " + e.getMessage());
            return false;
        }

        // 缓存总长度 >= 预加载大小，视为缓存完成
        return totalCacheLength >= config.getPreloadSize();
    }

    /**
     * 移除单个URL的缓存（模拟 removeKey 功能，遍历移除该 key 所有 CacheSpan）
     * 解决：无 removeKey 方法的问题
     */
    public static void removeCache(Context context, String url) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        ExoCacheConfig config = getConfig();
        String cacheKey = config.getCacheKeyGenerator().generateKey(url);
        SimpleCache cache = getCache(context);

        try {
            // 获取该 key 对应的所有 CacheSpan
            NavigableSet<CacheSpan> cacheSpans = cache.getCachedSpans(cacheKey);
            if (cacheSpans == null || cacheSpans.isEmpty()) {
                ExoLog.log("No cache found for key: " + cacheKey);
                return;
            }

            // 遍历移除所有 CacheSpan（模拟 removeKey 批量移除效果）
            for (CacheSpan span : cacheSpans) {
                cache.removeSpan(span);
            }
            ExoLog.log("Removed cache for key: " + cacheKey);
        } catch (Exception e) {
            ExoLog.log("Remove cache failed: " + e.getMessage());
        }
    }

    /**
     * 清理所有缓存
     * 优先使用当前配置里的真实缓存目录，避免误删默认目录之外的内容
     */
    public static void clearAllCache(Context context) {
        ExoCacheConfig config = getConfig();
        File cacheDir = config.getCacheDir();
        if (cacheDir == null) {
            ExoLog.log("Clear cache skipped: cache dir is not configured");
            return;
        }

        try {
            // 先释放缓存资源
            if (sCache != null) {
                sCache.release();
                sCache = null; // 置空，下次获取将重新创建
            }
            // 递归删除真实缓存目录
            boolean isDeleted = deleteDir(cacheDir);
            if (isDeleted) {
                ExoLog.log("Cleared all cache");
            } else {
                ExoLog.log("Failed to delete cache dir: " + cacheDir.getAbsolutePath());
            }
        } catch (Exception e) {
            ExoLog.log("Clear cache failed: " + e.getMessage());
        }
    }

    /**
     * 获取当前缓存大小（字节）
     * 解决：无 Cache.Stats 问题，通过遍历所有 CacheSpan 计算总大小
     */
    public static long getCurrentCacheSize(Context context) {
        SimpleCache cache = getCache(context);
        long totalSize = 0;

        try {
            // 获取所有缓存的 key 迭代器
            Iterator<String> keyIterator = cache.getKeys().iterator();
            while (keyIterator.hasNext()) {
                String key = keyIterator.next();
                // 遍历每个 key 对应的 CacheSpan，累加长度
                NavigableSet<CacheSpan> cacheSpans = cache.getCachedSpans(key);
                if (cacheSpans != null && !cacheSpans.isEmpty()) {
                    for (CacheSpan span : cacheSpans) {
                        totalSize += span.length;
                    }
                }
            }
        } catch (Exception e) {
            ExoLog.log("Get current cache size failed: " + e.getMessage());
            totalSize = 0;
        }

        return totalSize;
    }

    /**
     * 获取最大缓存大小（字节）
     */
    public static long getMaxCacheSize(Context context) {
        ExoCacheConfig config = getConfig();
        return config.getCacheSize();
    }

    // 递归删除目录
    private static boolean deleteDir(File dir) {
        if (dir == null || !dir.exists()) {
            return true;
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDir(file);
                } else {
                    // 强制删除文件
                    file.delete();
                }
            }
        }
        // 强制删除目录
        return dir.delete();
    }
}
