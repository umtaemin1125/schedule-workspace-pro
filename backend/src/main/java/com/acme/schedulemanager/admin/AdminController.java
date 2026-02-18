package com.acme.schedulemanager.admin;

import com.acme.schedulemanager.domain.entity.BlockDocument;
import com.acme.schedulemanager.domain.entity.DayNote;
import com.acme.schedulemanager.domain.entity.UserAccount;
import com.acme.schedulemanager.domain.entity.WorkspaceItem;
import com.acme.schedulemanager.domain.repo.BlockDocumentRepository;
import com.acme.schedulemanager.domain.repo.DayNoteRepository;
import com.acme.schedulemanager.domain.repo.FileAssetRepository;
import com.acme.schedulemanager.domain.repo.UserAccountRepository;
import com.acme.schedulemanager.domain.repo.WorkspaceItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final UserAccountRepository userRepo;
    private final WorkspaceItemRepository itemRepo;
    private final BlockDocumentRepository blockRepo;
    private final DayNoteRepository dayNoteRepo;
    private final FileAssetRepository fileRepo;
    private final ObjectMapper objectMapper;

    public AdminController(
            UserAccountRepository userRepo,
            WorkspaceItemRepository itemRepo,
            BlockDocumentRepository blockRepo,
            DayNoteRepository dayNoteRepo,
            FileAssetRepository fileRepo,
            ObjectMapper objectMapper
    ) {
        this.userRepo = userRepo;
        this.itemRepo = itemRepo;
        this.blockRepo = blockRepo;
        this.dayNoteRepo = dayNoteRepo;
        this.fileRepo = fileRepo;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/stats")
    public AdminDtos.StatsResponse stats() {
        return new AdminDtos.StatsResponse(
                userRepo.count(),
                itemRepo.count(),
                blockRepo.count(),
                fileRepo.count()
        );
    }

    @GetMapping("/users")
    public List<AdminDtos.UserRow> users() {
        return userRepo.findAll().stream()
                .map(u -> new AdminDtos.UserRow(
                        u.getId(),
                        u.getEmail(),
                        u.getNickname(),
                        u.getRole(),
                        u.getFailedLoginCount(),
                        u.getLockedUntil(),
                        u.getCreatedAt(),
                        itemRepo.findByUserIdOrderByUpdatedAtDesc(u.getId()).size()
                ))
                .toList();
    }

    @GetMapping("/users/{userId}/items")
    public List<AdminDtos.UserItemRow> userItems(@PathVariable UUID userId) {
        userRepo.findById(userId).orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
        return itemRepo.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(item -> new AdminDtos.UserItemRow(
                        item.getId(),
                        item.getTitle(),
                        item.getStatus(),
                        item.getDueDate(),
                        item.getTemplateType(),
                        item.getUpdatedAt(),
                        blockRepo.countByItemId(item.getId()),
                        fileRepo.countByItemId(item.getId())
                ))
                .toList();
    }

    @GetMapping("/users/{userId}/items/{itemId}/blocks")
    public AdminDtos.UserItemBlocksResponse userItemBlocks(@PathVariable UUID userId, @PathVariable UUID itemId) {
        WorkspaceItem item = itemRepo.findById(itemId).orElseThrow(() -> new EntityNotFoundException("일정을 찾을 수 없습니다."));
        if (!item.getUserId().equals(userId)) {
            throw new IllegalArgumentException("사용자와 일정이 일치하지 않습니다.");
        }
        var blocks = blockRepo.findByItemIdOrderBySortOrderAsc(itemId).stream()
                .map(b -> new AdminDtos.BlockRow(b.getId(), b.getSortOrder(), b.getType(), b.getContent()))
                .toList();
        return new AdminDtos.UserItemBlocksResponse(userId, itemId, blocks);
    }

    @PatchMapping("/users/{userId}/role")
    @Transactional
    public AdminDtos.UserRow updateRole(@PathVariable UUID userId, @RequestBody AdminDtos.UserRoleUpdateRequest request) {
        UserAccount user = userRepo.findById(userId).orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
        String role = request.role() == null ? "USER" : request.role().toUpperCase();
        if (!"ADMIN".equals(role) && !"USER".equals(role)) {
            throw new IllegalArgumentException("권한은 USER 또는 ADMIN 만 허용됩니다.");
        }
        user.setRole(role);
        userRepo.save(user);
        return new AdminDtos.UserRow(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                user.getFailedLoginCount(),
                user.getLockedUntil(),
                user.getCreatedAt(),
                itemRepo.findByUserIdOrderByUpdatedAtDesc(user.getId()).size()
        );
    }

    @PostMapping("/users/{userId}/unlock")
    @Transactional
    public void unlockUser(@PathVariable UUID userId) {
        UserAccount user = userRepo.findById(userId).orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepo.save(user);
    }

    @DeleteMapping("/users/{userId}")
    @Transactional
    public void deleteUser(@PathVariable UUID userId) {
        UserAccount user = userRepo.findById(userId).orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
        userRepo.delete(user);
    }

    @DeleteMapping("/users/{userId}/items/{itemId}")
    @Transactional
    public void deleteUserItem(@PathVariable UUID userId, @PathVariable UUID itemId) {
        WorkspaceItem item = itemRepo.findById(itemId).orElseThrow(() -> new EntityNotFoundException("일정을 찾을 수 없습니다."));
        if (!item.getUserId().equals(userId)) throw new IllegalArgumentException("사용자와 일정이 일치하지 않습니다.");
        itemRepo.delete(item);
    }

    @GetMapping("/users/{userId}/items/{itemId}/detail")
    public AdminDtos.UserItemDetailResponse userItemDetail(@PathVariable UUID userId, @PathVariable UUID itemId) {
        WorkspaceItem item = itemRepo.findById(itemId).orElseThrow(() -> new EntityNotFoundException("일정을 찾을 수 없습니다."));
        if (!item.getUserId().equals(userId)) throw new IllegalArgumentException("사용자와 일정이 일치하지 않습니다.");

        String html = "";
        var block = blockRepo.findFirstByItemIdOrderBySortOrderAsc(itemId).orElse(null);
        if (block != null) {
            try {
                Map<String, Object> payload = objectMapper.readValue(block.getContent(), Map.class);
                html = String.valueOf(payload.getOrDefault("html", ""));
            } catch (Exception ignored) {
            }
        }

        String issue = "";
        String memo = "";
        if (item.getDueDate() != null) {
            DayNote note = dayNoteRepo.findByUserIdAndDueDate(userId, item.getDueDate()).orElse(null);
            if (note != null) {
                issue = note.getIssue();
                memo = note.getMemo();
            }
        }

        return new AdminDtos.UserItemDetailResponse(
                userId, itemId, item.getTitle(), item.getStatus(), item.getDueDate(), item.getTemplateType(), html, issue, memo
        );
    }

    @PutMapping("/users/{userId}/items/{itemId}/detail")
    @Transactional
    public AdminDtos.UserItemDetailResponse updateUserItemDetail(
            @PathVariable UUID userId,
            @PathVariable UUID itemId,
            @RequestBody AdminDtos.UserItemDetailUpdateRequest request
    ) throws Exception {
        WorkspaceItem item = itemRepo.findById(itemId).orElseThrow(() -> new EntityNotFoundException("일정을 찾을 수 없습니다."));
        if (!item.getUserId().equals(userId)) throw new IllegalArgumentException("사용자와 일정이 일치하지 않습니다.");

        if (request.title() != null && !request.title().isBlank()) item.setTitle(request.title());
        if (request.status() != null && !request.status().isBlank()) item.setStatus(request.status());
        if (request.dueDate() != null) item.setDueDate(request.dueDate());
        if (request.templateType() != null && !request.templateType().isBlank()) item.setTemplateType(request.templateType());
        itemRepo.save(item);

        blockRepo.deleteByItemId(itemId);
        BlockDocument block = new BlockDocument();
        block.setItemId(itemId);
        block.setSortOrder(0);
        block.setType("paragraph");
        block.setContent(objectMapper.writeValueAsString(Map.of("html", request.html() == null ? "" : request.html())));
        blockRepo.save(block);

        if (item.getDueDate() != null) {
            DayNote note = dayNoteRepo.findByUserIdAndDueDate(userId, item.getDueDate()).orElseGet(() -> {
                DayNote n = new DayNote();
                n.setUserId(userId);
                n.setDueDate(item.getDueDate());
                return n;
            });
            note.setIssue(request.issue() == null ? "" : request.issue());
            note.setMemo(request.memo() == null ? "" : request.memo());
            dayNoteRepo.save(note);
        }

        return userItemDetail(userId, itemId);
    }
}
