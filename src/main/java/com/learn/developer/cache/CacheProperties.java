package com.learn.developer.cache;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.cache")
public class CacheProperties {
    public static class Admin {
        private String apiKey = ""; // empty disables auth

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }

    private boolean enabled = true;
    private Duration ttl = Duration.ofSeconds(60);
    private long maxWeightBytes = 100 * 1024 * 1024; // 100 MiB
    private int maxBodyBytes = 256 * 1024; // 256 KiB per entry
    private List<String> varyHeaders = List.of("Accept", "Accept-Encoding", "Accept-Language");
    private boolean skipWhenAuthorization = true;
    private boolean addXcacheHeader = true;
    private boolean addAgeHeader = true;

    private Admin admin = new Admin();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public long getMaxWeightBytes() {
        return maxWeightBytes;
    }

    public void setMaxWeightBytes(long maxWeightBytes) {
        this.maxWeightBytes = maxWeightBytes;
    }

    public int getMaxBodyBytes() {
        return maxBodyBytes;
    }

    public void setMaxBodyBytes(int maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    public List<String> getVaryHeaders() {
        return varyHeaders;
    }

    public void setVaryHeaders(List<String> varyHeaders) {
        this.varyHeaders = varyHeaders;
    }

    public boolean isSkipWhenAuthorization() {
        return skipWhenAuthorization;
    }

    public void setSkipWhenAuthorization(boolean skipWhenAuthorization) {
        this.skipWhenAuthorization = skipWhenAuthorization;
    }

    public boolean isAddXcacheHeader() {
        return addXcacheHeader;
    }

    public void setAddXcacheHeader(boolean addXcacheHeader) {
        this.addXcacheHeader = addXcacheHeader;
    }

    public boolean isAddAgeHeader() {
        return addAgeHeader;
    }

    public void setAddAgeHeader(boolean addAgeHeader) {
        this.addAgeHeader = addAgeHeader;
    }

    public Admin getAdmin() {
        return admin;
    }

    public void setAdmin(Admin admin) {
        this.admin = admin;
    }
}