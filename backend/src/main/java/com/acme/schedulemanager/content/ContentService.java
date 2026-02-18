package com.acme.schedulemanager.content;

import com.acme.schedulemanager.domain.entity.BlockDocument;
import com.acme.schedulemanager.domain.entity.WorkspaceItem;
import com.acme.schedulemanager.domain.repo.BlockDocumentRepository;
import com.acme.schedulemanager.domain.repo.WorkspaceItemRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ContentService {
    private final BlockDocumentRepository blockRepo;
    private final WorkspaceItemRepository itemRepo;

    public ContentService(BlockDocumentRepository blockRepo, WorkspaceItemRepository itemRepo) {
        this.blockRepo = blockRepo;
        this.itemRepo = itemRepo;
    }

    public ContentDtos.BlocksResponse load(UUID userId, UUID itemId) {
        WorkspaceItem item = verifyOwner(userId, itemId);
        List<ContentDtos.BlockPayload> blocks = blockRepo.findByItemIdOrderBySortOrderAsc(item.getId()).stream()
                .map(b -> new ContentDtos.BlockPayload(b.getId(), b.getSortOrder(), b.getType(), b.getContent()))
                .toList();
        return new ContentDtos.BlocksResponse(itemId, blocks);
    }

    @Transactional
    public ContentDtos.BlocksResponse save(UUID userId, UUID itemId, ContentDtos.SaveBlocksRequest request) {
        WorkspaceItem item = verifyOwner(userId, itemId);
        blockRepo.deleteByItemId(item.getId());
        List<ContentDtos.BlockPayload> saved = request.blocks().stream().map(payload -> {
            BlockDocument b = new BlockDocument();
            b.setItemId(itemId);
            b.setSortOrder(payload.sortOrder());
            b.setType(payload.type());
            b.setContent(payload.content());
            blockRepo.save(b);
            return new ContentDtos.BlockPayload(b.getId(), b.getSortOrder(), b.getType(), b.getContent());
        }).toList();
        return new ContentDtos.BlocksResponse(itemId, saved);
    }

    private WorkspaceItem verifyOwner(UUID userId, UUID itemId) {
        WorkspaceItem item = itemRepo.findById(itemId).orElseThrow(() -> new EntityNotFoundException("항목을 찾을 수 없습니다."));
        if (!item.getUserId().equals(userId)) throw new IllegalArgumentException("권한이 없습니다.");
        return item;
    }
}
