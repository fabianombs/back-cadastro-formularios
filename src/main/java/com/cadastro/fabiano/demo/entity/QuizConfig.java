package com.cadastro.fabiano.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// @BatchSize de classe vale para quem aponta para ca por @ManyToOne LAZY
// (FormTemplate.quiz e FormTemplate.survey). Numa listagem de templates,
// sem isto e uma consulta por template; com isto, uma para a pagina toda.
@BatchSize(size = 25)
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

    // Primeiro nivel do N+1 aninhado do quiz: sem batch, montar o
    // QuizConfigResponse faz 1 consulta por quiz para trazer as questoes.
    @OneToMany(mappedBy = "quizConfig", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 25)
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

    /** Imagem de fundo específica para celular (opcional; sem ela, usa a de cima) */
    @Column(name = "background_image_mobile_url")
    private String backgroundImageMobileUrl;

    /** Imagem de fundo específica para tablet (opcional; sem ela, usa a de cima) */
    @Column(name = "background_image_tablet_url")
    private String backgroundImageTabletUrl;

    @Column(name = "primary_color")
    private String primaryColor;

    @Column(name = "text_color")
    private String textColor;

    // Cor de fundo dos cards de resposta — independente da cor primária
    @Column(name = "card_color")
    private String cardColor;

    // Cor de fundo dos cards de cadastro/ready — independente dos cards de resposta
    @Column(name = "register_card_color")
    private String registerCardColor;

    // Cor de fundo dos inputs de cadastro
    @Column(name = "input_color")
    private String inputColor;

    // Cor de fundo dos cards do ranking/pódio
    @Column(name = "ranking_card_color")
    private String rankingCardColor;

    // Cor do botão principal — null usa primaryColor como fallback
    @Column(name = "button_color")
    private String buttonColor;

    // Cor do texto dentro dos botões — null usa #fff como fallback
    @Column(name = "button_text_color")
    private String buttonTextColor;

    // Texto editável da tela "Tudo pronto!" — null usa os valores padrão no frontend
    @Column(name = "ready_title", length = 120)
    private String readyTitle;

    @Column(name = "ready_message", length = 255)
    private String readyMessage;

    // Tela de agradecimento final (opcional) — mostrada acima do resultado/pontuação
    // quando habilitada; sem ela, comportamento de sempre (só a tela de resultado).
    @Column(name = "thank_you_enabled", nullable = false)
    @Builder.Default
    private boolean thankYouEnabled = false;

    @Column(name = "thank_you_title", length = 255)
    private String thankYouTitle;

    @Column(name = "thank_you_subtitle", length = 255)
    private String thankYouSubtitle;

    @Column(name = "thank_you_paragraph", columnDefinition = "TEXT")
    private String thankYouParagraph;

    @Column(name = "thank_you_bg_color", length = 20)
    private String thankYouBgColor;

    @Column(name = "thank_you_bg_gradient", length = 255)
    private String thankYouBgGradient;

    @Column(name = "thank_you_bg_image_url", length = 500)
    private String thankYouBgImageUrl;

    @Column(name = "thank_you_bg_image_mobile_url", length = 500)
    private String thankYouBgImageMobileUrl;

    @Column(name = "thank_you_bg_image_tablet_url", length = 500)
    private String thankYouBgImageTabletUrl;

    @Column(name = "thank_you_icon_color", length = 20)
    private String thankYouIconColor;

    @Column(name = "thank_you_title_color", length = 20)
    private String thankYouTitleColor;

    @Column(name = "thank_you_text_color", length = 20)
    private String thankYouTextColor;

    @Column(name = "thank_you_font_family", length = 100)
    private String thankYouFontFamily;

    @Column(name = "thank_you_title_font_size", length = 20)
    private String thankYouTitleFontSize;

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
