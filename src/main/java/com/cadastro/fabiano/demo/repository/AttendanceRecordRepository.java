package com.cadastro.fabiano.demo.repository;

import com.cadastro.fabiano.demo.entity.AttendanceRecord;
import com.cadastro.fabiano.demo.entity.Client;
import com.cadastro.fabiano.demo.entity.FormTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    // Para stats (DashboardService)
    List<AttendanceRecord> findByFormTemplateOrderByRowOrderAscCreatedAtAsc(FormTemplate template);

    // Para listagem paginada
    Page<AttendanceRecord> findByFormTemplateOrderByRowOrderAscCreatedAtAsc(FormTemplate template, Pageable pageable);

    void deleteByFormTemplate(FormTemplate template);

    long countByFormTemplate(FormTemplate template);

    long countByFormTemplateAndAttended(FormTemplate template, boolean attended);

    long countByAttended(boolean attended);

    long countByFormTemplate_Client(Client client);

    long countByFormTemplate_ClientAndAttended(Client client, boolean attended);

    @Query("SELECT ar.formTemplate.id as templateId, COUNT(ar) as attendanceCount " +
           "FROM AttendanceRecord ar WHERE ar.formTemplate.id IN :templateIds " +
           "GROUP BY ar.formTemplate.id")
    List<AttendanceCountByTemplate> countByTemplateIds(@Param("templateIds") List<Long> templateIds);

    interface AttendanceCountByTemplate {
        Long getTemplateId();
        Long getAttendanceCount();
    }

    /**
     * Total e presentes de varios templates numa consulta so. O dashboard
     * fazia dois COUNT por template da pagina (FABIANO-38).
     */
    @Query("SELECT ar.formTemplate.id AS templateId, " +
           "COUNT(ar) AS total, " +
           "SUM(CASE WHEN ar.attended = TRUE THEN 1L ELSE 0L END) AS present " +
           "FROM AttendanceRecord ar WHERE ar.formTemplate.id IN :templateIds " +
           "GROUP BY ar.formTemplate.id")
    List<AttendanceStatsByTemplate> countGroupedByTemplateIds(@Param("templateIds") List<Long> templateIds);

    interface AttendanceStatsByTemplate {
        Long getTemplateId();
        Long getTotal();
        Long getPresent();
    }
}
