package com.acme.schedulemanager.exportimport;

import com.acme.schedulemanager.security.SecurityUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/backup")
public class BackupController {
    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @GetMapping(value = "/export", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public void export(HttpServletResponse response) throws IOException {
        response.setHeader("Content-Disposition", "attachment; filename=backup.zip");
        backupService.exportAll(SecurityUtils.principal().userId(), response.getOutputStream());
    }

    @PostMapping("/import")
    public BackupService.BackupImportReport importZip(@RequestParam("file") MultipartFile file) {
        return backupService.importAll(SecurityUtils.principal().userId(), file);
    }
}
