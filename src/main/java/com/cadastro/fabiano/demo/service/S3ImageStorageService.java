package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.utils.HashUtil;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.Set;

public class S3ImageStorageService implements ImageStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/jpg"
    );

    private static final long MAX_SIZE_BYTES = 5 * 1024 * 1024;

    private final S3Client s3Client;
    private final String   bucketName;
    private final String   s3BaseUrl;

    public S3ImageStorageService(S3Client s3Client, String bucketName, String s3BaseUrl) {
        this.s3Client   = s3Client;
        this.bucketName = bucketName;
        this.s3BaseUrl  = s3BaseUrl;
    }

    @Override
    public String store(MultipartFile file) throws Exception {
        validate(file);

        byte[] fileBytes = file.getBytes();
        String hash      = HashUtil.generateSHA256(fileBytes);
        String extension = getExtension(file);
        String key       = hash + extension;

        if (!objectExists(key)) {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .build(),
                RequestBody.fromBytes(fileBytes)
            );
        }

        return s3BaseUrl + "/" + key;
    }

    @Override
    public void delete(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return;
        try {
            String key = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
            s3Client.deleteObject(
                DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build()
            );
        } catch (Exception ignored) {
            // Silencioso — mesmo contrato do LocalImageStorageService
        }
    }

    private boolean objectExists(String key) {
        try {
            s3Client.headObject(
                HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build()
            );
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
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
