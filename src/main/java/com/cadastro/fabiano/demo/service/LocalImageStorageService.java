package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.utils.HashUtil;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.util.Set;

public class LocalImageStorageService implements ImageStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/jpg"
    );

    private static final long MAX_SIZE_BYTES = 5 * 1024 * 1024;

    private final String uploadDir;
    private final String baseUrl;

    public LocalImageStorageService(String uploadDir, String baseUrl) {
        this.uploadDir = uploadDir;
        this.baseUrl   = baseUrl;
    }

    @Override
    public void delete(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return;
        try {
            String filename = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.deleteIfExists(uploadPath.resolve(filename));
        } catch (Exception ignored) {
            // Arquivo já removido ou caminho inválido — sem impacto no fluxo
        }
    }

    @Override
    public String store(MultipartFile file) throws Exception {
        validate(file);

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);

        byte[] fileBytes = file.getBytes();
        String hash      = HashUtil.generateSHA256(fileBytes);
        String extension = getExtension(file);
        String filename  = hash + extension;
        Path targetPath  = uploadPath.resolve(filename);

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
