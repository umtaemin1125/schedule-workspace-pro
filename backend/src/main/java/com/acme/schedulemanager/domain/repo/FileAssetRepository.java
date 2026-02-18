package com.acme.schedulemanager.domain.repo;

import com.acme.schedulemanager.domain.entity.FileAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FileAssetRepository extends JpaRepository<FileAsset, UUID> {
    List<FileAsset> findByItemId(UUID itemId);
    long countByItemId(UUID itemId);
}
