package com.learn.developer.cache;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatusCode;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public final class CachedResponse {
    private final byte[] body;
    private final int statusCode;
    private final MultiValueMap<String, String> headers;
    private final Instant storedAt;
    private final long maxAgeSeconds; // if upstream provided Cache-Control: max-age

    public CachedResponse(byte[] body, int statusCode, MultiValueMap<String, String> headers, Instant storedAt,
            long maxAgeSeconds) {
        this.body = body;
        this.statusCode = statusCode;
        this.headers = new LinkedMultiValueMap<>();
        if (headers != null) {
            headers.forEach((k, v) -> this.headers.put(k, List.copyOf(v)));
        }
        this.storedAt = storedAt;
        this.maxAgeSeconds = maxAgeSeconds;
    }

    public byte[] getBody() {
        return body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public MultiValueMap<String, String> getHeaders() {
        return headers;
    }

    public Instant getStoredAt() {
        return storedAt;
    }

    public long getMaxAgeSeconds() {
        return maxAgeSeconds;
    }

    public HttpStatusCode status() {
        return HttpStatusCode.valueOf(statusCode);
    }

    public int weight() {
        return body == null ? 0 : body.length;
    }
}