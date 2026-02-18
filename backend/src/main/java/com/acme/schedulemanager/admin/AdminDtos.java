package com.acme.schedulemanager.admin;

import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class AdminDtos {
    public record StatsResponse(long totalUsers, long totalItems, long totalBlocks, long totalFiles) {}
    public record UserRow(UUID id, String email, String nickname, String role, int failedLoginCount, Instant lockedUntil, Instant createdAt, long itemCount) {}
    public record UserItemRow(UUID id, String title, String status, LocalDate dueDate, String templateType, Instant updatedAt, long blockCount, long fileCount) {}
    public record BlockRow(UUID id, int sortOrder, String type, String content) {}
    public record UserItemBlocksResponse(UUID userId, UUID itemId, List<BlockRow> blocks) {}
    public record UserRoleUpdateRequest(String role) {}
}
