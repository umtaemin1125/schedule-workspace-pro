package com.acme.schedulemanager.files;

import com.acme.schedulemanager.domain.entity.FileAsset;
import com.acme.schedulemanager.domain.entity.WorkspaceItem;
import com.acme.schedulemanager.domain.repo.FileAssetRepository;
import com.acme.schedulemanager.domain.repo.WorkspaceItemRepository;
import com.acme.schedulemanager.security.SecurityUtils;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
public class FileController {
    private final StorageService storageService;
    private final FileAssetRepository fileRepo;
    private final WorkspaceItemRepository itemRepo;

    public FileController(StorageService storageService, FileAssetRepository fileRepo, WorkspaceItemRepository itemRepo) {
        this.storageService = storageService;
        this.fileRepo = fileRepo;
        this.itemRepo = itemRepo;
    }

    @PostMapping("/api/files/upload")
    public FileDtos.UploadResponse upload(@RequestParam("itemId") UUID itemId, @RequestParam("file") MultipartFile file) {
        UUID userId = SecurityUtils.principal().userId();
        WorkspaceItem item = itemRepo.findById(itemId).orElseThrow(() -> new EntityNotFoundException("항목을 찾을 수 없습니다."));
        if (!item.getUserId().equals(userId)) throw new IllegalArgumentException("권한이 없습니다.");

        String storedName = storageService.store(file);
        FileAsset asset = new FileAsset();
        asset.setUserId(userId);
        asset.setItemId(itemId);
        asset.setOriginalName(file.getOriginalFilename());
        asset.setStoredName(storedName);
        asset.setMimeType(file.getContentType());
        asset.setSizeBytes(file.getSize());
        fileRepo.save(asset);
        return new FileDtos.UploadResponse(asset.getId(), "/files/" + storedName, asset.getOriginalName(), asset.getMimeType(), asset.getSizeBytes());
    }

    @GetMapping("/api/files/item/{itemId}")
    public List<FileDtos.AssetResponse> listByItem(@PathVariable UUID itemId) {
        UUID userId = SecurityUtils.principal().userId();
        WorkspaceItem item = itemRepo.findById(itemId).orElseThrow(() -> new EntityNotFoundException("항목을 찾을 수 없습니다."));
        if (!item.getUserId().equals(userId)) throw new IllegalArgumentException("권한이 없습니다.");
        return fileRepo.findByItemIdOrderByCreatedAtDesc(itemId).stream()
                .map(v -> new FileDtos.AssetResponse(v.getId(), "/files/" + v.getStoredName(), v.getOriginalName(), v.getMimeType(), v.getSizeBytes()))
                .toList();
    }

    @GetMapping("/files/{storedName}")
    public ResponseEntity<Resource> fetch(@PathVariable String storedName) {
        Resource resource = storageService.load(storedName);
        MediaType mediaType = fileRepo.findFirstByStoredName(storedName)
                .map(FileAsset::getMimeType)
                .map(MediaType::parseMediaType)
                .orElseGet(() -> MediaTypeFactory.getMediaType(storedName).orElse(MediaType.APPLICATION_OCTET_STREAM));
        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }
}
