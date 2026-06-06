package com.cadastro.fabiano.demo.repository;

import com.cadastro.fabiano.demo.entity.SurveyResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Map;

public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, Long> {

    List<SurveyResponse> findBySurveyIdOrderByCreatedAtDesc(Long surveyId);

    long countBySurveyId(Long surveyId);

    // Contagem agrupada por score para o gráfico de distribuição
    @Query("SELECT r.score, COUNT(r) FROM SurveyResponse r WHERE r.survey.id = :surveyId GROUP BY r.score")
    List<Object[]> countByScoreGrouped(@Param("surveyId") Long surveyId);

    @Query("SELECT AVG(r.score) FROM SurveyResponse r WHERE r.survey.id = :surveyId")
    Double avgScoreBySurveyId(@Param("surveyId") Long surveyId);
}
