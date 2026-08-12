package com.neuralbase.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path uploadDir;

    public FileStorageService(@Value("${neuralbase.storage.upload-dir}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录: " + this.uploadDir, e);
        }
    }

    public String store(MultipartFile file) throws IOException {
        String original = file.getOriginalFilename();
        String safeName;
        if (original == null || original.isBlank()) {
            safeName = "unnamed";
        } else {
            Path p = Path.of(original);
            safeName = p.getFileName() == null ? "unnamed" : p.getFileName().toString();
        }
        Path target = uploadDir.resolve(UUID.randomUUID() + "_" + safeName);
        file.transferTo(target);
        return target.toString();
    }
}
