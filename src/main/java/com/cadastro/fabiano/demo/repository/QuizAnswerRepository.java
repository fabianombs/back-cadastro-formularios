package com.cadastro.fabiano.demo.repository;

import com.cadastro.fabiano.demo.entity.QuizAnswer;
import com.cadastro.fabiano.demo.entity.QuizSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, Long> {
    List<QuizAnswer> findBySession(QuizSession session);
    void deleteBySession(QuizSession session);
}
