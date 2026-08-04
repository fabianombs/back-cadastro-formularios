package com.cadastro.fabiano.demo.repository;

import com.cadastro.fabiano.demo.entity.Appointment;
import com.cadastro.fabiano.demo.entity.AppointmentStatus;
import com.cadastro.fabiano.demo.entity.Client;
import com.cadastro.fabiano.demo.entity.FormTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByFormTemplateAndSlotDate(FormTemplate formTemplate, LocalDate date);

    List<Appointment> findByFormTemplateAndSlotDateAndSlotTimeAndStatus(
            FormTemplate formTemplate, LocalDate date, LocalTime time, AppointmentStatus status);

    /** Contagem de agendados num slot — usada para verificar capacidade */
    long countByFormTemplateAndSlotDateAndSlotTimeAndStatus(
            FormTemplate formTemplate, LocalDate date, LocalTime time, AppointmentStatus status);

    /**
     * Verifica duplicidade dentro do mesmo dia.
     * Mesma pessoa (dedupKey) só pode ter 1 agendamento ativo por data no template.
     */
    boolean existsByFormTemplateAndSlotDateAndDedupKeyAndStatus(
            FormTemplate formTemplate, LocalDate slotDate, String dedupKey, AppointmentStatus status);

    // Para o DashboardService (stats, sem paginação)
    List<Appointment> findByFormTemplate(FormTemplate formTemplate);

    // Para o controller (paginado)
    Page<Appointment> findByFormTemplate(FormTemplate formTemplate, Pageable pageable);

    List<Appointment> findByFormTemplateAndStatus(FormTemplate formTemplate, AppointmentStatus status);

    long countByFormTemplate(FormTemplate formTemplate);

    long countByFormTemplateAndStatus(FormTemplate formTemplate, AppointmentStatus status);

    long countByStatus(AppointmentStatus status);

    long countByFormTemplate_Client(Client client);

    long countByFormTemplate_ClientAndStatus(Client client, AppointmentStatus status);

    void deleteByFormTemplate(FormTemplate formTemplate);

    /**
     * Total, agendados e cancelados de varios templates numa consulta so.
     * O dashboard fazia tres COUNT por template; agora sao tres somas dentro
     * do mesmo GROUP BY (FABIANO-38).
     *
     * Os status vem por parametro em vez de literal no JPQL para o nome do
     * enum nao ficar duplicado dentro de uma string.
     */
    @Query("SELECT a.formTemplate.id AS templateId, " +
           "COUNT(a) AS total, " +
           "SUM(CASE WHEN a.status = :agendado THEN 1L ELSE 0L END) AS confirmed, " +
           "SUM(CASE WHEN a.status = :cancelado THEN 1L ELSE 0L END) AS cancelled " +
           "FROM Appointment a WHERE a.formTemplate.id IN :templateIds " +
           "GROUP BY a.formTemplate.id")
    List<AppointmentCountByTemplate> countGroupedByTemplateIds(
            @Param("templateIds") List<Long> templateIds,
            @Param("agendado") AppointmentStatus agendado,
            @Param("cancelado") AppointmentStatus cancelado);

    interface AppointmentCountByTemplate {
        Long getTemplateId();
        Long getTotal();
        Long getConfirmed();
        Long getCancelled();
    }
}
