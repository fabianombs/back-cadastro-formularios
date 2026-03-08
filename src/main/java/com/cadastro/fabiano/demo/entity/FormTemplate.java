package com.cadastro.fabiano.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

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

    private String name; // Nome do template

    @ManyToOne
    @JoinColumn(name = "client_id")
    private User client; // Cliente que terá acesso ao formulário

    @OneToMany(mappedBy = "formTemplate", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FormField> fields;
}