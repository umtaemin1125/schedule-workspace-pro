package com.acme.schedulemanager.admin;

import java.util.UUID;

public class AdminDtos {
    public record StatsResponse(long totalUsers, long totalItems, long totalBlocks, long totalFiles) {}
    public record UserRow(UUID id, String email, String role, int failedLoginCount) {}
}
