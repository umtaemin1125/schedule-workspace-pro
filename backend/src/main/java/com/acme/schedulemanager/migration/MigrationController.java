package com.acme.schedulemanager.migration;

import com.acme.schedulemanager.security.SecurityUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/migration")
public class MigrationController {
    private final MigrationService migrationService;

    public MigrationController(MigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @PostMapping("/import")
    public MigrationService.MigrationReport importZip(@RequestParam("file") MultipartFile file) {
        return migrationService.importZip(SecurityUtils.principal().userId(), file);
    }
}
