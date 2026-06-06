package com.cadastro.fabiano.demo.repository;

import com.cadastro.fabiano.demo.entity.SurveyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SurveyConfigRepository extends JpaRepository<SurveyConfig, Long> {
    Optional<SurveyConfig> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
