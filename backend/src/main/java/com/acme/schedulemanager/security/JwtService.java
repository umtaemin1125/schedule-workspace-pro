package com.acme.schedulemanager.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {
    private final JwtProperties props;

    public JwtService(JwtProperties props) {
        this.props = props;
    }

    public String createAccessToken(UUID userId, String email, String nickname, String role) {
        return createToken(userId, email, nickname, role, props.accessExpSeconds(), key(props.accessSecret()));
    }

    public String createRefreshToken(UUID userId, String email, String nickname, String role) {
        return createToken(userId, email, nickname, role, props.refreshExpSeconds(), key(props.refreshSecret()));
    }

    public Claims parseAccess(String token) {
        return parse(token, key(props.accessSecret()));
    }

    public Claims parseRefresh(String token) {
        return parse(token, key(props.refreshSecret()));
    }

    private String createToken(UUID userId, String email, String nickname, String role, long expSeconds, SecretKey key) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claims(Map.of("role", role, "email", email, "nickname", nickname))
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expSeconds)))
                .signWith(key)
                .compact();
    }

    private Claims parse(String token, SecretKey key) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    private SecretKey key(String raw) {
        return Keys.hmacShaKeyFor(raw.getBytes(StandardCharsets.UTF_8));
    }
}
