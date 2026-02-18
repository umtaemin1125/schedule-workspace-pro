package com.acme.schedulemanager.domain.repo;

import com.acme.schedulemanager.domain.entity.BlockDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BlockDocumentRepository extends JpaRepository<BlockDocument, UUID> {
    List<BlockDocument> findByItemIdOrderBySortOrderAsc(UUID itemId);
    long countByItemId(UUID itemId);
    void deleteByItemId(UUID itemId);
}
