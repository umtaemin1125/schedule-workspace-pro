package com.acme.schedulemanager.auth;

import com.acme.schedulemanager.domain.entity.UserAccount;
import com.acme.schedulemanager.domain.repo.UserAccountRepository;
import com.acme.schedulemanager.security.JwtProperties;
import com.acme.schedulemanager.security.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class AuthService {
    private final UserAccountRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate redis;

    @Value("${app.auth.max-failed-login:5}")
    private int maxFailedLogin;

    @Value("${app.auth.lock-minutes:15}")
    private int lockMinutes;

    public AuthService(UserAccountRepository userRepo, PasswordEncoder passwordEncoder, JwtService jwtService, JwtProperties jwtProperties, StringRedisTemplate redis) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.redis = redis;
    }

    @Transactional
    public AuthDtos.UserResponse register(AuthDtos.RegisterRequest request) {
        userRepo.findByEmail(request.email()).ifPresent(it -> { throw new IllegalArgumentException("이미 사용 중인 이메일입니다."); });
        UserAccount user = new UserAccount();
        user.setEmail(request.email().toLowerCase());
        user.setNickname(request.nickname().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole("USER");
        user.setFailedLoginCount(0);
        userRepo.save(user);
        return new AuthDtos.UserResponse(user.getId().toString(), user.getEmail(), user.getNickname(), user.getRole());
    }

    @Transactional
    public AuthSession login(AuthDtos.LoginRequest request) {
        UserAccount user = userRepo.findByEmail(request.email().toLowerCase()).orElseThrow(() -> new EntityNotFoundException("계정을 찾을 수 없습니다."));
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new IllegalArgumentException("계정이 잠겨 있습니다. 잠시 후 다시 시도하세요.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.setFailedLoginCount(user.getFailedLoginCount() + 1);
            if (user.getFailedLoginCount() >= maxFailedLogin) {
                user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(lockMinutes)));
                user.setFailedLoginCount(0);
            }
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);

        String access = jwtService.createAccessToken(user.getId(), user.getEmail(), user.getNickname(), user.getRole());
        String refresh = jwtService.createRefreshToken(user.getId(), user.getEmail(), user.getNickname(), user.getRole());
        String key = refreshKey(user.getId());
        redis.opsForValue().set(key, refresh, Duration.ofSeconds(jwtProperties.refreshExpSeconds()));
        return new AuthSession(access, refresh, jwtProperties.accessExpSeconds(), user);
    }

    public AuthSession refresh(String refreshToken, String csrfHeader, String csrfCookie) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("리프레시 토큰이 없습니다.");
        }
        if (!Objects.equals(csrfHeader, csrfCookie)) {
            throw new IllegalArgumentException("CSRF 검증에 실패했습니다.");
        }

        Claims claims = jwtService.parseRefresh(refreshToken);
        UUID userId = UUID.fromString(claims.getSubject());
        String key = refreshKey(userId);
        String saved = redis.opsForValue().get(key);
        if (!refreshToken.equals(saved)) {
            throw new IllegalArgumentException("리프레시 토큰이 유효하지 않습니다.");
        }

        UserAccount user = userRepo.findById(userId).orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
        String access = jwtService.createAccessToken(userId, user.getEmail(), user.getNickname(), user.getRole());
        String rotatedRefresh = jwtService.createRefreshToken(userId, user.getEmail(), user.getNickname(), user.getRole());
        redis.opsForValue().set(key, rotatedRefresh, Duration.ofSeconds(jwtProperties.refreshExpSeconds()));
        return new AuthSession(access, rotatedRefresh, jwtProperties.accessExpSeconds(), user);
    }

    public void logout(UUID userId) {
        redis.delete(refreshKey(userId));
    }

    public record AuthSession(String accessToken, String refreshToken, long accessExpiresIn, UserAccount user) {}

    private String refreshKey(UUID userId) {
        return "refresh:" + userId;
    }
}
