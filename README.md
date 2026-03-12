# ☁️ Reactive Edge Gateway with Caffeine Cache

A fully reactive edge gateway built with **Spring Boot 4.0.3** and **Spring Cloud Gateway**, featuring an **in-memory Caffeine cache** that short-circuits repeat GET requests by serving cached bodies directly. Designed for high-throughput environments with safe defaults for TTL, size limits, and header hygiene.

---

## 🚀 Features

- ⚡ Reactive routing via Spring Cloud Gateway
- 🧠 In-memory response caching using Caffeine
- 🛡️ Header sanitization and cache-control compliance
- 🧰 Bootstrap configuration support (`bootstrap.yml`)
- 🔒 Optional cache bypass for authenticated requests
- 📊 Actuator endpoints for health and metrics
- 🧹 Admin endpoint for cache eviction

---

## 📦 Tech Stack

| Component              | Version        |
|------------------------|----------------|
| Spring Boot            | 4.0.3.         |
| Spring Cloud Gateway   | 2025.1.0       |
| Spring WebFlux         | Reactive       |
| Caffeine Cache         | Latest stable  |
| Java                   | 25             |

---

## Highlights
- ✅ **Reactive** end-to-end (non-blocking capture and replay of response bodies)
- 🧠 **Caffeine** with weighted eviction by body size and global TTL
- 🛡️ **Header hygiene** (no `Set-Cookie*` copied, no hop-by-hop headers)
- 🔄 **Vary** support (`Accept`, `Accept-Encoding`, `Accept-Language`) to keep content-negotiation safe
- 🚫 **Bypass controls**: `Cache-Control: no-cache` or `X-Bypass-Cache: true`
- 🔐 **Auth-aware**: skip caching if request has `Authorization` (configurable)
- 📊 **Metrics**: Micrometer gauges + Caffeine stats via `/admin/cache/stats` and Actuator
- 🧹 **Eviction**: `/admin/cache/clear` & `/admin/cache/evict`

---

## Quick start
```bash
mvn spring-boot:run
```

Open Swagger UI at `http://localhost:8080/swagger-ui.html`.

---

## 🔍 How to Test Caching with curl

### 1. First request → MISS (stores in cache)
```bash
curl -i http://localhost:8080/users
```
Response headers will include:
```
X-Cache: MISS
```

### 2. Second request → HIT (served from cache)
```bash
curl -i http://localhost:8080/users
```
Response headers now include:
```
X-Cache: HIT
Age: <seconds since cached>
```

### 3. Check cache stats via Admin API
```bash
curl -s -H "X-API-Key: changeme" http://localhost:8080/admin/cache/stats | jq
```
Example output:
```json
{
  "missCount": 5,
  "estimatedSize": 1,
  "hitCount": 1,
  "hitRate": 0.16666666666666666,
  "evictionCount": 0,
  "maxWeightBytes": 104857600
}
```

### 4. Evict all entries
```bash
curl -X DELETE -H "X-API-Key: changeme" http://localhost:8080/admin/cache/clear
```

### 5. Evict a specific entry
```bash
curl -X DELETE -H "X-API-Key: changeme" \
  "http://localhost:8080/admin/cache/evict?method=GET&pathAndQuery=/users"
```

---

## Notes
- Only `200 OK` with bodies ≤ `gateway.cache.max-body-bytes` are cached.
- Upstream `Cache-Control: no-store` or `private` disables caching (unless normalized by filters).
- Upstream `Cache-Control: max-age=N` is respected (min of upstream and local TTL).
- Add `X-API-Key` header matching `gateway.cache.admin.api-key` to call admin endpoints (leave empty to disable auth).

---

## 📜 License
This project is licensed under the MIT License.  
You are free to use, modify, and distribute this code with proper attribution.  
See the [LICENSE](LICENSE) file for details.

## ⚠️ Disclaimer
This is a **personal project** created independently outside of my employment.  
It is **not a product of any organization**, nor does it represent the views, technologies, or intellectual property of any organization.  
All code is released openly under the chosen license.



