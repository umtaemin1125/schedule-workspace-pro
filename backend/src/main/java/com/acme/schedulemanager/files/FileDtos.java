package com.acme.schedulemanager.files;

import java.util.UUID;

public class FileDtos {
    public record UploadResponse(UUID id, String url, String originalName, String mimeType, long sizeBytes) {}
}
