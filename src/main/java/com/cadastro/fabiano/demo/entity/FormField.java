package com.cadastro.fabiano.demo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "form_fields")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String label; // Ex: "Nome", "Telefone"
    private String type; // Ex: "text", "number", "date"
    private boolean required;

    /** Cor personalizada para este campo específico (hex, ex: #3b82f6) */
    @Column(name = "field_color")
    private String fieldColor;

    /**
     * Largura em colunas do grid (2 = largura total, 1 = meia largura).
     * Valor padrão: 2.
     */
    @Column(name = "col_span", nullable = false)
    @Builder.Default
    private int colSpan = 2;

    @ManyToOne
    @JoinColumn(name = "form_template_id")
    private FormTemplate formTemplate;
}