package com.acme.schedulemanager.workspace;

import com.acme.schedulemanager.domain.entity.ItemTag;
import com.acme.schedulemanager.domain.entity.WorkspaceItem;
import com.acme.schedulemanager.domain.entity.DayNote;
import com.acme.schedulemanager.domain.repo.BlockDocumentRepository;
import com.acme.schedulemanager.domain.repo.DayNoteRepository;
import com.acme.schedulemanager.domain.repo.ItemTagRepository;
import com.acme.schedulemanager.domain.repo.WorkspaceItemRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class WorkspaceService {
    private final WorkspaceItemRepository itemRepo;
    private final ItemTagRepository itemTagRepo;
    private final BlockDocumentRepository blockRepo;
    private final DayNoteRepository dayNoteRepo;
    private final ObjectMapper objectMapper;

    public WorkspaceService(
            WorkspaceItemRepository itemRepo,
            ItemTagRepository itemTagRepo,
            BlockDocumentRepository blockRepo,
            DayNoteRepository dayNoteRepo,
            ObjectMapper objectMapper
    ) {
        this.itemRepo = itemRepo;
        this.itemTagRepo = itemTagRepo;
        this.blockRepo = blockRepo;
        this.dayNoteRepo = dayNoteRepo;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WorkspaceDtos.ItemResponse create(UUID userId, WorkspaceDtos.ItemRequest request) {
        WorkspaceItem item = new WorkspaceItem();
        item.setUserId(userId);
        item.setTitle(request.title());
        item.setParentId(request.parentId());
        item.setDueDate(request.dueDate());
        item.setTemplateType(normalizeTemplateType(request.templateType()));
        item.setStatus("todo");
        itemRepo.save(item);
        return toResponse(item);
    }

    public List<WorkspaceDtos.ItemResponse> findAll(UUID userId) {
        return itemRepo.findByUserIdOrderByUpdatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    public List<WorkspaceDtos.ItemResponse> search(UUID userId, String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        List<WorkspaceItem> byTitle = itemRepo.findByUserIdAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(userId, normalized);
        List<UUID> contentIds = blockRepo.searchItemIdsByKeyword(normalized);
        List<WorkspaceItem> byContent = contentIds.isEmpty()
                ? List.of()
                : itemRepo.findAllById(contentIds).stream().filter(v -> v.getUserId().equals(userId)).toList();
        return java.util.stream.Stream.concat(byTitle.stream(), byContent.stream())
                .collect(java.util.stream.Collectors.toMap(WorkspaceItem::getId, v -> v, (a, b) -> a))
                .values().stream()
                .sorted(byDateDescUpdatedDesc())
                .map(this::toResponse)
                .toList();
    }

    public List<WorkspaceDtos.ItemResponse> searchByDate(UUID userId, String keyword, LocalDate dueDate) {
        String normalized = keyword == null ? "" : keyword.trim();
        List<WorkspaceItem> byTitle = itemRepo.findByUserIdAndDueDateAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(userId, dueDate, normalized);
        List<UUID> contentIds = blockRepo.searchItemIdsByKeyword(normalized);
        List<WorkspaceItem> byContent = contentIds.isEmpty()
                ? List.of()
                : itemRepo.findAllById(contentIds).stream()
                .filter(v -> v.getUserId().equals(userId) && dueDate.equals(v.getDueDate()))
                .toList();
        return java.util.stream.Stream.concat(byTitle.stream(), byContent.stream())
                .collect(java.util.stream.Collectors.toMap(WorkspaceItem::getId, v -> v, (a, b) -> a))
                .values().stream()
                .sorted(byDateDescUpdatedDesc())
                .map(this::toResponse)
                .toList();
    }

    public List<WorkspaceDtos.ItemResponse> findByDate(UUID userId, LocalDate dueDate) {
        return itemRepo.findByUserIdAndDueDateOrderByUpdatedAtDesc(userId, dueDate).stream().map(this::toResponse).toList();
    }

    public List<WorkspaceDtos.BoardRowResponse> board(UUID userId, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        return itemRepo.findByUserIdAndDueDateBetweenOrderByDueDateDescUpdatedAtDesc(userId, from, to).stream()
                .map(item -> {
                    String todayWork = "";
                    String issue = "";
                    String memo = "";
                    int checklistTotal = 0;
                    int checklistDone = 0;

                    var firstBlock = blockRepo.findFirstByItemIdOrderBySortOrderAsc(item.getId()).orElse(null);
                    if (firstBlock != null) {
                        try {
                            JsonNode root = objectMapper.readTree(firstBlock.getContent());
                            JsonNode worklog = root.path("worklog");
                            if (worklog.isObject()) {
                                todayWork = toOneLine(worklog.path("requestContent").asText(""));
                                issue = toOneLine(worklog.path("requestChannel").asText(""));
                                memo = toOneLine(worklog.path("processContent1").asText(""));
                                checklistTotal = countToken(todayWork, "[ ]") + countToken(memo, "[ ]");
                                checklistDone = countToken(todayWork, "[x]") + countToken(memo, "[x]");
                            } else {
                                String html = root.path("html").asText("");
                                String issueFromPayload = toOneLine(root.path("issue").asText(""));
                                String memoFromPayload = toOneLine(root.path("memo").asText(""));
                                BoardSummary summary = summarizeHtml(html);
                                todayWork = summary.todayWork();
                                issue = issueFromPayload.isBlank() ? summary.issue() : issueFromPayload;
                                memo = memoFromPayload.isBlank() ? summary.memo() : memoFromPayload;
                                checklistTotal = summary.checklistTotal();
                                checklistDone = summary.checklistDone();
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    DayNote dayNote = item.getDueDate() == null
                            ? null
                            : dayNoteRepo.findByUserIdAndDueDate(userId, item.getDueDate()).orElse(null);
                    if (dayNote != null) {
                        if (!dayNote.getIssue().isBlank()) issue = shortText(dayNote.getIssue());
                        if (!dayNote.getMemo().isBlank()) memo = shortText(dayNote.getMemo());
                    }

                    return new WorkspaceDtos.BoardRowResponse(
                            item.getId(),
                            item.getParentId(),
                            item.getDueDate(),
                            item.getTitle(),
                            item.getStatus(),
                            item.getTemplateType(),
                            todayWork,
                            issue,
                            memo,
                            checklistTotal,
                            checklistDone
                    );
                })
                .toList();
    }

    public WorkspaceDtos.DayNoteResponse getDayNote(UUID userId, LocalDate dueDate) {
        DayNote note = dayNoteRepo.findByUserIdAndDueDate(userId, dueDate).orElse(null);
        if (note == null) return new WorkspaceDtos.DayNoteResponse(dueDate, "", "");
        return new WorkspaceDtos.DayNoteResponse(dueDate, note.getIssue(), note.getMemo());
    }

    @Transactional
    public WorkspaceDtos.DayNoteResponse upsertDayNote(UUID userId, LocalDate dueDate, WorkspaceDtos.DayNoteUpsertRequest request) {
        DayNote note = dayNoteRepo.findByUserIdAndDueDate(userId, dueDate).orElseGet(() -> {
            DayNote n = new DayNote();
            n.setUserId(userId);
            n.setDueDate(dueDate);
            return n;
        });
        note.setIssue(request.issue() == null ? "" : request.issue());
        note.setMemo(request.memo() == null ? "" : request.memo());
        dayNoteRepo.save(note);
        return new WorkspaceDtos.DayNoteResponse(dueDate, note.getIssue(), note.getMemo());
    }

    @Transactional
    public WorkspaceDtos.ItemResponse update(UUID userId, UUID itemId, WorkspaceDtos.ItemUpdateRequest request) {
        WorkspaceItem item = itemRepo.findById(itemId).orElseThrow(() -> new EntityNotFoundException("항목을 찾을 수 없습니다."));
        if (!item.getUserId().equals(userId)) throw new IllegalArgumentException("권한이 없습니다.");
        if (request.title() != null && !request.title().isBlank()) item.setTitle(request.title());
        if (request.status() != null) item.setStatus(request.status());
        if (request.dueDate() != null) item.setDueDate(request.dueDate());
        if (request.templateType() != null) item.setTemplateType(normalizeTemplateType(request.templateType()));
        if (request.parentId() != null || request.parentId() == null) item.setParentId(request.parentId());

        if (request.tagIds() != null) {
            itemTagRepo.deleteByItemId(itemId);
            request.tagIds().forEach(tagId -> {
                ItemTag map = new ItemTag();
                map.setItemId(itemId);
                map.setTagId(tagId);
                itemTagRepo.save(map);
            });
        }

        return toResponse(item);
    }

    @Transactional
    public void delete(UUID userId, UUID itemId) {
        WorkspaceItem item = itemRepo.findById(itemId).orElseThrow(() -> new EntityNotFoundException("항목을 찾을 수 없습니다."));
        if (!item.getUserId().equals(userId)) throw new IllegalArgumentException("권한이 없습니다.");
        itemRepo.delete(item);
    }

    private WorkspaceDtos.ItemResponse toResponse(WorkspaceItem item) {
        return new WorkspaceDtos.ItemResponse(item.getId(), item.getParentId(), item.getTitle(), item.getStatus(), item.getDueDate(), item.getTemplateType(), item.getUpdatedAt());
    }

    private String normalizeTemplateType(String templateType) {
        if (templateType == null || templateType.isBlank()) return "free";
        return switch (templateType.toLowerCase()) {
            case "worklog", "meeting", "free" -> templateType.toLowerCase();
            default -> "free";
        };
    }

    private BoardSummary summarizeHtml(String html) {
        if (html == null || html.isBlank()) {
            return new BoardSummary("", "", "", 0, 0);
        }
        Document doc = Jsoup.parseBodyFragment(html);
        String today = "";
        String issue = "";
        String memo = "";
        int total = 0;
        int done = 0;
        for (Element li : doc.select("li")) {
            String text = li.text();
            if (text.contains("[ ]") || text.contains("[x]") || text.contains("☐") || text.contains("☑")) {
                total++;
                if (text.contains("[x]") || text.contains("☑")) done++;
            }
        }

        String section = null;
        List<String> todayLines = new ArrayList<>();
        List<String> issueLines = new ArrayList<>();
        List<String> memoLines = new ArrayList<>();

        for (Element el : doc.body().children()) {
            String text = el.text().trim();
            if (text.isBlank()) continue;
            if (el.tagName().matches("h1|h2|h3|h4")) {
                if (text.contains("요청내용") || text.contains("오늘의 업무")) {
                    section = "today";
                    continue;
                }
                if (text.contains("이슈")) {
                    section = "issue";
                    continue;
                }
                if (text.contains("메모")) {
                    section = "memo";
                    continue;
                }
                section = null;
                continue;
            }

            if (section == null) continue;
            if ("today".equals(section)) todayLines.add(text);
            if ("issue".equals(section)) issueLines.add(text);
            if ("memo".equals(section)) memoLines.add(text);
        }

        if (!todayLines.isEmpty()) today = shortText(String.join(" / ", todayLines));
        if (!issueLines.isEmpty()) issue = shortText(String.join(" / ", issueLines));
        if (!memoLines.isEmpty()) memo = shortText(String.join(" / ", memoLines));

        if (today.isBlank()) today = shortText(doc.select("p,li").stream().map(Element::text).findFirst().orElse(""));
        if (done > total) done = total;
        return new BoardSummary(today, issue, memo, total, done);
    }

    private int countToken(String source, String token) {
        if (source == null || source.isBlank()) return 0;
        int count = 0;
        int from = 0;
        while (true) {
            int idx = source.indexOf(token, from);
            if (idx < 0) return count;
            count++;
            from = idx + token.length();
        }
    }

    private String toOneLine(String raw) {
        return shortText(raw == null ? "" : raw.replace('\n', ' '));
    }

    private String shortText(String raw) {
        if (raw == null) return "";
        String compact = raw.replaceAll("\\s+", " ").trim();
        return compact.length() > 120 ? compact.substring(0, 120) + "..." : compact;
    }

    private Comparator<WorkspaceItem> byDateDescUpdatedDesc() {
        return Comparator
                .comparing(WorkspaceItem::getDueDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(WorkspaceItem::getUpdatedAt, Comparator.reverseOrder());
    }

    private record BoardSummary(
            String todayWork,
            String issue,
            String memo,
            int checklistTotal,
            int checklistDone
    ) {}
}
