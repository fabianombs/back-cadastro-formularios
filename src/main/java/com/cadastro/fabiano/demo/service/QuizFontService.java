package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.entity.QuizFont;
import com.cadastro.fabiano.demo.repository.QuizFontRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class QuizFontService {

    static final long MAX_BYTES = 2L * 1024 * 1024;

    private final QuizFontRepository repository;

    public QuizFontService(QuizFontRepository repository) {
        this.repository = repository;
    }

    /** Guarda a fonte e devolve o caminho publico dela, relativo a API. */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new RuntimeException("Envie um arquivo de fonte.");
        if (file.getSize() > MAX_BYTES) throw new RuntimeException("A fonte deve ter no máximo 2 MB.");
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Não foi possível ler o arquivo da fonte.");
        }
        String mime = detectMime(data);
        if (mime == null) throw new RuntimeException("Formato inválido. Use WOFF2, WOFF, OTF ou TTF.");

        QuizFont font = repository.save(QuizFont.builder()
                .id(UUID.randomUUID().toString())
                .fileName(safeName(file.getOriginalFilename()))
                .mimeType(mime)
                .data(data)
                .createdAt(LocalDateTime.now())
                .build());
        return "/quizzes/fonts/" + font.getId() + "/" + font.getFileName();
    }

    public Optional<QuizFont> find(String id) {
        return repository.findById(id);
    }

    // Pela assinatura do arquivo: extensao e Content-Type vem do navegador e podem mentir
    static String detectMime(byte[] d) {
        if (d.length < 4) return null;
        int sig = ((d[0] & 0xff) << 24) | ((d[1] & 0xff) << 16) | ((d[2] & 0xff) << 8) | (d[3] & 0xff);
        return switch (sig) {
            case 0x774F4632 -> "font/woff2"; // wOF2
            case 0x774F4646 -> "font/woff";  // wOFF
            case 0x4F54544F -> "font/otf";   // OTTO
            case 0x00010000 -> "font/ttf";
            default -> null;
        };
    }

    static String safeName(String original) {
        String name = original == null ? "" : original.replaceAll("[^A-Za-z0-9._-]+", "_");
        if (name.isBlank()) name = "fonte";
        return name.length() > 100 ? name.substring(name.length() - 100) : name;
    }
}
