package com.cadastro.fabiano.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** Arquivo de fonte enviado para um quiz. Servido pela API para que o CORS do link publico valha para ele. */
@Entity
@Table(name = "quiz_fonts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QuizFont {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "file_name", nullable = false, length = 120)
    private String fileName;

    @Column(name = "mime_type", nullable = false, length = 40)
    private String mimeType;

    @Lob
    @Column(nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] data;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
