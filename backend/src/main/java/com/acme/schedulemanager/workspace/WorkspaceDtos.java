package com.acme.schedulemanager.workspace;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class WorkspaceDtos {
    public record ItemRequest(@NotBlank String title, UUID parentId, LocalDate dueDate) {}
    public record ItemUpdateRequest(String title, String status, LocalDate dueDate, List<UUID> tagIds, UUID parentId) {}
    public record ItemResponse(UUID id, UUID parentId, String title, String status, LocalDate dueDate, Instant updatedAt) {}
}
