package com.cadastro.fabiano.demo.entity;

import jakarta.persistence.*;
        import lombok.*;

        import java.time.LocalDateTime;


    @Entity
    @Table(name = "clients")
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

        private LocalDateTime createdAt;

        private LocalDateTime updatedAt;
    }

