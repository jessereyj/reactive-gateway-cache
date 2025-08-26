# ☁️ Reactive Edge Gateway with Caffeine Cache

A fully reactive edge gateway built with **Spring Boot 3.5.14** and **Spring Cloud Gateway**, featuring an **in-memory Caffeine cache** that short-circuits repeat GET requests by serving cached bodies directly. Designed for high-throughput environments with safe defaults for TTL, size limits, and header hygiene.

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
| Spring Boot            | 3.5.14         |
| Spring Cloud Gateway   | 2024.0.3       |
| Spring WebFlux         | Reactive       |
| Caffeine Cache         | Latest stable  |
| Java                   | 21             |

---

## 🛠️ Setup

### Clone the project

```bash
git clone https://github.com/your-org/reactive-gateway-cache.git
cd reactive-gateway-cache
