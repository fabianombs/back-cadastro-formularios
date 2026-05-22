package com.cadastro.fabiano.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_sessions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QuizSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Sessão vinculada diretamente ao quiz (não mais ao template)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_config_id", nullable = false)
    private QuizConfig quizConfig;

    @Column(name = "player_name", nullable = false)
    private String playerName;

    @Column(name = "player_contact", nullable = false)
    private String playerContact;

    @Column(name = "total_score", nullable = false)
    private int totalScore = 0;

    @Column(name = "correct_answers", nullable = false)
    private int correctAnswers = 0;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions = 0;

    @Column(nullable = false)
    private boolean completed = false;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { this.createdAt = LocalDateTime.now(); }
}
