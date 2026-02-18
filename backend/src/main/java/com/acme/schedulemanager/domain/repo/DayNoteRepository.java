package com.acme.schedulemanager.domain.repo;

import com.acme.schedulemanager.domain.entity.DayNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface DayNoteRepository extends JpaRepository<DayNote, UUID> {
    Optional<DayNote> findByUserIdAndDueDate(UUID userId, LocalDate dueDate);
}
