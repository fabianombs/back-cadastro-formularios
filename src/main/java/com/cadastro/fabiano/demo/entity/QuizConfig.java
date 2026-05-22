package com.cadastro.fabiano.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quiz_configs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QuizConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nome do quiz exibido na biblioteca e no ranking
    @Column(nullable = false)
    private String name;

    // Slug público para montar as URLs: /quiz/{slug} e /quiz/{slug}/ranking
    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "time_per_question", nullable = false)
    private int timePerQuestion = 30;

    @Column(name = "points_per_question", nullable = false)
    private int pointsPerQuestion = 1000;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "quizConfig", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    @Builder.Default
    private List<QuizQuestion> questions = new ArrayList<>();

    // ── Aparência visual do quiz público ──────────────────────────────────────
    @Column(name = "background_color")
    private String backgroundColor;

    @Column(name = "background_gradient")
    private String backgroundGradient;

    @Column(name = "background_image_url")
    private String backgroundImageUrl;

    @Column(name = "primary_color")
    private String primaryColor;

    @Column(name = "text_color")
    private String textColor;

    // Cor de fundo dos cards de resposta — independente da cor primária
    @Column(name = "card_color")
    private String cardColor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
