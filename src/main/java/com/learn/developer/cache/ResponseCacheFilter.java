package com.learn.developer.cache;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;

import com.github.benmanes.caffeine.cache.Cache;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ResponseCacheFilter implements GlobalFilter, Ordered {

    private static final String X_CACHE = "X-Cache";
    private final Cache<CacheKey, CachedResponse> cache;
    private final CacheProperties props;

    public ResponseCacheFilter(Cache<CacheKey, CachedResponse> cache, CacheProperties props) {
        this.cache = cache;
        this.props = props;
    }

    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1; // -2
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
            org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        if (!props.isEnabled())
            return chain.filter(exchange);

        // Only cache GET requests.
        HttpMethod method = exchange.getRequest().getMethod();
        if (method == null || method != HttpMethod.GET)
            return chain.filter(exchange);

        // Respect request no-cache / bypass header
        String reqCacheCtl = exchange.getRequest().getHeaders().getFirst(HttpHeaders.CACHE_CONTROL);
        if (reqCacheCtl != null && reqCacheCtl.toLowerCase().contains("no-cache")) {
            return chain.filter(exchange);
        }
        if ("true".equalsIgnoreCase(exchange.getRequest().getHeaders().getFirst("X-Bypass-Cache"))) {
            return chain.filter(exchange);
        }

        // Optional: skip if Authorization header present
        if (props.isSkipWhenAuthorization()
                && exchange.getRequest().getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            return chain.filter(exchange);
        }

        CacheKey key = CacheKey.from(exchange, props.getVaryHeaders());

        // Cache hit fast-path
        CachedResponse hit = cache.getIfPresent(key);
        if (hit != null) {
            return writeFromCache(exchange, hit);
        }

        // Cache miss: decorate response to capture body
        ServerHttpResponse original = exchange.getResponse();
        DataBufferFactory bufferFactory = original.bufferFactory();

        ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.min(props.getMaxBodyBytes(), 512 * 1024));

        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(original) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (body instanceof Flux<? extends DataBuffer> flux) {
                    Flux<DataBuffer> intercepted = flux.map(dataBuffer -> {
                        int readable = dataBuffer.readableByteCount();
                        if (baos.size() + readable <= props.getMaxBodyBytes()) {
                            byte[] bytes = new byte[readable];
                            dataBuffer.read(bytes);
                            baos.write(bytes, 0, bytes.length);
                            DataBufferUtils.release(dataBuffer);
                            return bufferFactory.wrap(bytes);
                        } else {
                            // Too large: bypass caching but still stream to client
                            return dataBuffer; // pass-through (do not read/consume)
                        }
                    });

                    return super.writeWith(intercepted)
                            .doOnTerminate(() -> maybeStore(exchange, baos.toByteArray()))
                            .onErrorResume(ex -> super.writeWith(Flux.empty()));
                }
                return super.writeWith(body);
            }
        };

        return chain.filter(exchange.mutate().response(decorated).build());
    }

    private Mono<Void> writeFromCache(ServerWebExchange exchange, CachedResponse cached) {
        ServerHttpResponse resp = exchange.getResponse();
        resp.setStatusCode(cached.status());

        // Copy headers with hygiene: drop Set-Cookie* and hop-by-hop headers
        cached.getHeaders().forEach((name, values) -> {
            if (!isSensitive(name)) {
                resp.getHeaders().put(name, values);
            }
        });

        // Enrich with Age and X-Cache
        if (props.isAddAgeHeader()) {
            long age = Math.max(0, Duration.between(cached.getStoredAt(), Instant.now()).getSeconds());
            resp.getHeaders().set(HttpHeaders.AGE, String.valueOf(age));
        }
        if (props.isAddXcacheHeader()) {
            resp.getHeaders().set(X_CACHE, "HIT");
        }

        // Ensure Content-Length and write body
        byte[] body = cached.getBody();
        resp.getHeaders().setContentLength(body.length);
        DataBufferFactory f = resp.bufferFactory();
        return resp.writeWith(Mono.just(f.wrap(body)));
    }

    private boolean isSensitive(String header) {
        String h = header == null ? "" : header.toLowerCase();
        return h.equals("set-cookie") || h.equals("set-cookie2") || h.equals("transfer-encoding");
    }

    private void maybeStore(ServerWebExchange exchange, byte[] body) {
        if (body == null)
            return;
        if (body.length == 0)
            return; // don't cache empty bodies
        if (body.length > props.getMaxBodyBytes())
            return; // guardrail

        HttpStatusCode status = Objects.requireNonNullElse(exchange.getResponse().getStatusCode(),
                HttpStatusCode.valueOf(200));
        if (status.value() != 200)
            return; // cache only 200 OK by default

        HttpHeaders h = exchange.getResponse().getHeaders();

        // Respect upstream Cache-Control: no-store / private
        String respCacheCtl = h.getFirst(HttpHeaders.CACHE_CONTROL);
        if (respCacheCtl != null) {
            String v = respCacheCtl.toLowerCase();
            if (v.contains("no-store") || v.contains("private"))
                return;
        }

        long maxAgeSeconds = extractMaxAgeSeconds(respCacheCtl);

        MultiValueMap<String, String> headersCopy = new LinkedMultiValueMap<>();
        h.forEach((k, v) -> headersCopy.put(k, List.copyOf(v)));

        CachedResponse value = new CachedResponse(body, status.value(), headersCopy, Instant.now(), maxAgeSeconds);

        CacheKey key = CacheKey.from(exchange, props.getVaryHeaders());
        cache.put(key, value);
    }

    private long extractMaxAgeSeconds(String cacheControl) {
        if (cacheControl == null)
            return -1;
        String v = cacheControl.toLowerCase();
        int idx = v.indexOf("max-age=");
        if (idx < 0)
            return -1;
        int start = idx + 8;
        int end = start;
        while (end < v.length() && Character.isDigit(v.charAt(end)))
            end++;
        try {
            return Long.parseLong(v.substring(start, end));
        } catch (Exception e) {
            return -1;
        }
    }
}