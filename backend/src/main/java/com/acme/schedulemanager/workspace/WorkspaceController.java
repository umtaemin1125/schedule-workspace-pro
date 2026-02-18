package com.acme.schedulemanager.workspace;

import com.acme.schedulemanager.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspace/items")
public class WorkspaceController {
    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @PostMapping
    public WorkspaceDtos.ItemResponse create(@RequestBody @Valid WorkspaceDtos.ItemRequest request) {
        return workspaceService.create(SecurityUtils.principal().userId(), request);
    }

    @GetMapping
    public List<WorkspaceDtos.ItemResponse> list(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "dueDate", required = false) LocalDate dueDate
    ) {
        UUID userId = SecurityUtils.principal().userId();
        if (dueDate != null) {
            return workspaceService.findByDate(userId, dueDate);
        }
        if (q != null && !q.isBlank()) {
            return workspaceService.search(userId, q);
        }
        return workspaceService.findAll(userId);
    }

    @PatchMapping("/{id}")
    public WorkspaceDtos.ItemResponse update(@PathVariable UUID id, @RequestBody WorkspaceDtos.ItemUpdateRequest request) {
        return workspaceService.update(SecurityUtils.principal().userId(), id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        workspaceService.delete(SecurityUtils.principal().userId(), id);
    }

    @GetMapping("/board")
    public List<WorkspaceDtos.BoardRowResponse> board(@RequestParam("month") @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return workspaceService.board(SecurityUtils.principal().userId(), month);
    }

    @GetMapping("/recent")
    public List<WorkspaceDtos.ItemResponse> recent() {
        return workspaceService.findAll(SecurityUtils.principal().userId()).stream().limit(10).toList();
    }

    @GetMapping("/day-note")
    public WorkspaceDtos.DayNoteResponse dayNote(@RequestParam("date") LocalDate date) {
        return workspaceService.getDayNote(SecurityUtils.principal().userId(), date);
    }

    @PutMapping("/day-note")
    public WorkspaceDtos.DayNoteResponse upsertDayNote(
            @RequestParam("date") LocalDate date,
            @RequestBody WorkspaceDtos.DayNoteUpsertRequest request
    ) {
        return workspaceService.upsertDayNote(SecurityUtils.principal().userId(), date, request);
    }
}
