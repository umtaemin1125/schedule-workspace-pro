package com.acme.schedulemanager.migration;

import com.acme.schedulemanager.domain.entity.BlockDocument;
import com.acme.schedulemanager.domain.entity.DayNote;
import com.acme.schedulemanager.domain.entity.FileAsset;
import com.acme.schedulemanager.domain.entity.WorkspaceItem;
import com.acme.schedulemanager.domain.repo.BlockDocumentRepository;
import com.acme.schedulemanager.domain.repo.DayNoteRepository;
import com.acme.schedulemanager.domain.repo.FileAssetRepository;
import com.acme.schedulemanager.domain.repo.WorkspaceItemRepository;
import com.acme.schedulemanager.files.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MigrationService {
    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern KOR_DATE = Pattern.compile("(\\d{4})년\\s*(\\d{1,2})월\\s*(\\d{1,2})일");
    private static final Pattern TRAILING_ID = Pattern.compile("\\s+[0-9a-f]{32}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\(([^)]+)\\)");
    private static final Set<String> ASSET_EXT = Set.of("png", "jpg", "jpeg", "webp", "gif", "pdf", "txt", "csv", "doc", "docx", "xls", "xlsx", "ppt", "pptx");

    private final WorkspaceItemRepository itemRepo;
    private final BlockDocumentRepository blockRepo;
    private final DayNoteRepository dayNoteRepo;
    private final FileAssetRepository fileRepo;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;

    public MigrationService(
            WorkspaceItemRepository itemRepo,
            BlockDocumentRepository blockRepo,
            DayNoteRepository dayNoteRepo,
            FileAssetRepository fileRepo,
            StorageService storageService,
            ObjectMapper objectMapper
    ) {
        this.itemRepo = itemRepo;
        this.blockRepo = blockRepo;
        this.dayNoteRepo = dayNoteRepo;
        this.fileRepo = fileRepo;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MigrationReport importZip(UUID userId, MultipartFile zipFile) {
        List<String> detected = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        int persistedItems = 0;
        int persistedFiles = 0;
        Map<String, UUID> itemPathMap = new HashMap<>();
        Map<UUID, Map<String, String>> itemAssetRewrites = new HashMap<>();

        try {
            byte[] topBytes = zipFile.getBytes();
            String sourceName = safeName(zipFile.getOriginalFilename(), "upload.zip");
            List<ArchiveEntryData> entries = extractEntries(sourceName, topBytes, failures);

            for (ArchiveEntryData entry : entries) {
                String lower = entry.path().toLowerCase();
                if (lower.endsWith(".csv")) {
                    if (lower.endsWith("_all.csv")) {
                        detected.add("csv-skip-all:" + entry.path());
                        continue;
                    }
                    detected.add("csv:" + entry.path());
                    persistedItems += parseCsv(userId, entry.bytes(), entry.path(), failures);
                }
            }

            List<ArchiveEntryData> docs = entries.stream()
                    .filter(e -> {
                        String lower = e.path().toLowerCase();
                        return lower.endsWith(".md") || lower.endsWith(".html") || lower.endsWith(".htm");
                    })
                    .sorted((a, b) -> Integer.compare(depth(a.path()), depth(b.path())))
                    .toList();

            for (ArchiveEntryData entry : docs) {
                String lower = entry.path().toLowerCase();
                UUID parentId = findParentIdForDocument(entry.path(), itemPathMap);
                int levelFromDate = levelFromDate(entry.path());
                boolean mergeToParent = parentId != null && levelFromDate >= 2;
                if (lower.endsWith(".md")) {
                    detected.add("markdown:" + entry.path());
                    String markdown = new String(entry.bytes(), StandardCharsets.UTF_8);
                    if (mergeToParent) {
                        String html = markdownToHtml(markdown);
                        appendToParentBlock(parentId, fileName(entry.path()), html, failures);
                    } else {
                        ParseResult result = parseMarkdown(userId, markdown, entry.path(), parentId, failures);
                        if (result.itemId() != null) {
                            if (result.created()) persistedItems++;
                            registerItemPath(itemPathMap, entry.path(), result.itemId());
                        }
                    }
                } else {
                    detected.add("html:" + entry.path());
                    String html = new String(entry.bytes(), StandardCharsets.UTF_8);
                    if (mergeToParent) {
                        String safeHtml = Jsoup.clean(Jsoup.parse(html).body().html(), Safelist.relaxed().addTags("hr"));
                        appendToParentBlock(parentId, fileName(entry.path()), safeHtml, failures);
                    } else {
                        ParseResult result = parseHtml(userId, html, entry.path(), parentId, failures);
                        if (result.itemId() != null) {
                            if (result.created()) persistedItems++;
                            registerItemPath(itemPathMap, entry.path(), result.itemId());
                        }
                    }
                }
            }

            for (ArchiveEntryData entry : entries) {
                String ext = extension(entry.path());
                if (!ASSET_EXT.contains(ext)) continue;
                UUID itemId = findBestItemMatch(entry.path(), itemPathMap);
                if (itemId == null) continue;

                try {
                    String mime = toMime(ext);
                    String originalName = fileName(entry.path());
                    String storedName = storageService.store(originalName, mime, entry.bytes());
                    FileAsset asset = new FileAsset();
                    asset.setUserId(userId);
                    asset.setItemId(itemId);
                    asset.setOriginalName(originalName);
                    asset.setStoredName(storedName);
                    asset.setMimeType(mime);
                    asset.setSizeBytes(entry.bytes().length);
                    fileRepo.save(asset);
                    registerAssetRewrite(itemAssetRewrites, itemId, entry.path(), originalName, "/files/" + storedName);
                    persistedFiles++;
                } catch (Exception e) {
                    failures.add("파일 저장 실패(" + entry.path() + "): " + e.getMessage());
                }
            }

            for (var entry : itemAssetRewrites.entrySet()) {
                rewriteBlockImageUrls(entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            failures.add("ZIP 읽기 실패: " + e.getMessage());
        }

        return new MigrationReport(
                detected,
                persistedItems,
                persistedFiles,
                failures,
                List.of(
                        "날짜/상태 컬럼명이 다른 경우 수동 매핑 필요",
                        "일부 비표준 체크리스트 문법은 일반 문단으로 변환될 수 있음",
                        "중첩 ZIP 구조는 자동 탐지되지만 암호화 ZIP은 지원하지 않음"
                )
        );
    }

    private List<ArchiveEntryData> extractEntries(String sourceName, byte[] zipBytes, List<String> failures) {
        List<ArchiveEntryData> out = new ArrayList<>();
        try (ZipArchiveInputStream zip = new ZipArchiveInputStream(
                new ByteArrayInputStream(zipBytes),
                StandardCharsets.UTF_8.name(),
                true,
                true,
                true
        )) {
            ZipArchiveEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                byte[] bytes = zip.readAllBytes();
                String fullPath = sourceName + "/" + entry.getName().replace('\\', '/');
                if (fullPath.toLowerCase().endsWith(".zip")) {
                    out.addAll(extractEntries(fullPath, bytes, failures));
                } else {
                    out.add(new ArchiveEntryData(fullPath, bytes));
                }
            }
        } catch (Exception e) {
            failures.add("ZIP 펼치기 실패(" + sourceName + "): " + e.getMessage());
        }
        return out;
    }

    private int parseCsv(UUID userId, byte[] bytes, String sourcePath, List<String> failures) {
        int count = 0;
        Map<LocalDate, StringBuilder> issueByDate = new HashMap<>();
        Map<LocalDate, StringBuilder> memoByDate = new HashMap<>();
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true)
                     .build()
                     .parse(reader)) {

            var headers = parser.getHeaderMap().keySet();
            String dateKey = findHeader(headers, List.of("날짜", "date", "캘린더"));
            String workKey = findHeader(headers, List.of("오늘의 업무", "업무", "title", "task"));
            String issueKey = findHeader(headers, List.of("이슈", "issue"));
            String memoKey = findHeader(headers, List.of("메모", "memo", "note"));

            int index = 0;
            for (CSVRecord record : parser) {
                index++;
                try {
                    LocalDate dueDate = parseDateFlexible(read(record, dateKey));
                    String title = dueDate == null
                            ? firstNonBlank(read(record, "오늘의 업무 제목"), read(record, "제목"), "이관 항목 " + index)
                            : dueDate.toString();
                    title = title.lines().findFirst().orElse(title).trim();
                    if (title.length() > 120) title = title.substring(0, 120);
                    WorkspaceItem item = new WorkspaceItem();
                    item.setUserId(userId);
                    item.setTitle(normalizeTitle(title));
                    item.setStatus("todo");
                    item.setTemplateType("worklog");
                    if (dueDate != null) item.setDueDate(dueDate);
                    itemRepo.save(item);

                    String work = read(record, workKey);
                    String issue = read(record, issueKey);
                    String memo = read(record, memoKey);
                    String html = csvRowToHtml(work, issue, memo);
                    if (!html.isBlank()) saveHtmlBlock(item.getId(), html, issue, memo);
                    mergeDayText(issueByDate, dueDate, issue);
                    mergeDayText(memoByDate, dueDate, memo);
                    count++;
                } catch (Exception e) {
                    failures.add("CSV 레코드 파싱 실패(" + sourcePath + ", " + (index + 1) + "행): " + e.getMessage());
                }
            }
        } catch (Exception e) {
            failures.add("CSV 파싱 실패(" + sourcePath + "): " + e.getMessage());
        }
        upsertDayNotes(userId, issueByDate, memoByDate);
        return count;
    }

    private ParseResult parseMarkdown(UUID userId, String markdown, String filePath, UUID parentId, List<String> failures) {
        try {
            String extractedTitle = extractTitleFromMarkdown(markdown, filePath);
            LocalDate dueDate = parseDateFlexible(extractedTitle + " " + filePath);
            if (parentId == null && dueDate != null) {
                WorkspaceItem existing = findAnchorByDueDate(userId, dueDate);
                if (existing != null) {
                    appendToParentBlock(existing.getId(), fileName(filePath), markdownToHtml(markdown), failures);
                    return new ParseResult(existing.getId(), false);
                }
            }

            WorkspaceItem item = new WorkspaceItem();
            item.setUserId(userId);
            item.setTitle(extractedTitle);
            item.setStatus("todo");
            item.setTemplateType(inferTemplateType(markdown));
            item.setParentId(parentId);
            if (dueDate == null && parentId != null) {
                dueDate = itemRepo.findById(parentId).map(WorkspaceItem::getDueDate).orElse(null);
            }
            if (dueDate != null) item.setDueDate(dueDate);
            itemRepo.save(item);

            String html = markdownToHtml(markdown);
            if (!html.isBlank()) saveHtmlBlock(item.getId(), html);
            return new ParseResult(item.getId(), true);
        } catch (Exception e) {
            failures.add("Markdown 파싱 실패(" + filePath + "): " + e.getMessage());
            return new ParseResult(null, false);
        }
    }

    private ParseResult parseHtml(UUID userId, String html, String filePath, UUID parentId, List<String> failures) {
        try {
            LocalDate dueDate = parseDateFlexible(filePath);
            if (parentId == null && dueDate != null) {
                WorkspaceItem existing = findAnchorByDueDate(userId, dueDate);
                if (existing != null) {
                    String safeHtml = Jsoup.clean(Jsoup.parse(html).body().html(), Safelist.relaxed().addTags("hr"));
                    appendToParentBlock(existing.getId(), fileName(filePath), safeHtml, failures);
                    return new ParseResult(existing.getId(), false);
                }
            }

            WorkspaceItem item = new WorkspaceItem();
            item.setUserId(userId);
            item.setTitle(normalizeTitle(stripExtension(fileName(filePath))));
            item.setStatus("todo");
            item.setTemplateType("free");
            item.setParentId(parentId);
            dueDate = parseDateFlexible(item.getTitle() + " " + filePath);
            if (dueDate == null && parentId != null) {
                dueDate = itemRepo.findById(parentId).map(WorkspaceItem::getDueDate).orElse(null);
            }
            if (dueDate != null) item.setDueDate(dueDate);
            itemRepo.save(item);

            String safeHtml = Jsoup.clean(Jsoup.parse(html).body().html(), Safelist.relaxed().addTags("hr"));
            saveHtmlBlock(item.getId(), safeHtml);
            return new ParseResult(item.getId(), true);
        } catch (Exception e) {
            failures.add("HTML 파싱 실패(" + filePath + "): " + e.getMessage());
            return new ParseResult(null, false);
        }
    }

    private void saveHtmlBlock(UUID itemId, String html) throws Exception {
        BlockDocument block = new BlockDocument();
        block.setItemId(itemId);
        block.setSortOrder(0);
        block.setType("paragraph");
        block.setContent(objectMapper.writeValueAsString(Map.of("html", html)));
        blockRepo.save(block);
    }

    private void saveHtmlBlock(UUID itemId, String html, String issue, String memo) throws Exception {
        BlockDocument block = new BlockDocument();
        block.setItemId(itemId);
        block.setSortOrder(0);
        block.setType("paragraph");
        block.setContent(objectMapper.writeValueAsString(Map.of(
                "html", html,
                "issue", firstNonBlank(issue, ""),
                "memo", firstNonBlank(memo, "")
        )));
        blockRepo.save(block);
    }

    private void appendToParentBlock(UUID parentId, String sourceName, String html, List<String> failures) {
        if (parentId == null || html == null || html.isBlank()) return;
        try {
            String sectionTitle = normalizeTitle(stripExtension(sourceName));
            String section = "<hr /><h3>" + escapeHtml(sectionTitle) + "</h3>" + html;
            BlockDocument block = blockRepo.findFirstByItemIdOrderBySortOrderAsc(parentId).orElse(null);
            if (block == null) {
                saveHtmlBlock(parentId, section);
                return;
            }
            Map<String, Object> payload = objectMapper.readValue(block.getContent(), Map.class);
            String oldHtml = String.valueOf(payload.getOrDefault("html", ""));
            payload.put("html", oldHtml + section);
            block.setContent(objectMapper.writeValueAsString(payload));
            blockRepo.save(block);
        } catch (Exception e) {
            failures.add("상위 본문 병합 실패(" + sourceName + "): " + e.getMessage());
        }
    }

    private void registerItemPath(Map<String, UUID> map, String filePath, UUID itemId) {
        String normalized = filePath.replace('\\', '/');
        String dir = directoryPath(normalized);
        String stem = stripExtension(fileName(normalized));
        String pageFolder = (dir + "/" + stem).replaceAll("/+", "/");
        String normalizedFolder = (dir + "/" + normalizeTitle(stem)).replaceAll("/+", "/");
        map.put(pageFolder, itemId);
        map.put(normalizedFolder, itemId);
    }

    private UUID findParentIdForDocument(String filePath, Map<String, UUID> itemPathMap) {
        String dir = directoryPath(filePath.replace('\\', '/'));
        while (dir != null && !dir.isBlank()) {
            UUID found = itemPathMap.get(dir);
            if (found != null) return found;
            dir = directoryPath(dir);
        }
        return null;
    }

    private UUID findBestItemMatch(String filePath, Map<String, UUID> itemPathMap) {
        String normalized = filePath.replace('\\', '/');
        String best = null;
        for (String key : itemPathMap.keySet()) {
            if (normalized.startsWith(key + "/")) {
                if (best == null || key.length() > best.length()) {
                    best = key;
                }
            }
        }
        return best == null ? null : itemPathMap.get(best);
    }

    private String markdownToHtml(String markdown) {
        StringBuilder html = new StringBuilder();
        boolean inCode = false;
        boolean inList = false;
        for (String rawLine : markdown.split("\\R")) {
            String raw = rawLine == null ? "" : rawLine;
            String line = raw.trim();
            if (line.startsWith("```")) {
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                if (!inCode) {
                    html.append("<pre><code>");
                } else {
                    html.append("</code></pre>");
                }
                inCode = !inCode;
                continue;
            }
            if (inCode) {
                html.append(escapeHtml(raw)).append("\n");
                continue;
            }
            if (line.isBlank()) {
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                continue;
            }
            if ("---".equals(line) || "----------".equals(line)) {
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                html.append("<hr />");
                continue;
            }
            if (line.startsWith("# ")) {
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                html.append("<h1>").append(applyInlineCode(line.substring(2).trim())).append("</h1>");
                continue;
            }
            if (line.startsWith("## ")) {
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                html.append("<h2>").append(applyInlineCode(line.substring(3).trim())).append("</h2>");
                continue;
            }
            if (line.startsWith("### ")) {
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                html.append("<h3>").append(applyInlineCode(line.substring(4).trim())).append("</h3>");
                continue;
            }
            if (line.startsWith("- [ ] ") || line.startsWith("- [x] ")) {
                if (!inList) {
                    html.append("<ul>");
                    inList = true;
                }
                boolean checked = line.startsWith("- [x] ");
                String body = line.substring(6).trim();
                html.append("<li>").append(checked ? "☑ " : "☐ ").append(applyInlineCode(body)).append("</li>");
                continue;
            }
            if (line.startsWith("- ") || line.startsWith("▪️") || line.startsWith("🔸")) {
                if (!inList) {
                    html.append("<ul>");
                    inList = true;
                }
                String body = line.startsWith("- ") ? line.substring(2).trim() : line.substring(2).trim();
                html.append("<li>").append(applyInlineCode(body)).append("</li>");
                continue;
            }
            Matcher image = MARKDOWN_IMAGE.matcher(line);
            if (image.matches()) {
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                String src = escapeHtml(image.group(1).trim());
                html.append("<p><img src=\"").append(src).append("\" alt=\"image\" /></p>");
                continue;
            }
            if (inList) {
                html.append("</ul>");
                inList = false;
            }
            html.append("<p>").append(applyInlineCode(line)).append("</p>");
        }
        if (inList) html.append("</ul>");
        if (inCode) html.append("</code></pre>");
        return html.toString();
    }

    private String csvRowToHtml(String work, String issue, String memo) {
        StringBuilder html = new StringBuilder();
        appendSection(html, "요청내용", work);
        appendSection(html, "이슈", issue);
        appendSection(html, "메모", memo);
        return html.toString();
    }

    private void appendSection(StringBuilder html, String title, String value) {
        if (value == null || value.isBlank()) return;
        html.append("<h3>").append(title).append("</h3>");
        for (String line : value.split("\\R")) {
            if (line.isBlank()) continue;
            html.append("<p>").append(applyInlineCode(line.trim())).append("</p>");
        }
    }

    private String inferTemplateType(String markdown) {
        String lower = markdown.toLowerCase();
        if (lower.contains("요청자") || lower.contains("요청내용") || lower.contains("[내선]")) return "worklog";
        if (lower.contains("회의") || lower.contains("회의록")) return "meeting";
        return "free";
    }

    private String extractTitleFromMarkdown(String markdown, String filePath) {
        for (String line : markdown.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                return normalizeTitle(trimmed.substring(2).trim());
            }
        }
        return normalizeTitle(stripExtension(fileName(filePath)));
    }

    private String normalizeTitle(String raw) {
        if (raw == null || raw.isBlank()) return "이관 항목";
        String trimmed = raw.trim();
        return TRAILING_ID.matcher(trimmed).replaceAll("").trim();
    }

    private LocalDate parseDateFlexible(String source) {
        if (source == null || source.isBlank()) return null;
        Matcher iso = ISO_DATE.matcher(source);
        if (iso.find()) {
            return LocalDate.parse(iso.group(1));
        }
        Matcher kor = KOR_DATE.matcher(source);
        if (kor.find()) {
            int y = Integer.parseInt(kor.group(1));
            int m = Integer.parseInt(kor.group(2));
            int d = Integer.parseInt(kor.group(3));
            try {
                return LocalDate.of(y, m, d);
            } catch (DateTimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private String findHeader(Set<String> headers, List<String> candidates) {
        for (String c : candidates) {
            for (String h : headers) {
                if (h == null) continue;
                if (h.equalsIgnoreCase(c) || h.contains(c)) return h;
            }
        }
        return null;
    }

    private String read(CSVRecord record, String key) {
        if (key == null || !record.isMapped(key)) return "";
        return record.get(key);
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    private WorkspaceItem findAnchorByDueDate(UUID userId, LocalDate dueDate) {
        if (dueDate == null) return null;
        return itemRepo.findByUserIdAndDueDateOrderByUpdatedAtDesc(userId, dueDate).stream().findFirst().orElse(null);
    }

    private String applyInlineCode(String raw) {
        String escaped = escapeHtml(raw);
        escaped = escaped.replaceAll("₩([^₩]{1,200})₩", "<code>$1</code>");
        return escaped.replaceAll("`([^`]{1,300})`", "<code>$1</code>");
    }

    private String escapeHtml(String raw) {
        return raw
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String extension(String path) {
        String name = fileName(path).toLowerCase();
        int idx = name.lastIndexOf('.');
        return idx < 0 ? "" : name.substring(idx + 1);
    }

    private String toMime(String ext) {
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            case "txt" -> "text/plain";
            case "csv" -> "text/csv";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            default -> "image/png";
        };
    }

    private String fileName(String path) {
        int idx = path.lastIndexOf('/');
        return idx < 0 ? path : path.substring(idx + 1);
    }

    private String directoryPath(String path) {
        int idx = path.lastIndexOf('/');
        return idx < 0 ? "" : path.substring(0, idx);
    }

    private String stripExtension(String name) {
        int idx = name.lastIndexOf('.');
        return idx < 0 ? name : name.substring(0, idx);
    }

    private String safeName(String original, String fallback) {
        if (original == null || original.isBlank()) return fallback;
        return original.replace('\\', '/');
    }

    private int depth(String path) {
        if (path == null || path.isBlank()) return 0;
        int depth = 0;
        for (char c : path.toCharArray()) {
            if (c == '/') depth++;
        }
        return depth;
    }

    private int levelFromDate(String path) {
        if (path == null || path.isBlank()) return 0;
        String normalized = path.replace('\\', '/');
        String[] rawSegments = normalized.split("/");
        List<String> segments = new ArrayList<>();
        for (String segment : rawSegments) {
            if (segment == null || segment.isBlank()) continue;
            if (segment.toLowerCase().endsWith(".zip")) continue;
            segments.add(segment);
        }
        if (segments.isEmpty()) return 0;

        int dateIndex = -1;
        for (int i = 0; i < segments.size(); i++) {
            if (parseDateFlexible(stripExtension(segments.get(i))) != null) {
                dateIndex = i;
                break;
            }
        }
        if (dateIndex < 0) return 0;
        return Math.max(0, segments.size() - 1 - dateIndex);
    }

    private void registerAssetRewrite(
            Map<UUID, Map<String, String>> itemAssetRewrites,
            UUID itemId,
            String fullPath,
            String originalName,
            String fileUrl
    ) {
        Map<String, String> map = itemAssetRewrites.computeIfAbsent(itemId, ignored -> new HashMap<>());
        String normalized = fullPath.replace('\\', '/');
        map.put(originalName, fileUrl);
        map.put("./" + originalName, fileUrl);
        map.put(normalized, fileUrl);
        map.put(fileName(normalized), fileUrl);
    }

    private void rewriteBlockImageUrls(UUID itemId, Map<String, String> rewrites) {
        if (rewrites.isEmpty()) return;
        List<BlockDocument> blocks = blockRepo.findByItemIdOrderBySortOrderAsc(itemId);
        for (BlockDocument block : blocks) {
            try {
                Map<String, Object> payload = objectMapper.readValue(block.getContent(), Map.class);
                Object htmlObj = payload.get("html");
                if (!(htmlObj instanceof String html) || html.isBlank()) continue;

                String updated = replaceAssetUrls(html, rewrites);
                if (!updated.equals(html)) {
                    payload.put("html", updated);
                    block.setContent(objectMapper.writeValueAsString(payload));
                    blockRepo.save(block);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private String replaceAssetUrls(String html, Map<String, String> rewrites) {
        Document doc = Jsoup.parseBodyFragment(html);
        for (Element image : doc.select("img[src]")) {
            String src = image.attr("src");
            String replaced = findRewrite(src, rewrites);
            if (replaced != null) image.attr("src", replaced);
        }
        for (Element link : doc.select("a[href]")) {
            String href = link.attr("href");
            String replaced = findRewrite(href, rewrites);
            if (replaced != null) link.attr("href", replaced);
        }
        return doc.body().html();
    }

    private String findRewrite(String value, Map<String, String> rewrites) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.replace('\\', '/');
        if (rewrites.containsKey(normalized)) return rewrites.get(normalized);
        String name = fileName(normalized);
        if (rewrites.containsKey(name)) return rewrites.get(name);
        if (rewrites.containsKey("./" + name)) return rewrites.get("./" + name);
        return null;
    }

    private void mergeDayText(Map<LocalDate, StringBuilder> target, LocalDate dueDate, String value) {
        if (dueDate == null || value == null || value.isBlank()) return;
        StringBuilder sb = target.computeIfAbsent(dueDate, ignored -> new StringBuilder());
        String normalized = value.trim();
        if (normalized.isBlank()) return;
        if (!sb.isEmpty() && !sb.toString().contains(normalized)) sb.append("\n");
        if (!sb.toString().contains(normalized)) sb.append(normalized);
    }

    private void upsertDayNotes(UUID userId, Map<LocalDate, StringBuilder> issueByDate, Map<LocalDate, StringBuilder> memoByDate) {
        Set<LocalDate> keys = new java.util.HashSet<>();
        keys.addAll(issueByDate.keySet());
        keys.addAll(memoByDate.keySet());
        for (LocalDate day : keys) {
            DayNote note = dayNoteRepo.findByUserIdAndDueDate(userId, day).orElseGet(() -> {
                DayNote n = new DayNote();
                n.setUserId(userId);
                n.setDueDate(day);
                return n;
            });
            String issue = issueByDate.getOrDefault(day, new StringBuilder()).toString().trim();
            String memo = memoByDate.getOrDefault(day, new StringBuilder()).toString().trim();
            if (!issue.isBlank()) note.setIssue(issue);
            if (!memo.isBlank()) note.setMemo(memo);
            dayNoteRepo.save(note);
        }
    }

    private record ArchiveEntryData(String path, byte[] bytes) {}
    private record ParseResult(UUID itemId, boolean created) {}

    public record MigrationReport(
            List<String> detectedPatterns,
            int persistedItems,
            int persistedFiles,
            List<String> failures,
            List<String> manualFixHints
    ) {}
}
