package com.cadastro.fabiano.demo.repository;

import com.cadastro.fabiano.demo.entity.Client;
import com.cadastro.fabiano.demo.entity.FormSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FormSubmissionRepository extends JpaRepository<FormSubmission, Long> {

    // Para stats (DashboardService)
    List<FormSubmission> findByTemplate_Id(Long templateId);

    // Para listagem paginada
    Page<FormSubmission> findByTemplate_Id(Long templateId, Pageable pageable);

    boolean existsByIdAndTemplate_Id(Long id, Long templateId);

    long countByTemplate_Id(Long templateId);

    long countByTemplate_Client(Client client);
}
