package com.acme.schedulemanager.migration;

import com.acme.schedulemanager.domain.entity.BlockDocument;
import com.acme.schedulemanager.domain.entity.FileAsset;
import com.acme.schedulemanager.domain.entity.WorkspaceItem;
import com.acme.schedulemanager.domain.repo.BlockDocumentRepository;
import com.acme.schedulemanager.domain.repo.FileAssetRepository;
import com.acme.schedulemanager.domain.repo.WorkspaceItemRepository;
import com.acme.schedulemanager.files.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
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
    private static final Set<String> IMAGE_EXT = Set.of("png", "jpg", "jpeg", "webp", "gif");

    private final WorkspaceItemRepository itemRepo;
    private final BlockDocumentRepository blockRepo;
    private final FileAssetRepository fileRepo;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;

    public MigrationService(
            WorkspaceItemRepository itemRepo,
            BlockDocumentRepository blockRepo,
            FileAssetRepository fileRepo,
            StorageService storageService,
            ObjectMapper objectMapper
    ) {
        this.itemRepo = itemRepo;
        this.blockRepo = blockRepo;
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

        try {
            byte[] topBytes = zipFile.getBytes();
            String sourceName = safeName(zipFile.getOriginalFilename(), "upload.zip");
            List<ArchiveEntryData> entries = extractEntries(sourceName, topBytes, failures);

            for (ArchiveEntryData entry : entries) {
                String lower = entry.path().toLowerCase();
                if (lower.endsWith(".csv")) {
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
                if (lower.endsWith(".md")) {
                    detected.add("markdown:" + entry.path());
                    UUID created = parseMarkdown(userId, new String(entry.bytes(), StandardCharsets.UTF_8), entry.path(), parentId, failures);
                    if (created != null) {
                        persistedItems++;
                        registerItemPath(itemPathMap, entry.path(), created);
                    }
                } else {
                    detected.add("html:" + entry.path());
                    UUID created = parseHtml(userId, new String(entry.bytes(), StandardCharsets.UTF_8), entry.path(), parentId, failures);
                    if (created != null) {
                        persistedItems++;
                        registerItemPath(itemPathMap, entry.path(), created);
                    }
                }
            }

            for (ArchiveEntryData entry : entries) {
                String ext = extension(entry.path());
                if (!IMAGE_EXT.contains(ext)) continue;
                UUID itemId = findBestItemMatch(entry.path(), itemPathMap);
                if (itemId == null) continue;

                try {
                    String mime = toImageMime(ext);
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
                    persistedFiles++;
                } catch (Exception e) {
                    failures.add("이미지 저장 실패(" + entry.path() + "): " + e.getMessage());
                }
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
                    String title = firstNonBlank(read(record, workKey), read(record, issueKey), "이관 항목 " + index);
                    WorkspaceItem item = new WorkspaceItem();
                    item.setUserId(userId);
                    item.setTitle(normalizeTitle(title));
                    item.setStatus("todo");
                    item.setTemplateType("worklog");
                    LocalDate dueDate = parseDateFlexible(read(record, dateKey));
                    if (dueDate != null) item.setDueDate(dueDate);
                    itemRepo.save(item);

                    String html = csvRowToHtml(read(record, workKey), read(record, issueKey), read(record, memoKey));
                    if (!html.isBlank()) saveHtmlBlock(item.getId(), html);
                    count++;
                } catch (Exception e) {
                    failures.add("CSV 레코드 파싱 실패(" + sourcePath + ", " + (index + 1) + "행): " + e.getMessage());
                }
            }
        } catch (Exception e) {
            failures.add("CSV 파싱 실패(" + sourcePath + "): " + e.getMessage());
        }
        return count;
    }

    private UUID parseMarkdown(UUID userId, String markdown, String filePath, UUID parentId, List<String> failures) {
        try {
            WorkspaceItem item = new WorkspaceItem();
            item.setUserId(userId);
            item.setTitle(extractTitleFromMarkdown(markdown, filePath));
            item.setStatus("todo");
            item.setTemplateType(inferTemplateType(markdown));
            item.setParentId(parentId);
            LocalDate dueDate = parseDateFlexible(item.getTitle() + " " + filePath);
            if (dueDate == null && parentId != null) {
                dueDate = itemRepo.findById(parentId).map(WorkspaceItem::getDueDate).orElse(null);
            }
            if (dueDate != null) item.setDueDate(dueDate);
            itemRepo.save(item);

            String html = markdownToHtml(markdown);
            if (!html.isBlank()) saveHtmlBlock(item.getId(), html);
            return item.getId();
        } catch (Exception e) {
            failures.add("Markdown 파싱 실패(" + filePath + "): " + e.getMessage());
            return null;
        }
    }

    private UUID parseHtml(UUID userId, String html, String filePath, UUID parentId, List<String> failures) {
        try {
            WorkspaceItem item = new WorkspaceItem();
            item.setUserId(userId);
            item.setTitle(normalizeTitle(stripExtension(fileName(filePath))));
            item.setStatus("todo");
            item.setTemplateType("free");
            item.setParentId(parentId);
            LocalDate dueDate = parseDateFlexible(item.getTitle() + " " + filePath);
            if (dueDate == null && parentId != null) {
                dueDate = itemRepo.findById(parentId).map(WorkspaceItem::getDueDate).orElse(null);
            }
            if (dueDate != null) item.setDueDate(dueDate);
            itemRepo.save(item);

            String safeHtml = Jsoup.clean(Jsoup.parse(html).body().html(), Safelist.relaxed().addTags("hr"));
            saveHtmlBlock(item.getId(), safeHtml);
            return item.getId();
        } catch (Exception e) {
            failures.add("HTML 파싱 실패(" + filePath + "): " + e.getMessage());
            return null;
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

    private void registerItemPath(Map<String, UUID> map, String filePath, UUID itemId) {
        String normalized = filePath.replace('\\', '/');
        String dir = directoryPath(normalized);
        String stem = stripExtension(fileName(normalized));
        String pageFolder = (dir + "/" + stem).replaceAll("/+", "/");
        map.put(pageFolder, itemId);
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

    private String applyInlineCode(String raw) {
        String escaped = escapeHtml(raw);
        return escaped.replaceAll("₩([^₩]{1,200})₩", "<code>$1</code>");
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

    private String toImageMime(String ext) {
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
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

    private record ArchiveEntryData(String path, byte[] bytes) {}

    public record MigrationReport(
            List<String> detectedPatterns,
            int persistedItems,
            int persistedFiles,
            List<String> failures,
            List<String> manualFixHints
    ) {}
}
