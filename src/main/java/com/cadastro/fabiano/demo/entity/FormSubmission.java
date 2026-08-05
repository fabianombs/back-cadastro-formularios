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

    // Respostas da submissao: uma consulta por submissao na listagem.
    @ElementCollection
    @BatchSize(size = 100)
    @CollectionTable(name = "form_submission_values", joinColumns = @JoinColumn(name = "submission_id"))
    @MapKeyColumn(name = "field_label")
    @Column(name = "field_value")
    private Map<String, String> values;

    @CreationTimestamp
    private LocalDateTime createdAt;
}