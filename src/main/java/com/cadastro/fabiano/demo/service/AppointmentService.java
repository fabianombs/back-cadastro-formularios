package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.dto.request.BookAppointmentRequest;
import com.cadastro.fabiano.demo.dto.response.AppointmentResponse;
import com.cadastro.fabiano.demo.dto.response.AvailableSlotsResponse;
import com.cadastro.fabiano.demo.entity.Appointment;
import com.cadastro.fabiano.demo.entity.AppointmentStatus;
import com.cadastro.fabiano.demo.entity.FormTemplate;
import com.cadastro.fabiano.demo.repository.AppointmentRepository;
import com.cadastro.fabiano.demo.repository.FormTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final FormTemplateRepository formTemplateRepository;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              FormTemplateRepository formTemplateRepository) {
        this.appointmentRepository = appointmentRepository;
        this.formTemplateRepository = formTemplateRepository;
    }

    // ==========================
    // SLOTS DISPONÍVEIS POR DIA
    // ==========================
    public AvailableSlotsResponse getAvailableSlots(Long templateId, LocalDate date) {
        FormTemplate template = findTemplateWithSchedule(templateId);

        // Gera todos os slots do dia baseado na configuração
        List<LocalTime> allSlots = generateSlots(
                template.getScheduleStartTime(),
                template.getScheduleEndTime(),
                template.getSlotDurationMinutes()
        );

        // Busca slots já ocupados (status AGENDADO)
        Set<LocalTime> bookedTimes = appointmentRepository
                .findByFormTemplateAndSlotDate(template, date)
                .stream()
                .filter(a -> a.getStatus() == AppointmentStatus.AGENDADO)
                .map(Appointment::getSlotTime)
                .collect(Collectors.toSet());

        List<AvailableSlotsResponse.SlotInfo> slots = allSlots.stream()
                .map(t -> new AvailableSlotsResponse.SlotInfo(t, !bookedTimes.contains(t)))
                .toList();

        return new AvailableSlotsResponse(date, slots);
    }

    // ==========================
    // AGENDAR HORÁRIO
    // ==========================
    @Transactional
    public AppointmentResponse book(BookAppointmentRequest request) {
        FormTemplate template = findTemplateWithSchedule(request.templateId());

        validateSlotDate(template, request.slotDate());
        validateSlotTime(template, request.slotTime());

        // Verifica se já existe agendamento ativo para esse slot
        List<Appointment> existing = appointmentRepository
                .findByFormTemplateAndSlotDateAndSlotTimeAndStatus(
                        template, request.slotDate(), request.slotTime(), AppointmentStatus.AGENDADO
                );

        if (!existing.isEmpty()) {
            throw new RuntimeException("Este horário já está reservado. Escolha outro.");
        }

        Appointment appointment = Appointment.builder()
                .formTemplate(template)
                .slotDate(request.slotDate())
                .slotTime(request.slotTime())
                .status(AppointmentStatus.AGENDADO)
                .bookedByName(request.bookedByName())
                .bookedByContact(request.bookedByContact())
                .extraValues(request.extraValues() != null ? request.extraValues() : Map.of())
                .build();

        return toResponse(appointmentRepository.save(appointment));
    }

    // ==========================
    // CANCELAR AGENDAMENTO
    // ==========================
    @Transactional
    public AppointmentResponse cancel(Long appointmentId, String cancelledBy) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Agendamento não encontrado"));

        if (appointment.getStatus() == AppointmentStatus.CANCELADO) {
            throw new RuntimeException("Este agendamento já está cancelado");
        }

        appointment.setStatus(AppointmentStatus.CANCELADO);
        appointment.setCancelledBy(cancelledBy);
        appointment.setCancelledAt(java.time.LocalDateTime.now());

        return toResponse(appointmentRepository.save(appointment));
    }

    // ==========================
    // BUSCAR AGENDAMENTOS DO TEMPLATE
    // ==========================
    public Page<AppointmentResponse> getByTemplate(Long templateId, Pageable pageable) {
        FormTemplate template = formTemplateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template não encontrado"));

        return appointmentRepository.findByFormTemplate(template, pageable)
                .map(this::toResponse);
    }

    // ==========================
    // SLOTS DE MÚLTIPLOS DIAS
    // ==========================
    public List<AvailableSlotsResponse> getAvailableSlotsRange(Long templateId, LocalDate from, LocalDate to) {
        findTemplateWithSchedule(templateId); // valida que o template existe e tem agenda

        List<AvailableSlotsResponse> result = new ArrayList<>();
        LocalDate current = from;
        while (!current.isAfter(to)) {
            result.add(getAvailableSlots(templateId, current));
            current = current.plusDays(1);
        }
        return result;
    }

    // ==========================
    // HELPERS
    // ==========================
    private FormTemplate findTemplateWithSchedule(Long templateId) {
        FormTemplate template = formTemplateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template não encontrado"));

        if (!template.isHasSchedule()) {
            throw new RuntimeException("Este formulário não possui configuração de agenda");
        }
        return template;
    }

    private List<LocalTime> generateSlots(LocalTime start, LocalTime end, int durationMinutes) {
        List<LocalTime> slots = new ArrayList<>();
        LocalTime current = start;
        while (current.plusMinutes(durationMinutes).compareTo(end) <= 0) {
            slots.add(current);
            current = current.plusMinutes(durationMinutes);
        }
        return slots;
    }

    private void validateSlotDate(FormTemplate template, LocalDate date) {
        LocalDate today = LocalDate.now();
        LocalDate maxDate = today.plusDays(template.getMaxDaysAhead());

        if (date.isBefore(today)) {
            throw new RuntimeException("Não é possível agendar em datas passadas");
        }
        if (date.isAfter(maxDate)) {
            throw new RuntimeException("Agendamento disponível apenas para os próximos "
                    + template.getMaxDaysAhead() + " dias");
        }
    }

    private void validateSlotTime(FormTemplate template, LocalTime time) {
        List<LocalTime> validSlots = generateSlots(
                template.getScheduleStartTime(),
                template.getScheduleEndTime(),
                template.getSlotDurationMinutes()
        );
        if (!validSlots.contains(time)) {
            throw new RuntimeException("Horário inválido para este formulário");
        }
    }

    private AppointmentResponse toResponse(Appointment a) {
        return new AppointmentResponse(
                a.getId(),
                a.getFormTemplate().getId(),
                a.getFormTemplate().getName(),
                a.getSlotDate(),
                a.getSlotTime(),
                a.getStatus(),
                a.getBookedByName(),
                a.getBookedByContact(),
                a.getCancelledBy(),
                a.getCancelledAt(),
                a.getExtraValues(),
                a.getCreatedAt()
        );
    }
}
