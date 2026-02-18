package com.acme.schedulemanager.tags;

import com.acme.schedulemanager.domain.entity.Tag;
import com.acme.schedulemanager.domain.repo.TagRepository;
import com.acme.schedulemanager.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tags")
public class TagController {
    private final TagRepository tagRepository;

    public TagController(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    @PostMapping
    public TagDtos.TagResponse create(@RequestBody @Valid TagDtos.CreateTagRequest request) {
        UUID userId = SecurityUtils.principal().userId();
        if (tagRepository.existsByUserIdAndName(userId, request.name())) {
            throw new IllegalArgumentException("이미 존재하는 태그입니다.");
        }
        Tag tag = new Tag();
        tag.setUserId(userId);
        tag.setName(request.name());
        tagRepository.save(tag);
        return new TagDtos.TagResponse(tag.getId(), tag.getName());
    }

    @GetMapping
    public List<TagDtos.TagResponse> list() {
        return tagRepository.findByUserIdOrderByNameAsc(SecurityUtils.principal().userId()).stream()
                .map(tag -> new TagDtos.TagResponse(tag.getId(), tag.getName())).toList();
    }
}
