package com.acme.schedulemanager.migration;

import com.acme.schedulemanager.domain.entity.BlockDocument;
import com.acme.schedulemanager.domain.entity.WorkspaceItem;
import com.acme.schedulemanager.domain.repo.BlockDocumentRepository;
import com.acme.schedulemanager.domain.repo.WorkspaceItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MigrationService {
    private static final Pattern CSV_DATE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");

    private final WorkspaceItemRepository itemRepo;
    private final BlockDocumentRepository blockRepo;
    private final ObjectMapper objectMapper;

    public MigrationService(WorkspaceItemRepository itemRepo, BlockDocumentRepository blockRepo, ObjectMapper objectMapper) {
        this.itemRepo = itemRepo;
        this.blockRepo = blockRepo;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MigrationReport importZip(UUID userId, MultipartFile zipFile) {
        List<String> detected = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        int persistedItems = 0;

        try (ZipArchiveInputStream zip = new ZipArchiveInputStream(zipFile.getInputStream(), StandardCharsets.UTF_8.name(), true, true, true)) {
            ZipArchiveEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName().toLowerCase();
                byte[] bytes = zip.readAllBytes();

                if (name.endsWith(".csv")) {
                    detected.add("csv:" + entry.getName());
                    persistedItems += parseCsv(userId, new String(bytes, StandardCharsets.UTF_8), failures);
                } else if (name.endsWith(".md")) {
                    detected.add("markdown:" + entry.getName());
                    persistedItems += parseMarkdown(userId, new String(bytes, StandardCharsets.UTF_8), entry.getName(), failures);
                } else if (name.endsWith(".html") || name.endsWith(".htm")) {
                    detected.add("html:" + entry.getName());
                    persistedItems += parseHtml(userId, new String(bytes, StandardCharsets.UTF_8), entry.getName(), failures);
                }
            }
        } catch (Exception e) {
            failures.add("ZIP 읽기 실패: " + e.getMessage());
        }

        return new MigrationReport(detected, persistedItems, failures, List.of("날짜/상태 컬럼명이 다른 경우 수동 매핑 필요", "체크리스트 문법이 비표준이면 문단으로 변환됨"));
    }

    private int parseCsv(UUID userId, String csv, List<String> failures) {
        int count = 0;
        String[] lines = csv.split("\\R");
        if (lines.length < 2) return 0;
        for (int i = 1; i < lines.length; i++) {
            try {
                String[] cols = lines[i].split(",");
                String title = cols.length > 1 ? cols[1].trim() : "이관 항목 " + i;
                WorkspaceItem item = new WorkspaceItem();
                item.setUserId(userId);
                item.setTitle(title.isBlank() ? "이관 항목 " + i : title);
                item.setStatus(cols.length > 2 ? cols[2].trim().toLowerCase() : "todo");
                if (cols.length > 0) {
                    Matcher m = CSV_DATE.matcher(cols[0]);
                    if (m.find()) {
                        item.setDueDate(LocalDate.parse(m.group(1)));
                    }
                }
                itemRepo.save(item);
                count++;
            } catch (Exception e) {
                failures.add("CSV 행 파싱 실패(" + i + "행): " + e.getMessage());
            }
        }
        return count;
    }

    private int parseMarkdown(UUID userId, String markdown, String fileName, List<String> failures) {
        try {
            WorkspaceItem item = new WorkspaceItem();
            item.setUserId(userId);
            item.setTitle(fileName.replace(".md", ""));
            item.setStatus("todo");
            itemRepo.save(item);

            String[] lines = markdown.split("\\R");
            int order = 0;
            for (String line : lines) {
                if (line.isBlank()) continue;
                BlockDocument b = new BlockDocument();
                b.setItemId(item.getId());
                b.setSortOrder(order++);
                if (line.startsWith("# ")) b.setType("heading1");
                else if (line.startsWith("## ")) b.setType("heading2");
                else if (line.startsWith("### ")) b.setType("heading3");
                else if (line.startsWith("- [ ] ") || line.startsWith("- [x] ")) b.setType("checklist");
                else b.setType("paragraph");
                b.setContent(objectMapper.writeValueAsString(Map.of("text", line)));
                blockRepo.save(b);
            }
            return 1;
        } catch (Exception e) {
            failures.add("Markdown 파싱 실패(" + fileName + "): " + e.getMessage());
            return 0;
        }
    }

    private int parseHtml(UUID userId, String html, String fileName, List<String> failures) {
        try {
            WorkspaceItem item = new WorkspaceItem();
            item.setUserId(userId);
            item.setTitle(fileName.replace(".html", "").replace(".htm", ""));
            item.setStatus("todo");
            itemRepo.save(item);

            var doc = Jsoup.parse(html);
            var nodes = doc.select("h1, h2, h3, p, li");
            int order = 0;
            for (var node : nodes) {
                String text = node.text();
                if (text.isBlank()) continue;
                BlockDocument b = new BlockDocument();
                b.setItemId(item.getId());
                b.setSortOrder(order++);
                b.setType(node.tagName().startsWith("h") ? "heading" + node.tagName().substring(1) : node.tagName().equals("li") ? "list" : "paragraph");
                b.setContent(objectMapper.writeValueAsString(Map.of("text", text)));
                blockRepo.save(b);
            }
            return 1;
        } catch (Exception e) {
            failures.add("HTML 파싱 실패(" + fileName + "): " + e.getMessage());
            return 0;
        }
    }

    public record MigrationReport(List<String> detectedPatterns, int persistedItems, List<String> failures, List<String> manualFixHints) {}
}
