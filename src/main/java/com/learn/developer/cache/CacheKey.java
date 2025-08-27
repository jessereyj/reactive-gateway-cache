package com.learn.developer.cache;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;

public final class CacheKey {
    public static CacheKey from(ServerWebExchange exchange, List<String> varyHeaders) {
        final String method = Objects.requireNonNull(exchange.getRequest().getMethod()).name();
        final URI uri = exchange.getRequest().getURI();

        // Normalize query order for stable keys
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>(exchange.getRequest().getQueryParams());
        String normalizedQuery = q.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getValue().stream().sorted().map(v -> e.getKey() + "=" + v)
                        .collect(Collectors.joining("&")))
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining("&"));

        String pathAndQuery = uri.getPath() + (normalizedQuery.isBlank() ? "" : ("?" + normalizedQuery));

        HttpHeaders h = exchange.getRequest().getHeaders();
        // build case-insensitive map for selected headers
        TreeMap<String, String> sel = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String name : varyHeaders) {
            String v = h.getFirst(name);
            if (v != null)
                sel.put(name.toLowerCase(), v);
        }
        String varyFp = sel.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining("|"));

        return new CacheKey(method, pathAndQuery, varyFp);
    }

    private final String method;
    private final String pathAndQuery;

    private final String varyHeaderFingerprint;

    private CacheKey(String method, String pathAndQuery, String varyHeaderFingerprint) {
        this.method = method;
        this.pathAndQuery = pathAndQuery;
        this.varyHeaderFingerprint = varyHeaderFingerprint;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof CacheKey other))
            return false;
        return method.equals(other.method) && pathAndQuery.equals(other.pathAndQuery)
                && varyHeaderFingerprint.equals(other.varyHeaderFingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, pathAndQuery, varyHeaderFingerprint);
    }

    @Override
    public String toString() {
        return method + " " + pathAndQuery + " [" + varyHeaderFingerprint + "]";
    }
}