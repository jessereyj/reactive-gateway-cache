package com.learn.developer.cache;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
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
    private static final String X_BYPASS_CACHE = "X-Bypass-Cache";

    private final Cache<CacheKey, CachedResponse> cache;
    private final CacheProperties props;

    public ResponseCacheFilter(Cache<CacheKey, CachedResponse> cache, CacheProperties props) {
        this.cache = cache;
        this.props = props;
    }

    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!props.isEnabled()) {
            return chain.filter(exchange);
        }

        HttpMethod method = exchange.getRequest().getMethod();
        if (method != HttpMethod.GET) {
            return chain.filter(exchange);
        }

        String reqCacheCtl = exchange.getRequest().getHeaders().getFirst(HttpHeaders.CACHE_CONTROL);
        if (reqCacheCtl != null && reqCacheCtl.toLowerCase(Locale.ROOT).contains("no-cache")) {
            return chain.filter(exchange);
        }

        if ("true".equalsIgnoreCase(exchange.getRequest().getHeaders().getFirst(X_BYPASS_CACHE))) {
            return chain.filter(exchange);
        }

        if (props.isSkipWhenAuthorization()
                && exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION) != null) {
            return chain.filter(exchange);
        }

        CacheKey key = CacheKey.from(exchange, props.getVaryHeaders());
        CachedResponse hit = cache.getIfPresent(key);
        if (hit != null) {
            return writeFromCache(exchange, hit);
        }

        ServerHttpResponse original = exchange.getResponse();
        DataBufferFactory bufferFactory = original.bufferFactory();

        ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.min(props.getMaxBodyBytes(), 512 * 1024));
        AtomicBoolean overflow = new AtomicBoolean(false);

        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(original) {

            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                Flux<? extends DataBuffer> source = Flux.from(body);

                Flux<DataBuffer> intercepted = source.map(dataBuffer -> {
                    if (overflow.get()) {
                        return dataBuffer;
                    }

                    int readable = dataBuffer.readableByteCount();
                    if (baos.size() + readable <= props.getMaxBodyBytes()) {
                        byte[] bytes = new byte[readable];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        baos.write(bytes, 0, bytes.length);
                        return bufferFactory.wrap(bytes);
                    }

                    overflow.set(true);
                    return dataBuffer;
                });

                return super.writeWith(intercepted)
                        .doOnSuccess(ignored -> {
                            if (!overflow.get()) {
                                maybeStore(exchange, baos.toByteArray());
                            }
                        });
            }
        };

        return chain.filter(exchange.mutate().response(decorated).build());
    }

    private Mono<Void> writeFromCache(ServerWebExchange exchange, CachedResponse cached) {
        ServerHttpResponse resp = exchange.getResponse();
        resp.setStatusCode(cached.getStatus());

        cached.getHeaders().forEach((name, values) -> {
            if (!isSensitive(name)) {
                resp.getHeaders().put(name, List.copyOf(values));
            }
        });

        if (props.isAddAgeHeader()) {
            long age = Math.max(0, Duration.between(cached.getStoredAt(), Instant.now()).getSeconds());
            resp.getHeaders().set(HttpHeaders.AGE, String.valueOf(age));
        }

        if (props.isAddXcacheHeader()) {
            resp.getHeaders().set(X_CACHE, "HIT");
        }

        byte[] body = cached.getBody();
        resp.getHeaders().setContentLength(body.length);
        return resp.writeWith(Mono.just(resp.bufferFactory().wrap(body)));
    }

    private boolean isSensitive(String header) {
        String h = header == null ? "" : header.toLowerCase(Locale.ROOT);
        return h.equals("set-cookie")
                || h.equals("set-cookie2")
                || h.equals("connection")
                || h.equals("keep-alive")
                || h.equals("proxy-authenticate")
                || h.equals("proxy-authorization")
                || h.equals("te")
                || h.equals("trailer")
                || h.equals("transfer-encoding")
                || h.equals("upgrade");
    }

    private void maybeStore(ServerWebExchange exchange, byte[] body) {
        if (body == null || body.length == 0 || body.length > props.getMaxBodyBytes()) {
            return;
        }

        HttpStatusCode status = Objects.requireNonNullElse(
                exchange.getResponse().getStatusCode(),
                HttpStatusCode.valueOf(200));

        if (status.value() != 200) {
            return;
        }

        HttpHeaders headers = exchange.getResponse().getHeaders();

        String respCacheCtl = headers.getFirst(HttpHeaders.CACHE_CONTROL);
        if (respCacheCtl != null) {
            String v = respCacheCtl.toLowerCase(Locale.ROOT);
            if (v.contains("no-store") || v.contains("private")) {
                return;
            }
        }

        long maxAgeSeconds = extractMaxAgeSeconds(respCacheCtl);

        MultiValueMap<String, String> headersCopy = new LinkedMultiValueMap<>();
        headers.forEach((k, v) -> {
            if (!isSensitive(k)) {
                headersCopy.put(k, List.copyOf(v));
            }
        });

        CachedResponse value = new CachedResponse(
                body,
                status.value(),
                headersCopy,
                Instant.now(),
                maxAgeSeconds);

        CacheKey key = CacheKey.from(exchange, props.getVaryHeaders());
        cache.put(key, value);
    }

    private long extractMaxAgeSeconds(String cacheControl) {
        if (cacheControl == null) {
            return -1;
        }

        String v = cacheControl.toLowerCase(Locale.ROOT);
        int idx = v.indexOf("max-age=");
        if (idx < 0) {
            return -1;
        }

        int start = idx + 8;
        int end = start;
        while (end < v.length() && Character.isDigit(v.charAt(end))) {
            end++;
        }

        if (start == end) {
            return -1;
        }

        try {
            return Long.parseLong(v.substring(start, end));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}