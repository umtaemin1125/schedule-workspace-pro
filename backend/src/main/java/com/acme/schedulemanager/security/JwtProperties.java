package com.acme.schedulemanager.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String accessSecret, String refreshSecret, long accessExpSeconds, long refreshExpSeconds) {
}
