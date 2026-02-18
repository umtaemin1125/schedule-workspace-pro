package com.acme.schedulemanager.admin;

import com.acme.schedulemanager.domain.repo.BlockDocumentRepository;
import com.acme.schedulemanager.domain.repo.FileAssetRepository;
import com.acme.schedulemanager.domain.repo.UserAccountRepository;
import com.acme.schedulemanager.domain.repo.WorkspaceItemRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
                .map(u -> new AdminDtos.UserRow(u.getId(), u.getEmail(), u.getNickname(), u.getRole(), u.getFailedLoginCount()))
                .toList();
    }
}
