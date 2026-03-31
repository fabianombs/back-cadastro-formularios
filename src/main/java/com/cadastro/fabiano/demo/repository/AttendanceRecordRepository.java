package com.cadastro.fabiano.demo.repository;

import com.cadastro.fabiano.demo.entity.AttendanceRecord;
import com.cadastro.fabiano.demo.entity.FormTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    // Para stats (DashboardService)
    List<AttendanceRecord> findByFormTemplateOrderByRowOrderAscCreatedAtAsc(FormTemplate template);

    // Para listagem paginada
    Page<AttendanceRecord> findByFormTemplateOrderByRowOrderAscCreatedAtAsc(FormTemplate template, Pageable pageable);

    void deleteByFormTemplate(FormTemplate template);

    long countByFormTemplate(FormTemplate template);

    long countByFormTemplateAndAttended(FormTemplate template, boolean attended);
}
