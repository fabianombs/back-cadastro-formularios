package com.cadastro.fabiano.demo.repository;

import com.cadastro.fabiano.demo.entity.AttendanceRecord;
import com.cadastro.fabiano.demo.entity.FormTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    List<AttendanceRecord> findByFormTemplateOrderByRowOrderAscCreatedAtAsc(FormTemplate template);

    Page<AttendanceRecord> findByFormTemplate(FormTemplate template, Pageable pageable);

    void deleteByFormTemplate(FormTemplate template);
}
