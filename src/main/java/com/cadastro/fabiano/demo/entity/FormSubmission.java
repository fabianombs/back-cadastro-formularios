package com.cadastro.fabiano.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "form_submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relacionamento com template
    @ManyToOne
    @JoinColumn(name = "template_id")
    private FormTemplate template;

    // Valores preenchidos pelo cliente
    @ElementCollection
    @CollectionTable(name = "form_submission_values", joinColumns = @JoinColumn(name = "submission_id"))
    @MapKeyColumn(name = "field_label")
    @Column(name = "field_value")
    private Map<String, String> values;

    private LocalDateTime createdAt = LocalDateTime.now();
}