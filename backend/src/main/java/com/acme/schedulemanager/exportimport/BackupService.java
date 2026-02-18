package com.acme.schedulemanager.exportimport;

import com.acme.schedulemanager.domain.entity.BlockDocument;
import com.acme.schedulemanager.domain.entity.FileAsset;
import com.acme.schedulemanager.domain.entity.WorkspaceItem;
import com.acme.schedulemanager.domain.repo.BlockDocumentRepository;
import com.acme.schedulemanager.domain.repo.FileAssetRepository;
import com.acme.schedulemanager.domain.repo.WorkspaceItemRepository;
import com.acme.schedulemanager.files.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class BackupService {
    private final WorkspaceItemRepository itemRepo;
    private final BlockDocumentRepository blockRepo;
    private final FileAssetRepository fileRepo;
    private final ObjectMapper objectMapper;
    private final StorageService storageService;

    public BackupService(WorkspaceItemRepository itemRepo, BlockDocumentRepository blockRepo, FileAssetRepository fileRepo, ObjectMapper objectMapper, StorageService storageService) {
        this.itemRepo = itemRepo;
        this.blockRepo = blockRepo;
        this.fileRepo = fileRepo;
        this.objectMapper = objectMapper;
        this.storageService = storageService;
    }

    public void exportAll(UUID userId, OutputStream outputStream) throws IOException {
        List<WorkspaceItem> items = itemRepo.findByUserIdOrderByUpdatedAtDesc(userId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("exportedAt", Instant.now().toString());
        payload.put("items", items.stream().map(item -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", item.getId());
            m.put("parentId", item.getParentId());
            m.put("title", item.getTitle());
            m.put("status", item.getStatus());
            m.put("dueDate", item.getDueDate());
            m.put("blocks", blockRepo.findByItemIdOrderBySortOrderAsc(item.getId()));
            m.put("files", fileRepo.findByItemId(item.getId()));
            return m;
        }).toList());

        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(outputStream)) {
            zip.putArchiveEntry(new ZipArchiveEntry("backup.json"));
            zip.write(objectMapper.writeValueAsBytes(payload));
            zip.closeArchiveEntry();

            for (WorkspaceItem item : items) {
                for (FileAsset file : fileRepo.findByItemId(item.getId())) {
                    var resource = storageService.load(file.getStoredName());
                    if (!resource.exists()) continue;
                    zip.putArchiveEntry(new ZipArchiveEntry("files/" + file.getStoredName()));
                    try (InputStream in = resource.getInputStream()) {
                        in.transferTo(zip);
                    }
                    zip.closeArchiveEntry();
                }
            }
            zip.finish();
        }
    }

    @Transactional
    public BackupImportReport importAll(UUID userId, MultipartFile file) {
        List<String> errors = new ArrayList<>();
        int importedItems = 0;
        try (ZipArchiveInputStream zip = new ZipArchiveInputStream(file.getInputStream(), StandardCharsets.UTF_8.name(), true, true, true)) {
            ZipArchiveEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("backup.json".equals(entry.getName())) {
                    Map<?, ?> payload = objectMapper.readValue(zip.readAllBytes(), Map.class);
                    Object rawItems = payload.get("items");
                    if (!(rawItems instanceof List<?> items)) {
                        continue;
                    }
                    for (Object obj : items) {
                        if (!(obj instanceof Map<?, ?> rawMap)) {
                            continue;
                        }
                        @SuppressWarnings("unchecked")
                        Map<String, Object> raw = (Map<String, Object>) rawMap;
                        try {
                            WorkspaceItem item = new WorkspaceItem();
                            item.setUserId(userId);
                            item.setTitle((String) raw.getOrDefault("title", "제목 없음"));
                            item.setStatus((String) raw.getOrDefault("status", "todo"));
                            itemRepo.save(item);
                            importedItems++;

                            List<Map<String, Object>> blocks = (List<Map<String, Object>>) raw.getOrDefault("blocks", List.of());
                            int order = 0;
                            for (Map<String, Object> b : blocks) {
                                BlockDocument block = new BlockDocument();
                                block.setItemId(item.getId());
                                block.setSortOrder(order++);
                                block.setType((String) b.getOrDefault("type", "paragraph"));
                                block.setContent(objectMapper.writeValueAsString(b.getOrDefault("content", Map.of("text", ""))));
                                blockRepo.save(block);
                            }
                        } catch (Exception e) {
                            errors.add("항목 복원 실패: " + e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            errors.add("ZIP 파싱 실패: " + e.getMessage());
        }
        return new BackupImportReport(importedItems, errors);
    }

    public record BackupImportReport(int importedItems, List<String> errors) {}
}
