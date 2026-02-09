package cn.sss.michael.exo.cache;


import cn.sss.michael.exo.ExoConfig;

import java.io.File;

/**
 * @author Michael by 61642
 * @date 2025/12/30 17:59
 * @Description 缓存与预加载全局配置类
 */
public class ExoCacheConfig {
    // 缓存总大小
    private long cacheSize;
    // 单文件预加载大小
    private long preloadSize;
    // 最大并行预加载任务数
    private int maxPreloadTaskCount;
    // 线程池核心线程数
    private int coreThreadCount;
    // 线程池最大线程数
    private int maxThreadCount;
    // 缓存过期时间（毫秒，<=0 表示永不过期）
    private long cacheExpireTime;
    // 预加载任务超时时间
    private long taskTimeout;
    // 元数据最大条目数（内存管控上限）
    private int maxMetadataEntryCount;
    // 缓存Key剥离规则（外部可自定义，默认剥离?后面参数）
    private CacheKeyGenerator cacheKeyGenerator;
    // 缓存目录
    private File cacheDir;

    private ExoCacheConfig(Builder builder) {
        this.cacheSize = builder.cacheSize;
        this.preloadSize = builder.preloadSize;
        this.maxPreloadTaskCount = builder.maxPreloadTaskCount;
        this.coreThreadCount = builder.coreThreadCount;
        this.maxThreadCount = builder.maxThreadCount;
        this.cacheExpireTime = builder.cacheExpireTime;
        this.taskTimeout = builder.taskTimeout;
        this.maxMetadataEntryCount = builder.maxMetadataEntryCount;
        this.cacheKeyGenerator = builder.cacheKeyGenerator;
        this.cacheDir = builder.cacheDir;
    }

    // 默认配置
    public static ExoCacheConfig getDefaultConfig() {
        return new Builder().build();
    }

    // Builder模式
    public static class Builder {
        private long cacheSize = ExoConfig.CACHE_DEFAULT_CACHE_SIZE;
        private long preloadSize = ExoConfig.CACHE_DEFAULT_PRELOAD_SIZE;
        private int maxPreloadTaskCount = ExoConfig.CACHE_DEFAULT_MAX_PRELOAD_TASK;
        private int coreThreadCount = ExoConfig.CACHE_DEFAULT_CORE_THREAD_COUNT;
        private int maxThreadCount = ExoConfig.CACHE_DEFAULT_MAX_THREAD_COUNT;
        private long cacheExpireTime = ExoConfig.CACHE_DEFAULT_CACHE_EXPIRE_TIME;
        private long taskTimeout = ExoConfig.CACHE_DEFAULT_TASK_TIMEOUT;
        private int maxMetadataEntryCount = ExoConfig.CACHE_DEFAULT_MAX_METADATA_ENTRY_COUNT;
        private CacheKeyGenerator cacheKeyGenerator = new DefaultCacheKeyGenerator();
        private File cacheDir;

        public Builder setCacheSize(long cacheSize) {
            this.cacheSize = cacheSize > 0 ? cacheSize : ExoConfig.CACHE_DEFAULT_CACHE_SIZE;
            return this;
        }

        public Builder setPreloadSize(long preloadSize) {
            this.preloadSize = preloadSize > 0 ? preloadSize : ExoConfig.CACHE_DEFAULT_PRELOAD_SIZE;
            return this;
        }

        public Builder setMaxPreloadTaskCount(int maxPreloadTaskCount) {
            this.maxPreloadTaskCount = maxPreloadTaskCount > 0 ? maxPreloadTaskCount : ExoConfig.CACHE_DEFAULT_MAX_PRELOAD_TASK;
            return this;
        }

        public Builder setCoreThreadCount(int coreThreadCount) {
            this.coreThreadCount = coreThreadCount > 0 ? coreThreadCount : ExoConfig.CACHE_DEFAULT_CORE_THREAD_COUNT;
            return this;
        }

        public Builder setMaxThreadCount(int maxThreadCount) {
            this.maxThreadCount = maxThreadCount > coreThreadCount ? maxThreadCount : ExoConfig.CACHE_DEFAULT_MAX_THREAD_COUNT;
            return this;
        }

        public Builder setCacheExpireTime(long cacheExpireTime) {
            this.cacheExpireTime = cacheExpireTime;
            return this;
        }

        public Builder setTaskTimeout(long taskTimeout) {
            this.taskTimeout = taskTimeout > 0 ? taskTimeout : ExoConfig.CACHE_DEFAULT_TASK_TIMEOUT;
            return this;
        }

        public Builder setCacheKeyGenerator(CacheKeyGenerator cacheKeyGenerator) {
            this.cacheKeyGenerator = cacheKeyGenerator != null ? cacheKeyGenerator : new DefaultCacheKeyGenerator();
            return this;
        }

        public Builder setMaxMetadataEntryCount(int maxMetadataEntryCount) {
            this.maxMetadataEntryCount = maxMetadataEntryCount;
            return this;
        }

        public Builder setCacheDir(File cacheDir) {
            this.cacheDir = cacheDir;
            return this;
        }

        public ExoCacheConfig build() {
            return new ExoCacheConfig(this);
        }
    }

    // 缓存Key生成器接口（支持外部自定义剥离规则）
    public interface CacheKeyGenerator {
        String generateKey(String url);
    }

    // 默认Key生成器（剥离?后面的动态参数）
    public static class DefaultCacheKeyGenerator implements CacheKeyGenerator {
        @Override
        public String generateKey(String url) {
            if (url == null || !url.contains("?")) {
                return url;
            }
            return url.substring(0, url.indexOf("?"));
        }
    }

    public long getCacheSize() {
        return cacheSize;
    }

    public long getPreloadSize() {
        return preloadSize;
    }

    public int getMaxPreloadTaskCount() {
        return maxPreloadTaskCount;
    }

    public int getCoreThreadCount() {
        return coreThreadCount;
    }

    public int getMaxThreadCount() {
        return maxThreadCount;
    }

    public long getCacheExpireTime() {
        return cacheExpireTime;
    }

    public long getTaskTimeout() {
        return taskTimeout;
    }

    public int getMaxMetadataEntryCount() {
        return maxMetadataEntryCount;
    }

    public File getCacheDir() {
        return cacheDir;
    }

    public CacheKeyGenerator getCacheKeyGenerator() {
        return cacheKeyGenerator;
    }
}