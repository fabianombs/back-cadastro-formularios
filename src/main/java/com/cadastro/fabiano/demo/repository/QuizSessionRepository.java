package com.cadastro.fabiano.demo.repository;

import com.cadastro.fabiano.demo.entity.QuizConfig;
import com.cadastro.fabiano.demo.entity.QuizSession;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface QuizSessionRepository extends JpaRepository<QuizSession, Long> {

    // Ranking público — top N sessões completas ordenadas por score desc, depois por tempo de conclusão
    @Query("SELECT s FROM QuizSession s WHERE s.quizConfig = :quizConfig AND s.completed = true ORDER BY s.totalScore DESC, s.completedAt ASC")
    List<QuizSession> findRanking(QuizConfig quizConfig, Pageable pageable);

    // Todas as sessões completas para relatório admin
    List<QuizSession> findByQuizConfigAndCompletedTrueOrderByTotalScoreDescCompletedAtAsc(QuizConfig quizConfig);

    long countByQuizConfigAndCompletedTrue(QuizConfig quizConfig);
}
