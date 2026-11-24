package com.cadastro.fabiano.demo.repository;

import com.cadastro.fabiano.demo.entity.QuizAnswer;
import com.cadastro.fabiano.demo.entity.QuizQuestion;
import com.cadastro.fabiano.demo.entity.QuizSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, Long> {
    List<QuizAnswer> findBySession(QuizSession session);
    Optional<QuizAnswer> findFirstBySessionAndQuestion(QuizSession session, QuizQuestion question);
    void deleteBySession(QuizSession session);
}
