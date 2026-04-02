package com.cadastro.fabiano.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;
import java.util.List;

@Entity
@Table(name = "form_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @ManyToOne
    @JoinColumn(name = "client_id")
    private Client client;

    @OneToMany(mappedBy = "formTemplate", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FormField> fields;

    // =====================
    // CONFIGURAÇÃO DE AGENDA
    // =====================

    @Column(name = "has_schedule", nullable = false)
    @Builder.Default
    private boolean hasSchedule = false;

    @Column(name = "has_attendance", nullable = false)
    @Builder.Default
    private boolean hasAttendance = false;

    @Column(name = "schedule_start_time")
    private LocalTime scheduleStartTime;

    @Column(name = "schedule_end_time")
    private LocalTime scheduleEndTime;

    @Column(name = "slot_duration_minutes")
    private Integer slotDurationMinutes;

    @Column(name = "max_days_ahead")
    private Integer maxDaysAhead;

    // =====================
    // APARÊNCIA / CUSTOMIZAÇÃO
    // =====================

    /** Cor de fundo sólida (hex, ex: #ffffff) */
    @Column(name = "background_color")
    private String backgroundColor;

    /** Gradiente CSS (ex: linear-gradient(135deg, #667eea 0%, #764ba2 100%)) */
    @Column(name = "background_gradient")
    private String backgroundGradient;

    /** URL da imagem de fundo do formulário */
    @Column(name = "background_image_url", length = 1000)
    private String backgroundImageUrl;

    /** URL da imagem no topo do formulário */
    @Column(name = "header_image_url", length = 1000)
    private String headerImageUrl;

    /** URL da imagem no rodapé do formulário */
    @Column(name = "footer_image_url", length = 1000)
    private String footerImageUrl;

    /** Cor primária / destaque (botões, bordas, etc.) */
    @Column(name = "primary_color")
    private String primaryColor;

    /** Cor do texto geral do formulário */
    @Column(name = "form_text_color")
    private String formTextColor;

    /** Cor de fundo dos campos */
    @Column(name = "field_background_color")
    private String fieldBackgroundColor;

    /** Cor do texto dentro dos campos */
    @Column(name = "field_text_color")
    private String fieldTextColor;

    /** Cor de fundo dos cards, tabelas e filtros */
    @Column(name = "card_background_color")
    private String cardBackgroundColor;

    /** Cor da borda dos cards e tabelas */
    @Column(name = "card_border_color")
    private String cardBorderColor;
}