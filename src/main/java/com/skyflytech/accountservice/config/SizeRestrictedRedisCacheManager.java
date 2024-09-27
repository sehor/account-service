package com.skyflytech.accountservice.config;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SizeRestrictedRedisCacheManager extends RedisCacheManager {

    private final RedisTemplate<Object, Object> redisTemplate;
    private final Map<String, Integer> cacheSizes;

    public SizeRestrictedRedisCacheManager(RedisCacheWriter cacheWriter, 
                                           RedisCacheConfiguration defaultCacheConfiguration,
                                           Map<String, RedisCacheConfiguration> initialCacheConfigurations,
                                           RedisTemplate<Object, Object> redisTemplate,
                                           Map<String, Integer> cacheSizes) {
        super(cacheWriter, defaultCacheConfiguration, initialCacheConfigurations);
        this.redisTemplate = redisTemplate;
        this.cacheSizes = new ConcurrentHashMap<>(cacheSizes);
    }

    @Override
    protected RedisCache createRedisCache(String name, RedisCacheConfiguration cacheConfig) {
        RedisCache redisCache = super.createRedisCache(name, cacheConfig);
        return new SizeRestrictedRedisCache(redisCache, redisTemplate, cacheSizes.getOrDefault(name, Integer.MAX_VALUE));
    }

    private static class SizeRestrictedRedisCache extends RedisCache {
        private final RedisCache delegate;
        private final RedisTemplate<Object, Object> redisTemplate;
        private final int maxSize;

        public SizeRestrictedRedisCache(RedisCache delegate, RedisTemplate<Object, Object> redisTemplate, int maxSize) {
            super(delegate.getName(), delegate.getNativeCache(), delegate.getCacheConfiguration());
            this.delegate = delegate;
            this.redisTemplate = redisTemplate;
            this.maxSize = maxSize;
        }

        @Override
        public void put(Object key, Object value) {
            if (getCurrentSize() >= maxSize) {
                evictOldestEntry();
            }
            delegate.put(key, value);
        }

        private long getCurrentSize() {
            Long size = redisTemplate.opsForSet().size(getName());
            return size != null ? size : 0;
        }

        private void evictOldestEntry() {
            Object oldestKey = redisTemplate.opsForZSet().range(getName(), 0, 0).iterator().next();
            delegate.evict(oldestKey);
        }

        // 其他方法委托给delegate
    }
}