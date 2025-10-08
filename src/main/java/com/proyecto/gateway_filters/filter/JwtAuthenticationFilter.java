package com.proyecto.gateway_filters.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final ReactiveJwtDecoder jwtDecoder;
    private final List<String> whitelist; // patterns already lower-cased
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthenticationFilter(ReactiveJwtDecoder jwtDecoder,
                                   @Value("${gateway.security.whitelist:}") String whitelistProp) {
        this.jwtDecoder = jwtDecoder;
        if (whitelistProp == null || whitelistProp.isBlank()) {
            this.whitelist = List.of();
        } else {
            // normalize patterns to lower-case and trim
            this.whitelist = Arrays.stream(whitelistProp.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toList());
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isWhitelisted(String path) {
        if (path == null) return false;
        String lowerPath = path.toLowerCase(Locale.ROOT);
        for (String pattern : whitelist) {
            if (pathMatcher.match(pattern, lowerPath)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        log.debug("Incoming request path: {}", path);

        if (isWhitelisted(path)) {
            log.debug("Path whitelisted, skipping JWT validation: {}", path);
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7).trim();
        log.debug("Validating token for path {} ...", path);

        return jwtDecoder.decode(token)
                .flatMap((Jwt jwt) -> {
                    exchange.getAttributes().put("jwt", jwt);
                    exchange.getAttributes().put("principal", jwt.getSubject());

                    JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
                    SecurityContextImpl securityContext = new SecurityContextImpl(authentication);

                    log.debug("Token validated for subject={}, jti={}", jwt.getSubject(), jwt.getId());

                    return chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));
                })
                .onErrorResume(ex -> {
                    log.warn("JWT validation failed for path {}: {}", path, ex.toString());
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                });
    }
}
