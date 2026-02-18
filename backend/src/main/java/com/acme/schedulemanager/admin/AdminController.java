package com.acme.schedulemanager.admin;

import com.acme.schedulemanager.domain.entity.UserAccount;
import com.acme.schedulemanager.domain.entity.WorkspaceItem;
import com.acme.schedulemanager.domain.repo.BlockDocumentRepository;
import com.acme.schedulemanager.domain.repo.FileAssetRepository;
import com.acme.schedulemanager.domain.repo.UserAccountRepository;
import com.acme.schedulemanager.domain.repo.WorkspaceItemRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final UserAccountRepository userRepo;
    private final WorkspaceItemRepository itemRepo;
    private final BlockDocumentRepository blockRepo;
    private final FileAssetRepository fileRepo;

    public AdminController(UserAccountRepository userRepo, WorkspaceItemRepository itemRepo, BlockDocumentRepository blockRepo, FileAssetRepository fileRepo) {
        this.userRepo = userRepo;
        this.itemRepo = itemRepo;
        this.blockRepo = blockRepo;
        this.fileRepo = fileRepo;
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
}
