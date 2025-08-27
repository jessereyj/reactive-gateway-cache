package com.learn.developer.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.github.benmanes.caffeine.cache.Cache;
import com.learn.developer.cache.CacheKey;
import com.learn.developer.cache.CacheProperties;
import com.learn.developer.cache.CachedResponse;

@RestController
@RequestMapping("/admin/cache")
public class AdminController {

    // Helper for evict endpoint to rebuild the key
    static class CacheKeyBuilder {
        CacheKey build(String method, String pathAndQuery, String varyHeadersCsv, String varyValuesCsv) {
            String fp;
            if (!StringUtils.hasText(varyValuesCsv)) {
                fp = "";
            } else {
                fp = String.join("|", varyValuesCsv.split(","));
            }
            return new CacheKeyReflect(method, pathAndQuery, fp).toKey();
        }
    }

    // Use a minimal adapter to construct a CacheKey (since CacheKey has a factory
    // bound to exchange)
    static class CacheKeyReflect {
        private final String method;
        private final String pathAndQuery;
        private final String varyFp;

        CacheKeyReflect(String method, String pathAndQuery, String varyFp) {
            this.method = method;
            this.pathAndQuery = pathAndQuery;
            this.varyFp = varyFp;
        }

        CacheKey toKey() {
            try {
                var ctor = CacheKey.class.getDeclaredConstructor(String.class, String.class, String.class);
                ctor.setAccessible(true);
                return ctor.newInstance(method, pathAndQuery, varyFp);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create CacheKey for eviction", e);
            }
        }
    }

    private final Cache<CacheKey, CachedResponse> cache;

    private final CacheProperties props;

    public AdminController(Cache<CacheKey, CachedResponse> cache, CacheProperties props) {
        this.cache = cache;
        this.props = props;
    }

    @DeleteMapping("/clear")
    public Map<String, Object> clear(@RequestHeader(name = "X-API-Key", required = false) String key) {
        requireApiKey(key);
        cache.invalidateAll();
        return Map.of("ok", true);
    }

    @DeleteMapping("/evict")
    public Map<String, Object> evict(@RequestHeader(name = "X-API-Key", required = false) String key,
            @RequestParam String method,
            @RequestParam String pathAndQuery,
            @RequestParam(defaultValue = "Accept,Accept-Encoding,Accept-Language") String varyHeaders,
            @RequestParam(defaultValue = "") String varyValues) {
        requireApiKey(key);
        CacheKey k = new CacheKeyBuilder().build(method, pathAndQuery, varyHeaders, varyValues);
        cache.invalidate(k);
        return Map.of("ok", true);
    }

    @GetMapping(value = "/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> stats(@RequestHeader(name = "X-API-Key", required = false) String key) {
        requireApiKey(key);
        var stats = cache.stats();
        return Map.of(
                "hitCount", stats.hitCount(),
                "missCount", stats.missCount(),
                "hitRate", stats.hitRate(),
                "evictionCount", stats.evictionCount(),
                "estimatedSize", cache.estimatedSize(),
                "maxWeightBytes", props.getMaxWeightBytes());
    }

    private void requireApiKey(String key) {
        String expected = props.getAdmin().getApiKey();
        if (StringUtils.hasText(expected) && !expected.equals(key)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "invalid api key");
        }
    }
}