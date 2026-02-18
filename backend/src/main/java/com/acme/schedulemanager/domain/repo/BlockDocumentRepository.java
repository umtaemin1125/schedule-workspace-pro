package com.acme.schedulemanager.domain.repo;

import com.acme.schedulemanager.domain.entity.BlockDocument;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlockDocumentRepository extends JpaRepository<BlockDocument, UUID> {
    List<BlockDocument> findByItemIdOrderBySortOrderAsc(UUID itemId);
    Optional<BlockDocument> findFirstByItemIdOrderBySortOrderAsc(UUID itemId);
    long countByItemId(UUID itemId);
    void deleteByItemId(UUID itemId);

    @Query(value = "select distinct b.item_id from blocks b where lower(cast(b.content as text)) like lower(concat('%', :keyword, '%'))", nativeQuery = true)
    List<UUID> searchItemIdsByKeyword(@Param("keyword") String keyword);
}
