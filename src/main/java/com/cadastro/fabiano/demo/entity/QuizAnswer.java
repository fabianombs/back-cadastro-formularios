package com.cadastro.fabiano.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_answers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QuizAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private QuizSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private QuizQuestion question;

    // Pode ser null se o tempo esgotou
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id")
    private QuizOption option;

    @Column(name = "is_correct", nullable = false)
    private boolean correct = false;

    @Column(name = "points_earned", nullable = false)
    private int pointsEarned = 0;

    // Tempo que o jogador levou para responder (ms) — usado no bônus de velocidade
    @Column(name = "time_taken_ms", nullable = false)
    private long timeTakenMs = 0;

    @Column(name = "answered_at", nullable = false, updatable = false)
    private LocalDateTime answeredAt;

    @PrePersist
    void prePersist() { this.answeredAt = LocalDateTime.now(); }
}
