# reactive-gateway-cache
Reactive edge gateway using Spring Cloud Gateway on Spring Boot 3.5, with an in-memory Caffeine cache and early-bootstrap configuration. The cache will short-circuit repeat GETs by serving cached bodies directly, with safe defaults (TTL, size limits, and header hygiene).
