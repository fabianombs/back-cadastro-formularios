package com.cadastro.fabiano.demo.repository;

import com.cadastro.fabiano.demo.entity.FormSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FormSubmissionRepository extends JpaRepository<FormSubmission, Long> {

    List<FormSubmission> findByTemplate_Id(Long templateId);

    boolean existsByIdAndTemplate_Id(Long id, Long templateId);

}