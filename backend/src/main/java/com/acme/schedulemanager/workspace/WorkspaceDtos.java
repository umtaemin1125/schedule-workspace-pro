package com.acme.schedulemanager.workspace;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class WorkspaceDtos {
    public record ItemRequest(@NotBlank String title, UUID parentId, LocalDate dueDate, String templateType) {}
    public record ItemUpdateRequest(String title, String status, LocalDate dueDate, String templateType, List<UUID> tagIds, UUID parentId) {}
    public record ItemResponse(UUID id, UUID parentId, String title, String status, LocalDate dueDate, String templateType, Instant updatedAt) {}
    public record BoardRowResponse(
            UUID id,
            UUID parentId,
            LocalDate dueDate,
            String title,
            String status,
            String templateType,
            String todayWork,
            String issue,
            String memo,
            int checklistTotal,
            int checklistDone
    ) {}
}
