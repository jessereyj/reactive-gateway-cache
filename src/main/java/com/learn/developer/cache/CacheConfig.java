package com.learn.developer.cache;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Weigher;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration
public class CacheConfig {

    @Bean
    Cache<CacheKey, CachedResponse> responseCache(CacheProperties props, MeterRegistry registry) {
        Caffeine<CacheKey, CachedResponse> builder = Caffeine.newBuilder()
                .recordStats()
                .expireAfterWrite(props.getTtl())
                .maximumWeight(props.getMaxWeightBytes())
                .weigher((Weigher<CacheKey, CachedResponse>) (k, v) -> v == null ? 0 : v.weight());

        Cache<CacheKey, CachedResponse> cache = builder.build();

        Gauge.builder("gateway.cache.size", cache, c -> c.estimatedSize()).register(registry);
        Gauge.builder("gateway.cache.weight.bytes", cache, c -> (double) props.getMaxWeightBytes()).register(registry);

        return cache;
    }
}