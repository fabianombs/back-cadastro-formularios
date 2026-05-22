package com.cadastro.fabiano.demo.repository;

import com.cadastro.fabiano.demo.entity.QuizConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface QuizConfigRepository extends JpaRepository<QuizConfig, Long> {
    Optional<QuizConfig> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
