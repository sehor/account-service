package com.skyflytech.accountservice.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory();
    }

    @Bean
    public RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<Object, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory, RedisTemplate<Object, Object> redisTemplate) {
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put("user", defaultCacheConfig.entryTtl(Duration.ofHours(12)));
        cacheConfigurations.put("accountSet", defaultCacheConfig.entryTtl(Duration.ofHours(2)));
        cacheConfigurations.put("accountingPeriods", defaultCacheConfig.entryTtl(Duration.ofHours(2)));

        Map<String, Integer> cacheSizes = new HashMap<>();
        cacheSizes.put("user", 1000);
        cacheSizes.put("accountSet", 500);
        cacheSizes.put("accountingPeriods", 2000);
        return new SizeRestrictedRedisCacheManager(
            RedisCacheWriter.nonLockingRedisCacheWriter(redisConnectionFactory),
            defaultCacheConfig,
            cacheConfigurations,
            redisTemplate,
            cacheSizes
        );
    }
}