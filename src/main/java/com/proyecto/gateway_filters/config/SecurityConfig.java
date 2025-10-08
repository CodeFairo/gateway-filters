package com.proyecto.gateway_filters.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {

        // Deshabilita CSRF para APIs (usamos Bearer tokens)
        http.csrf(csrf -> csrf.disable());

        http
                .authorizeExchange(ex -> ex
                        // Rutas públicas (whitelist) — patrones genéricos con posible prefijo
                        .pathMatchers("/api-oauth2/**", "/*/api-oauth2/**", "/.well-known/**", "/*/.well-known/**", "/actuator/health").permitAll()
                        // todo lo demás requiere autenticación
                        .anyExchange().authenticated()
                )
                // delega la verificación JWT a Spring Security (oauth2 resource server)
                .oauth2ResourceServer(oauth2 -> oauth2.jwt());

        return http.build();
    }
}
