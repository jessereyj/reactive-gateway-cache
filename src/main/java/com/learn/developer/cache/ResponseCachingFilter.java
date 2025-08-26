package com.learn.developer.cache;

import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.github.benmanes.caffeine.cache.Cache;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ResponseCachingFilter implements GlobalFilter, Ordered {

    @Autowired
    private Cache<String, CachedResponse> responseCache;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String cacheKey = exchange.getRequest().getURI().toString();

        if (!HttpMethod.GET.equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }

        CachedResponse cached = responseCache.getIfPresent(cacheKey);
        if (cached != null) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(cached.getStatus());
            response.getHeaders().putAll(cached.getHeaders());
            response.getHeaders().setContentType(cached.getMediaType());
            DataBuffer buffer = response.bufferFactory().wrap(cached.getBody());
            return response.writeWith(Mono.just(buffer));
        }

        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();

        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                return DataBufferUtils.join(Flux.from(body))
                        .flatMap(dataBuffer -> {
                            byte[] content = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(content);
                            DataBufferUtils.release(dataBuffer);

                            HttpHeaders headers = new HttpHeaders();
                            headers.putAll(getDelegate().getHeaders());
                            MediaType mediaType = headers.getContentType();

                            if (mediaType != null && mediaType.includes(MediaType.APPLICATION_JSON)) {
                                HttpStatus status = HttpStatus.resolve(getStatusCode().value());
                                if (status != null) {
                                    responseCache.put(cacheKey,
                                            new CachedResponse(status, headers, content, mediaType));
                                }
                            }

                            DataBuffer buffer = bufferFactory.wrap(content);
                            return getDelegate().writeWith(Mono.just(buffer));
                        });
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
