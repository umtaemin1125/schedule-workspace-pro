package com.acme.schedulemanager.workspace;

import com.acme.schedulemanager.domain.entity.ItemTag;
import com.acme.schedulemanager.domain.entity.WorkspaceItem;
import com.acme.schedulemanager.domain.repo.ItemTagRepository;
import com.acme.schedulemanager.domain.repo.WorkspaceItemRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WorkspaceService {
    private final WorkspaceItemRepository itemRepo;
    private final ItemTagRepository itemTagRepo;

    public WorkspaceService(WorkspaceItemRepository itemRepo, ItemTagRepository itemTagRepo) {
        this.itemRepo = itemRepo;
        this.itemTagRepo = itemTagRepo;
    }

    @Transactional
    public WorkspaceDtos.ItemResponse create(UUID userId, WorkspaceDtos.ItemRequest request) {
        WorkspaceItem item = new WorkspaceItem();
        item.setUserId(userId);
        item.setTitle(request.title());
        item.setParentId(request.parentId());
        item.setStatus("todo");
        itemRepo.save(item);
        return toResponse(item);
    }

    public List<WorkspaceDtos.ItemResponse> findAll(UUID userId) {
        return itemRepo.findByUserIdOrderByUpdatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    public List<WorkspaceDtos.ItemResponse> search(UUID userId, String keyword) {
        return itemRepo.findByUserIdAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(userId, keyword).stream().map(this::toResponse).toList();
    }

    @Transactional
    public WorkspaceDtos.ItemResponse update(UUID userId, UUID itemId, WorkspaceDtos.ItemUpdateRequest request) {
        WorkspaceItem item = itemRepo.findById(itemId).orElseThrow(() -> new EntityNotFoundException("항목을 찾을 수 없습니다."));
        if (!item.getUserId().equals(userId)) throw new IllegalArgumentException("권한이 없습니다.");
        if (request.title() != null && !request.title().isBlank()) item.setTitle(request.title());
        if (request.status() != null) item.setStatus(request.status());
        if (request.dueDate() != null) item.setDueDate(request.dueDate());
        if (request.parentId() != null || request.parentId() == null) item.setParentId(request.parentId());

        if (request.tagIds() != null) {
            itemTagRepo.deleteByItemId(itemId);
            request.tagIds().forEach(tagId -> {
                ItemTag map = new ItemTag();
                map.setItemId(itemId);
                map.setTagId(tagId);
                itemTagRepo.save(map);
            });
        }

        return toResponse(item);
    }

    @Transactional
    public void delete(UUID userId, UUID itemId) {
        WorkspaceItem item = itemRepo.findById(itemId).orElseThrow(() -> new EntityNotFoundException("항목을 찾을 수 없습니다."));
        if (!item.getUserId().equals(userId)) throw new IllegalArgumentException("권한이 없습니다.");
        itemRepo.delete(item);
    }

    private WorkspaceDtos.ItemResponse toResponse(WorkspaceItem item) {
        return new WorkspaceDtos.ItemResponse(item.getId(), item.getParentId(), item.getTitle(), item.getStatus(), item.getDueDate(), item.getUpdatedAt());
    }
}
