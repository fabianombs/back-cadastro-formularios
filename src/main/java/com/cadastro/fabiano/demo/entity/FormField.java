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

    @ManyToOne
    @JoinColumn(name = "form_template_id")
    private FormTemplate formTemplate;
}