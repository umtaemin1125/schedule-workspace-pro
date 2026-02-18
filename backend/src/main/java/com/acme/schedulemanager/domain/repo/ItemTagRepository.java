package com.acme.schedulemanager.domain.repo;

import com.acme.schedulemanager.domain.entity.ItemTag;
import com.acme.schedulemanager.domain.entity.ItemTagId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ItemTagRepository extends JpaRepository<ItemTag, ItemTagId> {
    List<ItemTag> findByItemId(UUID itemId);
    void deleteByItemId(UUID itemId);
}
