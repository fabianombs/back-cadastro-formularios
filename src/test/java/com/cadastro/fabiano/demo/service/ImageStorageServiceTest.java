package com.cadastro.fabiano.demo.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalImageStorageService service;

    @BeforeEach
    void setUp() {
        service = new LocalImageStorageService(tempDir.toString(), "http://localhost:8080");
    }

    @Test
    @DisplayName("store: armazena JPEG e retorna URL pública")
    void store_jpeg_success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "fake-jpeg-data".getBytes());

        String url = service.store(file);

        assertThat(url).startsWith("http://localhost:8080/files/");
        assertThat(url).endsWith(".jpg");
    }

    @Test
    @DisplayName("store: armazena PNG e retorna URL pública")
    void store_png_success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", "fake-png-data".getBytes());

        String url = service.store(file);

        assertThat(url).endsWith(".png");
    }

    @Test
    @DisplayName("store: deduplicação — mesmo conteúdo não cria novo arquivo")
    void store_deduplication() throws Exception {
        byte[] content = "identical-content".getBytes();
        MockMultipartFile file1 = new MockMultipartFile("file", "a.jpg", "image/jpeg", content);
        MockMultipartFile file2 = new MockMultipartFile("file", "b.jpg", "image/jpeg", content);

        String url1 = service.store(file1);
        String url2 = service.store(file2);

        assertThat(url1).isEqualTo(url2);
        assertThat(Files.list(tempDir).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("store: lança exceção para tipo de arquivo não permitido")
    void store_invalidType_throwsException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.gif", "image/gif", "data".getBytes());

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("não permitido");
    }

    @Test
    @DisplayName("store: lança exceção quando arquivo excede 5MB")
    void store_fileTooLarge_throwsException() {
        byte[] largeData = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.jpg", "image/jpeg", largeData);

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("grande");
    }

    @Test
    @DisplayName("store: arquivo sem extensão usa .jpg como padrão")
    void store_noExtension_usesDefaultJpg() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "noextension", "image/jpeg", "data".getBytes());

        String url = service.store(file);

        assertThat(url).endsWith(".jpg");
    }

    @Test
    @DisplayName("delete: remove arquivo existente")
    void delete_existingFile_removesIt() throws Exception {
        byte[] content = "image-data".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "img.jpg", "image/jpeg", content);
        String url = service.store(file);

        String filename = url.substring(url.lastIndexOf('/') + 1);
        Path filePath = tempDir.resolve(filename);
        assertThat(filePath).exists();

        service.delete(url);

        assertThat(filePath).doesNotExist();
    }

    @Test
    @DisplayName("delete: URL nula é ignorada silenciosamente")
    void delete_nullUrl_ignored() {
        service.delete(null);
        service.delete("  ");
    }
}
