package com.acme.schedulemanager.domain.repo;

import com.acme.schedulemanager.domain.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID> {
    List<Tag> findByUserIdOrderByNameAsc(UUID userId);
    boolean existsByUserIdAndName(UUID userId, String name);
}
