package com.cadastro.fabiano.demo.config;

import com.cadastro.fabiano.demo.service.ImageStorageService;
import com.cadastro.fabiano.demo.service.LocalImageStorageService;
import com.cadastro.fabiano.demo.service.S3ImageStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {

    // ── Dev: armazenamento local ──────────────────────────────────────────────

    @Bean
    @Profile("dev")
    public ImageStorageService localImageStorageService(
            @Value("${app.upload-dir:uploads}") String uploadDir,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        return new LocalImageStorageService(uploadDir, baseUrl);
    }

    // ── Prod / Homolog: AWS S3 ────────────────────────────────────────────────

    @Bean
    @Profile("!dev")
    public S3Client s3Client(
            @Value("${aws.access-key-id}") String accessKeyId,
            @Value("${aws.secret-access-key}") String secretKey,
            @Value("${aws.region}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretKey)))
                .build();
    }

    @Bean
    @Profile("!dev")
    public ImageStorageService s3ImageStorageService(
            S3Client s3Client,
            @Value("${aws.s3.bucket}") String bucket,
            @Value("${aws.s3.base-url}") String s3BaseUrl) {
        return new S3ImageStorageService(s3Client, bucket, s3BaseUrl);
    }
}
