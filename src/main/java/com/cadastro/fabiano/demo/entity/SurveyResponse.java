package com.cadastro.fabiano.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "survey_responses")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SurveyResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survey_id", nullable = false)
    private SurveyConfig survey;

    // Score de 1 (Muito Insatisfeito) a 5 (Muito Satisfeito)
    @Column(nullable = false)
    private Integer score;

    @Column(columnDefinition = "TEXT")
    private String comment;

    // Nome/referência do respondente quando a pesquisa é vinculada a outro template
    @Column(name = "respondent_ref")
    private String respondentRef;

    // Slug do template origem quando a pesquisa é disparada ao fim de outro fluxo
    @Column(name = "source_template_slug")
    private String sourceTemplateSlug;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { this.createdAt = LocalDateTime.now(); }
}
