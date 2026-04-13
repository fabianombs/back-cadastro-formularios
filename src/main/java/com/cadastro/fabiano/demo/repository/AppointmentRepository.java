package com.cadastro.fabiano.demo.repository;

import com.cadastro.fabiano.demo.entity.Appointment;
import com.cadastro.fabiano.demo.entity.AppointmentStatus;
import com.cadastro.fabiano.demo.entity.Client;
import com.cadastro.fabiano.demo.entity.FormTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
