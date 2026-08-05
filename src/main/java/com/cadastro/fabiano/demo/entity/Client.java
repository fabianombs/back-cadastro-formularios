package com.cadastro.fabiano.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "clients")
@SQLRestriction("deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String email;

    private String phone;

    private String company;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    @Column(unique = true, nullable = false)
    private String username;

    // Sem @CreationTimestamp o Hibernate incluia a coluna no INSERT com NULL
    // explicito, e NULL explicito SOBREPOE o DEFAULT CURRENT_TIMESTAMP da
    // migration V2 — o padrao do MySQL so vale quando a coluna e omitida.
    // Resultado: nenhum cliente criado pela aplicacao tinha data (FABIANO-53).
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FormTemplate> templates;

    // =====================
    // SOFT DELETE
    // =====================

    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean deleted = false;
}