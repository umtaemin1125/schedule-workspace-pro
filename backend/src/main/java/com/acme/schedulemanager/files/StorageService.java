package com.acme.schedulemanager.files;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    String store(MultipartFile file);
    String store(String originalName, String mimeType, byte[] bytes);
    Resource load(String storedName);
}
