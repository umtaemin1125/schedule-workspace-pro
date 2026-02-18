package com.acme.schedulemanager.domain.repo;

import com.acme.schedulemanager.domain.entity.WorkspaceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WorkspaceItemRepository extends JpaRepository<WorkspaceItem, UUID> {
    List<WorkspaceItem> findByUserIdOrderByUpdatedAtDesc(UUID userId);
    List<WorkspaceItem> findByUserIdAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(UUID userId, String keyword);
    List<WorkspaceItem> findByUserIdAndDueDateOrderByUpdatedAtDesc(UUID userId, LocalDate dueDate);
    List<WorkspaceItem> findByUserIdAndDueDateBetweenOrderByDueDateDescUpdatedAtDesc(UUID userId, LocalDate from, LocalDate to);
}
