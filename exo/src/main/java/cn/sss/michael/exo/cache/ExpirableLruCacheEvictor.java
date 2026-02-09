package cn.sss.michael.exo.cache;

import androidx.annotation.NonNull;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheEvictor;
import androidx.media3.datasource.cache.CacheSpan;

import cn.sss.michael.exo.util.ExoLog;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Michael by 61642
 * @date 2025/12/30 16:54
 * @Description 磁盘缓存淘汰策略管理器 纯 CacheSpan 实现「LRU + 过期时间」缓存淘汰器
 */
@UnstableApi
public class ExpirableLruCacheEvictor implements CacheEvictor {
    private final long maxCacheSize; // 缓存最大总容量（字节）
    private final long expireTimeMs; // 缓存过期时间（毫秒，<=0 永不过期）
    private final int maxMetadataEntryCount; // 元数据最大条目数（内存管控上限）
    private final ReentrantLock lock = new ReentrantLock(); // 线程安全锁

    /**
     * CacheSpan 唯一标识（key + position 确保唯一性）
     */
    private static class SpanUniqueKey {
        private final String key; // 缓存主 Key
        private final long position; // 缓存片段起始位置

        public SpanUniqueKey(String key, long position) {
            this.key = key;
            this.position = position;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SpanUniqueKey that = (SpanUniqueKey) o;
            return position == that.position && Objects.equals(key, that.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, position);
        }
    }

    // LRU 顺序映射：按访问顺序排序，存储 CacheSpan 实例
    private final Map<SpanUniqueKey, CacheSpan> lruSpanMap = new LinkedHashMap<>(16, 0.75f, true);
    // 访问时间映射：自主维护每个 CacheSpan 的最后访问时间
    private final Map<SpanUniqueKey, Long> spanAccessTimeMap = new LinkedHashMap<>(16, 0.75f, true);

    private long currentCacheSize = 0; // 当前缓存总大小（所有 CacheSpan length 之和）
    private Cache cache; // 持有 Cache 引用，用于执行 Span 移除操作

    public ExpirableLruCacheEvictor(long maxCacheSize, long expireTimeMs, int maxMetadataEntryCount) {
        this.maxCacheSize = maxCacheSize;
        this.expireTimeMs = expireTimeMs;
        this.maxMetadataEntryCount = maxMetadataEntryCount;
    }

    @Override
    public boolean requiresCacheSpanTouches() {
        // 必须返回 true，触发 onSpanTouched 方法（维护 LRU 顺序 + 访问时间）
        return true;
    }

    @Override
    public void onCacheInitialized() {
        ExoLog.log("缓存初始化");
    }

    @Override
    public void onStartFile(Cache cache, String key, long position, long length) {
        this.cache = cache;
    }

    /**
     * CacheSpan 添加时 → 维护映射 + 更新大小 + 清理过期/超限 Span
     */
    @Override
    public void onSpanAdded(Cache cache, CacheSpan span) {
        lock.lock();
        try {
            this.cache = cache;
            SpanUniqueKey uniqueKey = new SpanUniqueKey(span.key, span.position);

            // 维护 LRU 顺序映射
            lruSpanMap.put(uniqueKey, span);
            // 自主维护初始访问时间（添加时视为首次访问）
            spanAccessTimeMap.put(uniqueKey, System.currentTimeMillis());
            // 更新当前缓存总大小
            currentCacheSize += span.length;

            // 清理过期 CacheSpan
            cleanExpiredCacheSpans();
            // 清理超限 LRU CacheSpan（磁盘容量）
            trimToMaxSizeByCacheSpans();
            // 清理元数据超限条目（内存管控）
            trimMetadataToMaxCount();
        } finally {
            lock.unlock();
        }
    }

    /**
     * CacheSpan 移除时 → 清理映射 + 更新大小
     */
    @Override
    public void onSpanRemoved(Cache cache, CacheSpan span) {
        lock.lock();
        try {
            this.cache = cache;
            SpanUniqueKey uniqueKey = new SpanUniqueKey(span.key, span.position);

            // 从 LRU 顺序映射中移除
            lruSpanMap.remove(uniqueKey);
            // 从访问时间映射中移除（避免内存泄漏）
            spanAccessTimeMap.remove(uniqueKey);
            // 更新当前缓存总大小
            currentCacheSize -= span.length;

            // 兼容异常场景：防止缓存大小为负数
            if (currentCacheSize < 0) {
                currentCacheSize = 0;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * CacheSpan 被访问时 → 更新 LRU 顺序 + 刷新访问时间
     */
    @Override
    public void onSpanTouched(@NonNull Cache cache, @NonNull CacheSpan oldSpan, CacheSpan newSpan) {
        lock.lock();
        try {
            this.cache = cache;
            SpanUniqueKey newUniqueKey = new SpanUniqueKey(newSpan.key, newSpan.position);

            // 更新 LRU 顺序（重新放入触发 LinkedHashMap 排序）
            lruSpanMap.put(newUniqueKey, newSpan);
            // 刷新自主维护的访问时间（关键：解决 Span 无 lastAccessTime 问题）
            spanAccessTimeMap.put(newUniqueKey, System.currentTimeMillis());
        } finally {
            lock.unlock();
        }
    }

    /**
     * 清理过期 CacheSpan（使用自主维护的访问时间）
     */
    private void cleanExpiredCacheSpans() {
        if (expireTimeMs <= 0 || cache == null || lruSpanMap.isEmpty() || spanAccessTimeMap.isEmpty()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<SpanUniqueKey, CacheSpan>> spanIterator = lruSpanMap.entrySet().iterator();

        while (spanIterator.hasNext()) {
            Map.Entry<SpanUniqueKey, CacheSpan> spanEntry = spanIterator.next();
            SpanUniqueKey uniqueKey = spanEntry.getKey();
            CacheSpan span = spanEntry.getValue();

            // 从自主维护的映射中获取最后访问时间（不再依赖 Span 自身属性）
            Long lastAccessTime = spanAccessTimeMap.get(uniqueKey);
            if (lastAccessTime == null) {
                continue;
            }

            // 判断是否过期
            if (currentTime - lastAccessTime > expireTimeMs) {
                try {
                    // 精准移除当前过期的 CacheSpan
                    cache.removeSpan(span);
                    // 清理两个映射中的记录
                    spanIterator.remove();
                    spanAccessTimeMap.remove(uniqueKey);
                } catch (Exception e) {
                    // 捕获异常，避免单个 Span 移除失败中断整体流程
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 按 LRU 规则清理超限 CacheSpan
     */
    private void trimToMaxSizeByCacheSpans() {
        if (maxCacheSize <= 0 || cache == null || lruSpanMap.isEmpty() || currentCacheSize <= maxCacheSize) {
            return;
        }
        // 打印超限日志，方便排查问题
        long overSize = currentCacheSize - maxCacheSize;
        ExoLog.log("磁盘缓存超限，需要淘汰：" + overSize / 1024 / 1024 + "MB（当前：" + currentCacheSize / 1024 / 1024 + "MB，上限：" + maxCacheSize / 1024 / 1024 + "MB）");

        Iterator<Map.Entry<SpanUniqueKey, CacheSpan>> spanIterator = lruSpanMap.entrySet().iterator();
        int evictCount = 0; // 记录淘汰的缓存片段数量
        // 循环移除最久未访问的 Span（链表头部），直到缓存大小合规
        while (spanIterator.hasNext() && currentCacheSize > maxCacheSize) {
            Map.Entry<SpanUniqueKey, CacheSpan> spanEntry = spanIterator.next();
            SpanUniqueKey uniqueKey = spanEntry.getKey();
            CacheSpan oldestSpan = spanEntry.getValue();

            try {
                // 移除最久未访问的 CacheSpan
                cache.removeSpan(oldestSpan);
                // 清理映射 + 更新缓存大小
                spanIterator.remove();
                spanAccessTimeMap.remove(uniqueKey);
                currentCacheSize -= oldestSpan.length;
                evictCount++;
            } catch (Exception e) {
                ExoLog.log("淘汰超限缓存片段失败：" + e.getMessage());
                e.printStackTrace();
            }
        }
        ExoLog.log("磁盘缓存超限淘汰完成，共淘汰 " + evictCount + " 个缓存片段，当前缓存大小：" + currentCacheSize / 1024 / 1024 + "MB");

    }

    /**
     * 元数据超限清理
     */
    private void trimMetadataToMaxCount() {
        if (maxMetadataEntryCount <= 0 || lruSpanMap.size() <= maxMetadataEntryCount) {
            return;
        }

        int overCount = lruSpanMap.size() - maxMetadataEntryCount;
        ExoLog.log("缓存元数据条目超限，需要淘汰：" + overCount + " 条（当前：" + lruSpanMap.size() + " 条，上限：" + maxMetadataEntryCount + " 条）");

        Iterator<Map.Entry<SpanUniqueKey, CacheSpan>> spanIterator = lruSpanMap.entrySet().iterator();
        int evictCount = 0;

        // 移除最久未访问的元数据（同步删除对应磁盘缓存）
        while (spanIterator.hasNext() && lruSpanMap.size() > maxMetadataEntryCount) {
            Map.Entry<SpanUniqueKey, CacheSpan> spanEntry = spanIterator.next();
            SpanUniqueKey uniqueKey = spanEntry.getKey();
            CacheSpan oldestSpan = spanEntry.getValue();

            try {
                // 删除对应磁盘缓存文件
                cache.removeSpan(oldestSpan);
                // 清理元数据映射
                spanIterator.remove();
                spanAccessTimeMap.remove(uniqueKey);
                currentCacheSize -= oldestSpan.length;
                evictCount++;
            } catch (Exception e) {
                ExoLog.log("淘汰元数据超限缓存片段失败：" + e.getMessage());
                e.printStackTrace();
            }
        }

        ExoLog.log("缓存元数据超限淘汰完成，共淘汰 " + evictCount + " 条，当前元数据条目数：" + lruSpanMap.size());
    }

    /**
     * 超限清理
     */
    public void trimToMaxSize() {
        lock.lock();
        try {
            trimToMaxSizeByCacheSpans();
        } finally {
            lock.unlock();
        }
    }
}