package com.cadastro.fabiano.demo.repository;

import com.cadastro.fabiano.demo.entity.AttendanceCompanion;
import com.cadastro.fabiano.demo.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttendanceCompanionRepository extends JpaRepository<AttendanceCompanion, Long> {

    List<AttendanceCompanion> findByAttendanceRecordOrderByCreatedAtAsc(AttendanceRecord record);

    void deleteByAttendanceRecord(AttendanceRecord record);
}
