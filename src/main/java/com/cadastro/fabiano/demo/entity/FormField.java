package com.cadastro.fabiano.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

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
    private String type; // Ex: "text", "number", "date", "select"
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

    /**
     * Opções disponíveis para campos do tipo "select".
     * Armazenadas em tabela separada (form_field_options).
     */
    // Opcoes do campo: uma consulta por CAMPO ao montar a resposta do
    // template. Um formulario de 20 campos fazia 20 consultas so aqui.
    @ElementCollection
    @BatchSize(size = 100)
    @CollectionTable(name = "form_field_options", joinColumns = @JoinColumn(name = "form_field_id"))
    @Column(name = "option_value")
    @OrderColumn(name = "option_order")
    @Builder.Default
    private List<String> options = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "form_template_id")
    private FormTemplate formTemplate;
}