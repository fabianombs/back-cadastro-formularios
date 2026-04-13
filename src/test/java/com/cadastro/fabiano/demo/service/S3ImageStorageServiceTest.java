package com.cadastro.fabiano.demo.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3ImageStorageServiceTest {

    @Mock
    private S3Client s3Client;

    private S3ImageStorageService service;

    private static final String BUCKET   = "test-bucket";
    private static final String BASE_URL = "https://test-bucket.s3.us-east-1.amazonaws.com";

    @BeforeEach
    void setUp() {
        service = new S3ImageStorageService(s3Client, BUCKET, BASE_URL);
    }

    @Test
    @DisplayName("store: faz upload de JPEG novo e retorna URL do S3")
    void store_newFile_uploadsAndReturnsUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "fake-jpeg".getBytes());

        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String url = service.store(file);

        assertThat(url).startsWith(BASE_URL + "/");
        assertThat(url).endsWith(".jpg");
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("store: deduplicação — arquivo já existente no S3 não faz putObject")
    void store_duplicateFile_skipsUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "fake-jpeg".getBytes());

        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        String url = service.store(file);

        assertThat(url).startsWith(BASE_URL + "/");
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("store: PNG é aceito e retorna URL com extensão .png")
    void store_png_returnsCorrectExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "image.png", "image/png", "fake-png".getBytes());

        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String url = service.store(file);

        assertThat(url).endsWith(".png");
    }

    @Test
    @DisplayName("store: tipo não permitido lança exceção")
    void store_invalidType_throwsException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.gif", "image/gif", "data".getBytes());

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("não permitido");
    }

    @Test
    @DisplayName("store: arquivo maior que 5MB lança exceção")
    void store_fileTooLarge_throwsException() {
        byte[] largeData = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.jpg", "image/jpeg", largeData);

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("grande");
    }

    @Test
    @DisplayName("delete: chama deleteObject com a key correta")
    void delete_validUrl_callsDeleteObject() {
        String url = BASE_URL + "/abc123def456.jpg";

        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        service.delete(url);

        verify(s3Client).deleteObject(argThat((DeleteObjectRequest req) ->
                req.bucket().equals(BUCKET) && req.key().equals("abc123def456.jpg")));
    }

    @Test
    @DisplayName("delete: URL nula ou em branco é ignorada silenciosamente")
    void delete_nullOrBlankUrl_ignored() {
        service.delete(null);
        service.delete("  ");
        verifyNoInteractions(s3Client);
    }
}
