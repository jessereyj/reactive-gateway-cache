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
    private final long maxAgeSeconds;

    public CachedResponse(byte[] body, int statusCode, MultiValueMap<String, String> headers, Instant storedAt,
            long maxAgeSeconds) {
        this.body = body == null ? new byte[0] : body.clone();
        this.statusCode = statusCode;
        this.headers = new LinkedMultiValueMap<>();
        if (headers != null) {
            headers.forEach((k, v) -> this.headers.put(k, List.copyOf(v)));
        }
        this.storedAt = storedAt;
        this.maxAgeSeconds = maxAgeSeconds;
    }

    public byte[] getBody() {
        return body.clone();
    }

    public int getStatusCode() {
        return statusCode;
    }

    public HttpStatusCode getStatus() {
        return HttpStatusCode.valueOf(statusCode);
    }

    public MultiValueMap<String, String> getHeaders() {
        MultiValueMap<String, String> copy = new LinkedMultiValueMap<>();
        headers.forEach((k, v) -> copy.put(k, List.copyOf(v)));
        return copy;
    }

    public Instant getStoredAt() {
        return storedAt;
    }

    public long getMaxAgeSeconds() {
        return maxAgeSeconds;
    }

    public int weight() {
        return body.length;
    }
}