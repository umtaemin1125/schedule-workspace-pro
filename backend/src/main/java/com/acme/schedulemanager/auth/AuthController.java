package com.acme.schedulemanager.auth;

import com.acme.schedulemanager.security.AuthPrincipal;
import com.acme.schedulemanager.security.SecurityUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final LoginAttemptRateLimiter limiter;

    public AuthController(AuthService authService, LoginAttemptRateLimiter limiter) {
        this.authService = authService;
        this.limiter = limiter;
    }

    @PostMapping("/register")
    public AuthDtos.UserResponse register(@RequestBody @Valid AuthDtos.RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthDtos.TokenResponse login(@RequestBody @Valid AuthDtos.LoginRequest request, HttpServletResponse response, HttpServletRequest httpServletRequest) {
        limiter.check("login:" + httpServletRequest.getRemoteAddr(), 20, Duration.ofMinutes(1));
        var session = authService.login(request);
        setRefreshCookies(response, session.refreshToken());
        return new AuthDtos.TokenResponse(session.accessToken(), session.accessExpiresIn());
    }

    @PostMapping("/refresh")
    public AuthDtos.TokenResponse refresh(HttpServletRequest request, HttpServletResponse response, @RequestHeader(value = "X-CSRF-TOKEN", required = false) String csrfHeader) {
        limiter.check("refresh:" + request.getRemoteAddr(), 30, Duration.ofMinutes(1));
        String refreshToken = cookieValue(request, "refresh_token");
        String csrfCookie = cookieValue(request, "csrf_token");
        var session = authService.refresh(refreshToken, csrfHeader, csrfCookie);
        setRefreshCookies(response, session.refreshToken());
        return new AuthDtos.TokenResponse(session.accessToken(), session.accessExpiresIn());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthPrincipal principal, HttpServletResponse response) {
        AuthPrincipal p = principal != null ? principal : SecurityUtils.principal();
        authService.logout(p.userId());
        clearRefreshCookies(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AuthDtos.UserResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        AuthPrincipal p = principal != null ? principal : SecurityUtils.principal();
        return new AuthDtos.UserResponse(p.userId().toString(), p.email(), p.role());
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies()).filter(c -> c.getName().equals(name)).map(Cookie::getValue).findFirst().orElse(null);
    }

    private void setRefreshCookies(HttpServletResponse response, String refreshToken) {
        ResponseCookie refresh = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(false)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofDays(14))
                .build();
        ResponseCookie csrf = ResponseCookie.from("csrf_token", UUID.randomUUID().toString())
                .httpOnly(false)
                .secure(false)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofDays(14))
                .build();
        response.addHeader("Set-Cookie", refresh.toString());
        response.addHeader("Set-Cookie", csrf.toString());
    }

    private void clearRefreshCookies(HttpServletResponse response) {
        ResponseCookie refresh = ResponseCookie.from("refresh_token", "").path("/").maxAge(0).build();
        ResponseCookie csrf = ResponseCookie.from("csrf_token", "").path("/").maxAge(0).build();
        response.addHeader("Set-Cookie", refresh.toString());
        response.addHeader("Set-Cookie", csrf.toString());
    }
}
