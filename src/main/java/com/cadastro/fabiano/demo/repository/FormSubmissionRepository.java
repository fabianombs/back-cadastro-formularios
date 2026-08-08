package com.cadastro.fabiano.demo.repository;

import com.cadastro.fabiano.demo.entity.Client;
import com.cadastro.fabiano.demo.entity.FormSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FormSubmissionRepository extends JpaRepository<FormSubmission, Long> {

    // Para stats (DashboardService)
    List<FormSubmission> findByTemplate_Id(Long templateId);

    // Para listagem paginada
    Page<FormSubmission> findByTemplate_Id(Long templateId, Pageable pageable);

    boolean existsByIdAndTemplate_Id(Long id, Long templateId);

    void deleteByTemplate_Id(Long templateId);

    long countByTemplate_Id(Long templateId);

    long countByTemplate_Client(Client client);

    /**
     * Contagem de respostas de varios templates numa unica consulta agregada.
     * Substitui o countByTemplate_Id chamado dentro do laco do dashboard, que
     * fazia uma consulta por template da pagina (FABIANO-38).
     */
    @Query("SELECT s.template.id AS templateId, COUNT(s) AS total " +
           "FROM FormSubmission s WHERE s.template.id IN :templateIds " +
           "GROUP BY s.template.id")
    List<SubmissionCountByTemplate> countGroupedByTemplateIds(@Param("templateIds") List<Long> templateIds);

    interface SubmissionCountByTemplate {
        Long getTemplateId();
        Long getTotal();
    }
}
