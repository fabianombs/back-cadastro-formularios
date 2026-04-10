package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.dto.request.BookAppointmentRequest;
import com.cadastro.fabiano.demo.dto.response.AppointmentResponse;
import com.cadastro.fabiano.demo.dto.response.AvailableSlotsResponse;
import com.cadastro.fabiano.demo.entity.Appointment;
import com.cadastro.fabiano.demo.entity.AppointmentStatus;
import com.cadastro.fabiano.demo.entity.FormTemplate;
import com.cadastro.fabiano.demo.exception.DuplicateBookingException;
import com.cadastro.fabiano.demo.exception.SlotFullException;
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

    /**
     * Retorna todos os horários do dia para um template com agenda ativa,
     * indicando quantas vagas estão disponíveis e quantas já foram reservadas.
     *
     * @param templateId ID do template com configuração de agenda
     * @param date       data para consulta de disponibilidade
     * @return {@link AvailableSlotsResponse} com lista de slots e suas disponibilidades
     * @throws RuntimeException se o template não existir ou não possuir agenda configurada
     */
    public AvailableSlotsResponse getAvailableSlots(Long templateId, LocalDate date) {
        FormTemplate template = findTemplateWithSchedule(templateId);
        int capacity = Math.max(1, template.getSlotCapacity());

        List<LocalTime> allSlots = generateSlots(
                template.getScheduleStartTime(),
                template.getScheduleEndTime(),
                template.getSlotDurationMinutes()
        );

        // Agrupa contagem de agendados ativos por slot
        Map<LocalTime, Long> bookedCountBySlot = appointmentRepository
                .findByFormTemplateAndSlotDate(template, date)
                .stream()
                .filter(a -> a.getStatus() == AppointmentStatus.AGENDADO)
                .collect(Collectors.groupingBy(Appointment::getSlotTime, Collectors.counting()));

        List<AvailableSlotsResponse.SlotInfo> slots = allSlots.stream()
                .map(t -> {
                    int booked = bookedCountBySlot.getOrDefault(t, 0L).intValue();
                    return new AvailableSlotsResponse.SlotInfo(t, booked < capacity, booked, capacity);
                })
                .toList();

        return new AvailableSlotsResponse(date, slots);
    }

    // ==========================
    // AGENDAR HORÁRIO
    // ==========================

    /**
     * Realiza o agendamento de um horário, aplicando as seguintes regras de negócio:
     * <ol>
     *   <li>Lock pessimista no template para evitar race conditions concorrentes.</li>
     *   <li>Valida que a data está dentro do período permitido ({@code maxDaysAhead}).</li>
     *   <li>Valida que o horário é um slot válido gerado pela configuração de agenda.</li>
     *   <li>Deduplicação: se o template possuir {@code dedupFields} configurados,
     *       constrói uma chave a partir dos valores dos campos extras e rejeita
     *       um segundo agendamento com a mesma chave.</li>
     *   <li>Capacidade: rejeita o agendamento se o slot já atingiu {@code slotCapacity}.</li>
     * </ol>
     *
     * @param request dados do agendamento (templateId, data, horário, nome, contato e campos extras)
     * @return {@link AppointmentResponse} com os dados do agendamento criado
     * @throws DuplicateBookingException se a chave de deduplicação já existir para o template/data
     * @throws SlotFullException         se a capacidade máxima do slot já foi atingida
     * @throws RuntimeException          para template não encontrado, data/horário inválidos
     */
    @Transactional
    public AppointmentResponse book(BookAppointmentRequest request) {
        // Lock pessimista no template para serializar bookings concorrentes no mesmo evento
        FormTemplate template = formTemplateRepository.findByIdWithLock(request.templateId())
                .orElseThrow(() -> new RuntimeException("Template não encontrado"));

        if (!template.isHasSchedule()) {
            throw new RuntimeException("Este formulário não possui configuração de agenda");
        }

        validateSlotDate(template, request.slotDate());
        validateSlotTime(template, request.slotTime());

        Map<String, String> extraValues = request.extraValues() != null ? request.extraValues() : Map.of();

        // Regra: deduplicação por campos configurados no template
        Set<String> dedupFields = template.getDedupFields();
        String dedupKey = null;
        if (dedupFields != null && !dedupFields.isEmpty()) {
            dedupKey = buildDedupKey(dedupFields, extraValues);
            boolean alreadyBooked = appointmentRepository.existsByFormTemplateAndSlotDateAndDedupKeyAndStatus(
                    template, request.slotDate(), dedupKey, AppointmentStatus.AGENDADO);
            if (alreadyBooked) {
                String fieldNames = dedupFields.stream().sorted().collect(Collectors.joining(" + "));
                throw new DuplicateBookingException(
                        "Usuário já cadastrado na lista. " +
                        "Identificação por: " + fieldNames + ".");
            }
        }

        // Regra: verifica capacidade máxima do slot
        long bookedCount = appointmentRepository.countByFormTemplateAndSlotDateAndSlotTimeAndStatus(
                template, request.slotDate(), request.slotTime(), AppointmentStatus.AGENDADO);

        int effectiveCapacity = Math.max(1, template.getSlotCapacity());
        if (bookedCount >= effectiveCapacity) {
            throw new SlotFullException(
                    "Este horário está lotado (" + effectiveCapacity + " vaga(s) preenchida(s)). Escolha outro horário.");
        }

        Appointment appointment = Appointment.builder()
                .formTemplate(template)
                .slotDate(request.slotDate())
                .slotTime(request.slotTime())
                .status(AppointmentStatus.AGENDADO)
                .bookedByName(request.bookedByName())
                .bookedByContact(request.bookedByContact())
                .dedupKey(dedupKey)
                .extraValues(extraValues)
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
    // EXCLUIR AGENDAMENTO
    // ==========================
    @Transactional
    public void deleteAppointment(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Agendamento não encontrado"));
        appointmentRepository.delete(appointment);
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

    /**
     * Monta a chave de deduplicação a partir dos campos configurados.
     * Campos são ordenados alfabeticamente para garantir consistência independente
     * da ordem em que o criador do template os selecionou.
     * Valores são normalizados (trim + lowercase) para evitar falsos negativos.
     *
     * Exemplo: campos={"CPF","Nome"}, extraValues={"Nome":"João Silva","CPF":"123.456.789-00"}
     * → "cpf=123.456.789-00|nome=joão silva"
     */
    private String buildDedupKey(Set<String> fields, Map<String, String> extraValues) {
        return fields.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(field -> {
                    String value = extraValues.getOrDefault(field, "").trim().toLowerCase();
                    return field.toLowerCase() + "=" + value;
                })
                .collect(Collectors.joining("|"));
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
