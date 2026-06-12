package com.cadastro.fabiano.demo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "equipment_options")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipmentOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_id", nullable = false)
    private EquipmentCatalog catalog;

    @Column(nullable = false)
    private String label;

    /** Quantidade total disponível (relevante apenas com estoque ligado). */
    @Column(name = "total_qty", nullable = false)
    @Builder.Default
    private int totalQty = 0;

    /** Quantidade já atribuída. Disponível = totalQty - usedCount. */
    @Column(name = "used_count", nullable = false)
    @Builder.Default
    private int usedCount = 0;

    @Transient
    public int getAvailable() {
        return Math.max(0, totalQty - usedCount);
    }
}
