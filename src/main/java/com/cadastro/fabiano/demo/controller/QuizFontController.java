package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.service.QuizFontService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/quizzes/fonts")
@Tag(name = "Quiz — Fontes", description = "Fontes enviadas para os quizzes")
public class QuizFontController {

    private final QuizFontService service;

    public QuizFontController(QuizFontService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO')")
    @Operation(summary = "Enviar fonte", description = "WOFF2, WOFF, OTF ou TTF até 2MB")
    public Map<String, String> upload(@RequestPart("file") MultipartFile file) {
        return Map.of("url", service.store(file));
    }

    @GetMapping("/{id}/{fileName}")
    @Operation(summary = "Baixar fonte (público, usado pelo link do quiz)")
    public ResponseEntity<byte[]> get(@PathVariable String id, @PathVariable String fileName) {
        return service.find(id)
                .map(f -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(f.getMimeType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .body(f.getData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
