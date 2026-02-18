package com.acme.schedulemanager.files;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

@Service
public class LocalStorageService implements StorageService {
    private final Path baseDir;
    private static final Set<String> ALLOWED_MIME = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");
    private static final Set<String> ALLOWED_EXT = Set.of("png", "jpg", "jpeg", "webp", "gif");

    public LocalStorageService(@Value("${app.files.base-dir:/data/uploads}") String baseDir) throws IOException {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
        Files.createDirectories(this.baseDir);
    }

    @Override
    public String store(MultipartFile file) {
        if (file.isEmpty()) throw new IllegalArgumentException("빈 파일은 업로드할 수 없습니다.");
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        try {
            return store(original, file.getContentType(), file.getBytes());
        } catch (IOException e) {
            throw new IllegalArgumentException("파일 저장 중 오류가 발생했습니다.");
        }
    }

    @Override
    public String store(String originalName, String mimeType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("빈 파일은 업로드할 수 없습니다.");
        if (!ALLOWED_MIME.contains(mimeType)) throw new IllegalArgumentException("허용되지 않은 MIME 타입입니다.");

        String original = originalName == null ? "file" : originalName;
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!ALLOWED_EXT.contains(ext)) throw new IllegalArgumentException("허용되지 않은 확장자입니다.");

        String storedName = UUID.randomUUID() + "." + ext;
        Path target = baseDir.resolve(storedName).normalize();
        if (!target.startsWith(baseDir)) throw new IllegalArgumentException("잘못된 경로입니다.");
        try {
            Files.write(target, bytes);
            return storedName;
        } catch (IOException e) {
            throw new IllegalArgumentException("파일 저장 중 오류가 발생했습니다.");
        }
    }

    @Override
    public Resource load(String storedName) {
        Path target = baseDir.resolve(storedName).normalize();
        if (!target.startsWith(baseDir)) throw new IllegalArgumentException("잘못된 경로입니다.");
        return new FileSystemResource(target);
    }
}
