package com.proyecto.gateway_filters.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Configuración global de Rate Limiting con Redis y Eureka Discovery.
 * - Las rutas de los servicios se descubren automáticamente (Discovery Locator).
 * - Solo las rutas críticas (ej. OAuth2) se definen en application.properties.
 */
@Slf4j
@Configuration
public class RateLimiterConfig {

    @Value("${custom.rate-limiter.replenish-rate:5}")
    private int replenishRate;

    @Value("${custom.rate-limiter.burst-capacity:10}")
    private int burstCapacity;

    @Value("${custom.rate-limiter.enabled:true}")
    private boolean rateLimiterEnabled;

    /**
     * Resuelve la clave del RateLimiter.
     * Prioriza X-Client-ID → Authorization JWT → IP.
     */
    @Bean
    public KeyResolver customKeyResolver() {
        return exchange -> {
            String key = resolveKey(exchange);
            log.debug("RateLimiter key resolved: {}", key);
            return Mono.just(key);
        };
    }

    private String resolveKey(ServerWebExchange exchange) {
        // 1️⃣ Prioridad: X-Client-ID
        String clientId = exchange.getRequest().getHeaders().getFirst("X-Client-ID");
        if (clientId != null && !clientId.isBlank()) {
            return "client:" + clientId;
        }

        // 2️⃣ Segundo: Authorization (JWT)
        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            int hash = auth.substring(7).hashCode();
            return "token:" + hash;
        }

        // 3️⃣ Fallback: Dirección IP
        if (exchange.getRequest().getRemoteAddress() != null) {
            return "ip:" + exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }

        return "unknown";
    }

    /**
     * Configura RedisRateLimiter de forma global.
     * Si está deshabilitado, permite todas las peticiones.
     */
    @Bean
    public RedisRateLimiter redisRateLimiter() {
        log.info("Configuring RedisRateLimiter with replenishRate={} burstCapacity={} (enabled={})",
                replenishRate, burstCapacity, rateLimiterEnabled);

        if (!rateLimiterEnabled) {
            // Modo libre (sin límites)
            return new RedisRateLimiter(Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        return new RedisRateLimiter(replenishRate, burstCapacity);
    }
}
