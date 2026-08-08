package com.cadastro.fabiano.demo.repository;

import com.cadastro.fabiano.demo.entity.AttendanceCompanion;
import com.cadastro.fabiano.demo.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttendanceCompanionRepository extends JpaRepository<AttendanceCompanion, Long> {

    List<AttendanceCompanion> findByAttendanceRecordOrderByCreatedAtAsc(AttendanceRecord record);

    // Acompanhantes de varios registros numa consulta so. A listagem de
    // presenca montava o DTO registro a registro e consultava um a um:
    // 50 linhas = 50 consultas, medido no homolog em 05/08/2026 (FABIANO-38).
    List<AttendanceCompanion> findByAttendanceRecordIdInOrderByCreatedAtAsc(List<Long> recordIds);

    void deleteByAttendanceRecord(AttendanceRecord record);
}
