package com.acme.schedulemanager.content;

import com.acme.schedulemanager.security.SecurityUtils;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/content")
public class ContentController {
    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    @GetMapping("/{itemId}/blocks")
    public ContentDtos.BlocksResponse load(@PathVariable UUID itemId) {
        return contentService.load(SecurityUtils.principal().userId(), itemId);
    }

    @PutMapping("/{itemId}/blocks")
    public ContentDtos.BlocksResponse save(@PathVariable UUID itemId, @RequestBody ContentDtos.SaveBlocksRequest request) {
        return contentService.save(SecurityUtils.principal().userId(), itemId, request);
    }
}
