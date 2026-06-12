package com.cadastro.fabiano.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "equipment_catalogs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipmentCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_template_id", nullable = false)
    private FormTemplate formTemplate;

    @Column(nullable = false)
    private String name;

    /** Chave estavel gravada no rowData de cada registro (identifica a coluna do select). */
    @Column(name = "column_key")
    private String columnKey;

    /** Coluna da planilha de onde os valores foram extraidos (informativo). */
    @Column(name = "source_column")
    private String sourceColumn;

    /** Liga/desliga o controle de disponibilidade (estoque). */
    @Column(name = "stock_control", nullable = false)
    @Builder.Default
    private boolean stockControl = false;

    /** Mostra/oculta a coluna no controle do admin. */
    @Column(name = "visible", nullable = false)
    @Builder.Default
    private boolean visible = true;

    @OneToMany(mappedBy = "catalog", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EquipmentOption> options = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
