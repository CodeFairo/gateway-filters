package com.proyecto.gateway_filters.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/**
 * Crea ReactiveJwtDecoder solo si existe la property auth.server.jwks-uri.
 * Esto evita que el bean intente resolverse en entornos de test sin config.
 */
@Configuration
@ConditionalOnProperty(name = "auth.server.jwks-uri")
public class GatewayJwtConfig {

    @Value("${auth.server.jwks-uri}")
    private String jwksUri;

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwksUri).build();
    }
}
