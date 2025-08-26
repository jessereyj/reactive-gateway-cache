package com.learn.developer.cache;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

public class CachedResponse {
    private final HttpStatus status;
    private final HttpHeaders headers;
    private final byte[] body;
    private final MediaType mediaType;

    public CachedResponse(HttpStatus status, HttpHeaders headers, byte[] body, MediaType mediaType) {
        this.status = status;
        this.headers = headers;
        this.body = body;
        this.mediaType = mediaType;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return body;
    }

    public MediaType getMediaType() {
        return mediaType;
    }
}
