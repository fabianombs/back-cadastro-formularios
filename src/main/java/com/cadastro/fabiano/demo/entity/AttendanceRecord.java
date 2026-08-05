package com.cadastro.fabiano.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "attendance_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_template_id", nullable = false)
    private FormTemplate formTemplate;

    // O N+1 mais caro do sistema. Cada registro carrega seu rowData numa
    // consulta propria: uma pagina de 50 linhas fazia 54 consultas, medido no
    // homolog em 05/08/2026. Numa lista de 1005 registros, o custo escala com
    // o tamanho da pagina, e esta e a rota mais acessada pelo cliente do
    // Fabiano — a tela de presenca no tablet, durante o evento.
    //
    // @BatchSize agrupa os ids pendentes num "WHERE record_id IN (...)".
    // Tamanho 100 para cobrir com folga a maior pagina que o front pede.
    @ElementCollection
    @BatchSize(size = 100)
    @CollectionTable(
        name = "attendance_record_data",
        joinColumns = @JoinColumn(name = "record_id")
    )
    @MapKeyColumn(name = "col_key")
    @Column(name = "col_value", columnDefinition = "TEXT")
    @Builder.Default
    private Map<String, String> rowData = new HashMap<>();

    @Column(nullable = false)
    @Builder.Default
    private boolean attended = false;

    @Column(name = "attended_at")
    private LocalDateTime attendedAt;

    @Column(name = "notes")
    private String notes;

    // Quantidade de acompanhantes que o convidado trouxe ao evento
    @Column(name = "companions_count", nullable = false)
    @Builder.Default
    private int companionsCount = 0;

    @Column(name = "row_order")
    private Integer rowOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Quando a linha foi efetivamente preenchida (editada na tabela ou cadastrada pelo público).
    // NULL em linhas só importadas — a coluna "Preenchido em" fica em branco até preencherem.
    @Column(name = "filled_at")
    private LocalDateTime filledAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
