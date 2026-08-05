package com.cadastro.fabiano.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;

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

    @ManyToOne
    @JoinColumn(name = "form_template_id")
    private FormTemplate template;

    // O projeto ja agrupa toda colecao lazy globalmente
    // (hibernate.default_batch_fetch_size=50, application.properties:92).
    // Este @BatchSize so eleva o lote desta colecao para 100, cobrindo a
    // maior pagina que o front pede sem partir em dois lotes.
    @ElementCollection
    @BatchSize(size = 100)
    @CollectionTable(name = "form_submission_values", joinColumns = @JoinColumn(name = "submission_id"))
    @MapKeyColumn(name = "field_label")
    @Column(name = "field_value")
    private Map<String, String> values;

    @CreationTimestamp
    private LocalDateTime createdAt;
}