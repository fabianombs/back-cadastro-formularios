package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.utils.HashUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.util.Set;

@Service
public class ImageStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/jpg"
    );

    private static final long MAX_SIZE_BYTES = 5 * 1024 * 1024;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public String store(MultipartFile file) throws Exception {

        validate(file);

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);

        byte[] fileBytes = file.getBytes();

        // 🔥 HASH
        String hash = HashUtil.generateSHA256(fileBytes);

        // extensão
        String extension = getExtension(file);

        String filename = hash + extension;

        Path targetPath = uploadPath.resolve(filename);

        // 🔥 deduplicação
        if (!Files.exists(targetPath)) {
            Files.write(targetPath, fileBytes);
        }

        return baseUrl + "/files/" + filename;
    }

    private void validate(MultipartFile file) {

        String contentType = file.getContentType();

        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new RuntimeException("Tipo de arquivo não permitido");
        }

        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new RuntimeException("Arquivo muito grande (máx 5MB)");
        }
    }

    private String getExtension(MultipartFile file) {

        String originalFilename = file.getOriginalFilename();

        if (originalFilename != null && originalFilename.contains(".")) {
            return originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }

        return ".jpg";
    }
}