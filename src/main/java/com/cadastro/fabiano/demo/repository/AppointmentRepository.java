package com.cadastro.fabiano.demo.repository;

import com.cadastro.fabiano.demo.entity.Appointment;
import com.cadastro.fabiano.demo.entity.AppointmentStatus;
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

    Page<Appointment> findByFormTemplate(FormTemplate formTemplate, Pageable pageable);

    List<Appointment> findByFormTemplateAndStatus(FormTemplate formTemplate, AppointmentStatus status);
}
