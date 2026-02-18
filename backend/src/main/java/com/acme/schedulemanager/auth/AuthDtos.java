package com.acme.schedulemanager.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {
    public record RegisterRequest(@Email String email, @NotBlank @Size(max = 120) String nickname, @NotBlank String password) {}
    public record LoginRequest(@Email String email, @NotBlank String password) {}
    public record TokenResponse(String accessToken, long expiresInSeconds) {}
    public record UserResponse(String id, String email, String nickname, String role) {}
}
