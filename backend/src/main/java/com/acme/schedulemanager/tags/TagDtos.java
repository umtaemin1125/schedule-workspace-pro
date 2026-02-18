package com.acme.schedulemanager.tags;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public class TagDtos {
    public record CreateTagRequest(@NotBlank String name) {}
    public record TagResponse(UUID id, String name) {}
}
