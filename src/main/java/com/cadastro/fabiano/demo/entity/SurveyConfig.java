package com.cadastro.fabiano.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "survey_configs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SurveyConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "company_logo_url", columnDefinition = "TEXT")
    private String companyLogoUrl;

    @Column(name = "welcome_title", nullable = false)
    @Builder.Default
    private String welcomeTitle = "Como foi sua experiência?";

    @Column(name = "question_text", nullable = false)
    @Builder.Default
    private String questionText = "Quão satisfeito você está com nosso serviço hoje?";

    // Define se o campo de comentário opcional aparece na tela de avaliação
    @Column(name = "show_comment", nullable = false)
    @Builder.Default
    private Boolean showComment = false;

    @Column(name = "thank_you_msg", nullable = false)
    @Builder.Default
    private String thankYouMsg = "Muito obrigado pela sua avaliação!";

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    // ── Aparência visual da pesquisa pública ──────────────────────────────────

    @Column(name = "background_color")
    private String backgroundColor;

    @Column(name = "background_gradient", columnDefinition = "TEXT")
    private String backgroundGradient;

    @Column(name = "background_image_url", columnDefinition = "TEXT")
    private String backgroundImageUrl;

    @Column(name = "primary_color")
    private String primaryColor;

    @Column(name = "text_color")
    private String textColor;

    // Cor de fundo dos cards de opção de score
    @Column(name = "card_color")
    private String cardColor;

    // Cor do botão principal
    @Column(name = "button_color")
    private String buttonColor;

    // Cor do texto dentro dos botões
    @Column(name = "button_text_color")
    private String buttonTextColor;

    // Border radius da logo (ex: "50%" para circular, "8px" para arredondado)
    @Column(name = "logo_border_radius")
    private String logoBorderRadius;

    // Posição livre do logo (% relativo ao container)
    @Column(name = "logo_pos_x")
    @Builder.Default
    private Double logoPosX = 50.0;

    @Column(name = "logo_pos_y")
    @Builder.Default
    private Double logoPosY = 12.0;

    // Largura do logo em px
    @Column(name = "logo_width")
    @Builder.Default
    private Integer logoWidth = 120;

    // Posição livre do card principal (% relativo ao container)
    @Column(name = "card_pos_x")
    @Builder.Default
    private Double cardPosX = 50.0;

    @Column(name = "card_pos_y")
    @Builder.Default
    private Double cardPosY = 55.0;

    // Visibilidade do logo por tela
    @Column(name = "show_logo_welcome", nullable = false)
    @Builder.Default
    private Boolean showLogoWelcome = true;

    @Column(name = "show_logo_rating", nullable = false)
    @Builder.Default
    private Boolean showLogoRating = true;

    @Column(name = "show_logo_thankyou", nullable = false)
    @Builder.Default
    private Boolean showLogoThankyou = true;

    // Ícones customizados por nível de score (null = usa emoji padrão)
    @Column(name = "score_5_image_url", columnDefinition = "TEXT")
    private String score5ImageUrl;

    @Column(name = "score_4_image_url", columnDefinition = "TEXT")
    private String score4ImageUrl;

    @Column(name = "score_3_image_url", columnDefinition = "TEXT")
    private String score3ImageUrl;

    @Column(name = "score_2_image_url", columnDefinition = "TEXT")
    private String score2ImageUrl;

    @Column(name = "score_1_image_url", columnDefinition = "TEXT")
    private String score1ImageUrl;

    // Labels editáveis por nível de score
    @Column(name = "score_5_label")
    @Builder.Default private String score5Label = "Muito Satisfeito";

    @Column(name = "score_4_label")
    @Builder.Default private String score4Label = "Satisfeito";

    @Column(name = "score_3_label")
    @Builder.Default private String score3Label = "Regular";

    @Column(name = "score_2_label")
    @Builder.Default private String score2Label = "Insatisfeito";

    @Column(name = "score_1_label")
    @Builder.Default private String score1Label = "Muito Insatisfeito";

    // Subtítulos editáveis das telas
    @Column(name = "welcome_subtitle")
    @Builder.Default private String welcomeSubtitle = "Sua opinião é muito importante para nós!";

    @Column(name = "thankyou_subtitle")
    @Builder.Default private String thankyouSubtitle = "Avaliação registrada com sucesso.";

    // Textos dos botões
    @Column(name = "welcome_btn_text")
    @Builder.Default private String welcomeBtnText = "Começar";

    @Column(name = "rating_btn_text")
    @Builder.Default private String ratingBtnText = "Enviar avaliação";

    @OneToMany(mappedBy = "survey", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SurveyResponse> responses = new ArrayList<>();

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
