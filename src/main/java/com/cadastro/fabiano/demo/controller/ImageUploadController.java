package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.config.MetricasDeNegocio;
import com.cadastro.fabiano.demo.service.ImageStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/uploads")
@Tag(name = "Upload de Imagens", description = "Upload e remoção de imagens para personalização de templates")
public class ImageUploadController {

    private final ImageStorageService service;
    private final MetricasDeNegocio metricas;

    public ImageUploadController(ImageStorageService service, MetricasDeNegocio metricas) {
        this.service = service;
        this.metricas = metricas;
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Fazer upload de imagem",
            description = "Aceita JPEG ou PNG até 5MB. Retorna a URL pública da imagem. Imagens idênticas (mesmo hash SHA-256) reutilizam o arquivo existente")
    @ApiResponse(responseCode = "200", description = "Upload realizado — retorna { url: '...' }")
    @ApiResponse(responseCode = "400", description = "Tipo de arquivo inválido ou tamanho excedido")
    public ResponseEntity<?> uploadImage(@RequestPart("file") MultipartFile file) {
        long inicio = System.nanoTime();
        try {
            String url = service.store(file);
            // O tamanho vai junto com a duracao: sem ele, uma subida no tempo
            // de upload e ambigua entre armazenamento degradado e arquivo maior.
            metricas.uploadConcluido(System.nanoTime() - inicio, file.getSize());
            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            metricas.uploadFalhou(System.nanoTime() - inicio);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/image")
    @Operation(summary = "Excluir imagem", description = "Remove o arquivo físico do disco a partir da URL pública")
    @ApiResponse(responseCode = "204", description = "Imagem removida")
    public ResponseEntity<Void> deleteImage(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url != null && !url.isBlank()) {
            service.delete(url);
        }
        return ResponseEntity.noContent().build();
    }
}
