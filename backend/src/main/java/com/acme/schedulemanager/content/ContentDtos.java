package com.acme.schedulemanager.content;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public class ContentDtos {
    public record BlockPayload(UUID id, int sortOrder, @NotBlank String type, @NotBlank String content) {}
    public record SaveBlocksRequest(List<BlockPayload> blocks) {}
    public record BlocksResponse(UUID itemId, List<BlockPayload> blocks) {}
}
