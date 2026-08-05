package com.cadastro.fabiano.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    // @BatchSize mata o N+1 da listagem: sem ele, uma pagina de 20 templates
    // dispara 1 consulta para a lista + 20 para os campos. Com ele, o Hibernate
    // agrupa os ids pendentes num unico "WHERE template_id IN (...)".
    //
    // Escolhido em vez de JOIN FETCH porque nao mexe em consulta nenhuma e
    // convive com Pageable — JOIN FETCH de colecao com paginacao faz o
    // Hibernate paginar EM MEMORIA (aviso HHH000104), trazendo a tabela inteira.
    @OneToMany(mappedBy = "formTemplate", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 25)
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

    /** Ordem das colunas da planilha de presença, separadas por vírgula. */
    @Column(name = "attendance_column_order", columnDefinition = "TEXT")
    private String attendanceColumnOrder;

    @Column(name = "schedule_start_time")
    private LocalTime scheduleStartTime;

    @Column(name = "schedule_end_time")
    private LocalTime scheduleEndTime;

    @Column(name = "slot_duration_minutes")
    private Integer slotDurationMinutes;

    @Column(name = "max_days_ahead")
    private Integer maxDaysAhead;

    /** Capacidade máxima de pessoas por slot de horário */
    @Column(name = "slot_capacity", nullable = false, columnDefinition = "integer default 1")
    @Builder.Default
    private int slotCapacity = 1;

    /**
     * Campos do formulário usados como chave de deduplicação.
     * Se vazio → múltiplos agendamentos permitidos (sem restrição).
     * Se preenchido → a combinação dos valores desses campos deve ser única por template.
     * Exemplo: {"CPF"} ou {"Nome", "CPF"}
     */
    // EAGER numa colecao e o pior caso para listagem: o Hibernate emite uma
    // consulta POR LINHA da pagina, sempre, mesmo quando ninguem le o campo.
    // O @BatchSize agrupa essas consultas; trocar para LAZY exigiria conferir
    // cada uso e fica para o card do N+1 se o ganho nao bastar.
    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 25)
    @CollectionTable(
        name = "form_template_dedup_fields",
        joinColumns = @JoinColumn(name = "template_id")
    )
    @Column(name = "field_label")
    @Builder.Default
    private Set<String> dedupFields = new HashSet<>();

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

    /** Família tipográfica (ex: Poppins) */
    @Column(name = "font_family")
    private String fontFamily;

    /** Tamanho da fonte do título (ex: 22px) */
    @Column(name = "title_font_size")
    private String titleFontSize;

    /** Tamanho da fonte dos labels dos campos (ex: 14px) */
    @Column(name = "label_font_size")
    private String labelFontSize;

    /** Tamanho da fonte do botão de envio (ex: 15px) */
    @Column(name = "button_font_size")
    private String buttonFontSize;

    // =====================
    // LINK DE VISUALIZAÇÃO DO CLIENTE
    // =====================

    /** Token UUID único que identifica o link público de visualização do cliente */
    @Column(name = "view_token", unique = true)
    private String viewToken;

    /** Permite ao cliente exportar Excel na view pública */
    @Column(name = "view_allow_export", nullable = false)
    @Builder.Default
    private boolean viewAllowExport = false;

    /** Exibe a aba de respostas para o cliente */
    @Column(name = "view_show_submissions", nullable = false)
    @Builder.Default
    private boolean viewShowSubmissions = true;

    /** Exibe a aba de presença para o cliente */
    @Column(name = "view_show_attendance", nullable = false)
    @Builder.Default
    private boolean viewShowAttendance = true;

    /** Exibe a aba de agendamentos para o cliente */
    @Column(name = "view_show_appointments", nullable = false)
    @Builder.Default
    private boolean viewShowAppointments = true;

    /** Permite ao cliente MARCAR presença na view pública (sem login). Padrão: somente leitura */
    @Column(name = "view_allow_attendance_check", nullable = false)
    @Builder.Default
    private boolean viewAllowAttendanceCheck = false;

    /** Permite ao cliente ADICIONAR convidado na view pública (botão "+ Convidado"). Padrão: oculto */
    @Column(name = "view_allow_add_guest", nullable = false)
    @Builder.Default
    private boolean viewAllowAddGuest = false;

    /** Tamanho base da fonte da lista de presença (preset: SMALL/MEDIUM/LARGE/XLARGE). A view ajusta por dispositivo. */
    @Column(name = "attendance_font_scale", nullable = false, length = 16)
    @Builder.Default
    private String attendanceFontScale = "MEDIUM";

    // Visibilidade das colunas internas da lista de presenca
    @Column(name = "attendance_show_companions", nullable = false)
    @Builder.Default
    private boolean attendanceShowCompanions = true;

    @Column(name = "attendance_show_presence", nullable = false)
    @Builder.Default
    private boolean attendanceShowPresence = true;

    @Column(name = "attendance_show_notes", nullable = false)
    @Builder.Default
    private boolean attendanceShowNotes = true;

    @Column(name = "attendance_show_marked_at", nullable = false)
    @Builder.Default
    private boolean attendanceShowMarkedAt = true;

    // =====================
    // LGPD
    // =====================

    @Column(name = "lgpd_enabled", nullable = false)
    @Builder.Default
    private boolean lgpdEnabled = false;

    @Column(name = "lgpd_text", columnDefinition = "TEXT")
    private String lgpdText;

    // =====================
    // QUIZ (opcional)
    // =====================

    // Quiz associado a este template — null quando nenhum quiz foi selecionado
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_config_id")
    private QuizConfig quiz;

    // Pesquisa de satisfação exibida ao final do fluxo — null quando não vinculada
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survey_config_id")
    private SurveyConfig survey;

}