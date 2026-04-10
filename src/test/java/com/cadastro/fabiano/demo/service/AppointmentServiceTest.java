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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private FormTemplateRepository formTemplateRepository;

    @InjectMocks
    private AppointmentService service;

    private FormTemplate template;

    @BeforeEach
    void setUp() {
        template = FormTemplate.builder()
                .id(1L)
                .name("Consulta")
                .slug("consulta")
                .hasSchedule(true)
                .scheduleStartTime(LocalTime.of(8, 0))
                .scheduleEndTime(LocalTime.of(12, 0))
                .slotDurationMinutes(60)
                .maxDaysAhead(30)
                .slotCapacity(2)
                .build();
    }

    // ─── getAvailableSlots ────────────────────────────────────────────────────

    @Test
    @DisplayName("getAvailableSlots: retorna slots do dia com contagem correta")
    void getAvailableSlots_success() {
        LocalDate date = LocalDate.now().plusDays(1);

        Appointment booked = Appointment.builder()
                .slotTime(LocalTime.of(8, 0))
                .status(AppointmentStatus.AGENDADO)
                .build();

        when(formTemplateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(appointmentRepository.findByFormTemplateAndSlotDate(template, date))
                .thenReturn(List.of(booked));

        AvailableSlotsResponse response = service.getAvailableSlots(1L, date);

        assertThat(response.date()).isEqualTo(date);
        assertThat(response.slots()).hasSize(4); // 08:00, 09:00, 10:00, 11:00
        assertThat(response.slots().get(0).bookedCount()).isEqualTo(1);
        assertThat(response.slots().get(0).available()).isTrue(); // capacity=2, booked=1
    }

    @Test
    @DisplayName("getAvailableSlots: lança exceção se template não tem agenda")
    void getAvailableSlots_noSchedule_throws() {
        template.setHasSchedule(false);
        when(formTemplateRepository.findById(1L)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.getAvailableSlots(1L, LocalDate.now().plusDays(1)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("agenda");
    }

    @Test
    @DisplayName("getAvailableSlots: lança exceção se template não encontrado")
    void getAvailableSlots_notFound_throws() {
        when(formTemplateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAvailableSlots(99L, LocalDate.now().plusDays(1)))
                .isInstanceOf(RuntimeException.class);
    }

    // ─── book ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("book: agendamento criado com sucesso sem deduplicação")
    void book_success_noDedupFields() {
        LocalDate date = LocalDate.now().plusDays(1);
        LocalTime time = LocalTime.of(8, 0);

        BookAppointmentRequest request = new BookAppointmentRequest(
                1L, date, time, "João", "11999990000", null);

        Appointment saved = Appointment.builder()
                .id(10L)
                .formTemplate(template)
                .slotDate(date)
                .slotTime(time)
                .status(AppointmentStatus.AGENDADO)
                .bookedByName("João")
                .bookedByContact("11999990000")
                .createdAt(LocalDateTime.now())
                .build();

        when(formTemplateRepository.findByIdWithLock(1L)).thenReturn(Optional.of(template));
        when(appointmentRepository.countByFormTemplateAndSlotDateAndSlotTimeAndStatus(
                template, date, time, AppointmentStatus.AGENDADO)).thenReturn(0L);
        when(appointmentRepository.save(any())).thenReturn(saved);

        AppointmentResponse response = service.book(request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(AppointmentStatus.AGENDADO);
    }

    @Test
    @DisplayName("book: lança SlotFullException quando capacidade esgotada")
    void book_slotFull_throws() {
        LocalDate date = LocalDate.now().plusDays(1);
        LocalTime time = LocalTime.of(8, 0);

        BookAppointmentRequest request = new BookAppointmentRequest(
                1L, date, time, "Maria", "11988880000", null);

        when(formTemplateRepository.findByIdWithLock(1L)).thenReturn(Optional.of(template));
        when(appointmentRepository.countByFormTemplateAndSlotDateAndSlotTimeAndStatus(
                template, date, time, AppointmentStatus.AGENDADO)).thenReturn(2L); // capacity = 2

        assertThatThrownBy(() -> service.book(request))
                .isInstanceOf(SlotFullException.class);
    }

    @Test
    @DisplayName("book: lança DuplicateBookingException quando chave de dedup já existe")
    void book_duplicateDedup_throws() {
        LocalDate date = LocalDate.now().plusDays(1);
        LocalTime time = LocalTime.of(8, 0);

        template.getDedupFields().add("CPF");

        BookAppointmentRequest request = new BookAppointmentRequest(
                1L, date, time, "Ana", "11977770000",
                Map.of("CPF", "123.456.789-00"));

        when(formTemplateRepository.findByIdWithLock(1L)).thenReturn(Optional.of(template));
        when(appointmentRepository.existsByFormTemplateAndSlotDateAndDedupKeyAndStatus(
                eq(template), eq(date), anyString(), eq(AppointmentStatus.AGENDADO)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.book(request))
                .isInstanceOf(DuplicateBookingException.class);
    }

    @Test
    @DisplayName("book: lança exceção para data no passado")
    void book_pastDate_throws() {
        LocalDate pastDate = LocalDate.now().minusDays(1);

        BookAppointmentRequest request = new BookAppointmentRequest(
                1L, pastDate, LocalTime.of(8, 0), "Joana", "119", null);

        when(formTemplateRepository.findByIdWithLock(1L)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.book(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("passadas");
    }

    @Test
    @DisplayName("book: lança exceção para data além do maxDaysAhead")
    void book_dateTooFarAhead_throws() {
        LocalDate farDate = LocalDate.now().plusDays(31); // maxDaysAhead = 30

        BookAppointmentRequest request = new BookAppointmentRequest(
                1L, farDate, LocalTime.of(8, 0), "Pedro", "119", null);

        when(formTemplateRepository.findByIdWithLock(1L)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.book(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("próximos");
    }

    @Test
    @DisplayName("book: lança exceção para horário inválido")
    void book_invalidSlotTime_throws() {
        LocalDate date = LocalDate.now().plusDays(1);
        LocalTime invalidTime = LocalTime.of(8, 30); // slots são de 60 em 60 min

        BookAppointmentRequest request = new BookAppointmentRequest(
                1L, date, invalidTime, "Carlos", "119", null);

        when(formTemplateRepository.findByIdWithLock(1L)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.book(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("inválido");
    }

    @Test
    @DisplayName("book: lança exceção se template não tem agenda")
    void book_noSchedule_throws() {
        template.setHasSchedule(false);
        LocalDate date = LocalDate.now().plusDays(1);

        BookAppointmentRequest request = new BookAppointmentRequest(
                1L, date, LocalTime.of(8, 0), "Luis", "119", null);

        when(formTemplateRepository.findByIdWithLock(1L)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.book(request))
                .isInstanceOf(RuntimeException.class);
    }

    // ─── cancel ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cancel: cancela agendamento com sucesso")
    void cancel_success() {
        Appointment appointment = Appointment.builder()
                .id(5L)
                .formTemplate(template)
                .slotDate(LocalDate.now().plusDays(1))
                .slotTime(LocalTime.of(9, 0))
                .status(AppointmentStatus.AGENDADO)
                .createdAt(LocalDateTime.now())
                .build();

        when(appointmentRepository.findById(5L)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AppointmentResponse response = service.cancel(5L, "admin");

        assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELADO);
    }

    @Test
    @DisplayName("cancel: lança exceção ao cancelar agendamento já cancelado")
    void cancel_alreadyCancelled_throws() {
        Appointment appointment = Appointment.builder()
                .id(5L)
                .formTemplate(template)
                .status(AppointmentStatus.CANCELADO)
                .createdAt(LocalDateTime.now())
                .build();

        when(appointmentRepository.findById(5L)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.cancel(5L, "admin"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("já está cancelado");
    }

    @Test
    @DisplayName("cancel: lança exceção se agendamento não encontrado")
    void cancel_notFound_throws() {
        when(appointmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(99L, "admin"))
                .isInstanceOf(RuntimeException.class);
    }

    // ─── deleteAppointment ────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteAppointment: exclui com sucesso")
    void deleteAppointment_success() {
        Appointment appointment = Appointment.builder().id(3L).formTemplate(template).build();
        when(appointmentRepository.findById(3L)).thenReturn(Optional.of(appointment));

        service.deleteAppointment(3L);

        verify(appointmentRepository).delete(appointment);
    }

    @Test
    @DisplayName("deleteAppointment: lança exceção se não encontrado")
    void deleteAppointment_notFound_throws() {
        when(appointmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteAppointment(99L))
                .isInstanceOf(RuntimeException.class);
    }

    // ─── getByTemplate ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getByTemplate: retorna página de agendamentos")
    void getByTemplate_success() {
        Appointment a = Appointment.builder()
                .id(1L)
                .formTemplate(template)
                .slotDate(LocalDate.now())
                .slotTime(LocalTime.of(8, 0))
                .status(AppointmentStatus.AGENDADO)
                .createdAt(LocalDateTime.now())
                .build();

        PageRequest pageable = PageRequest.of(0, 10);
        when(formTemplateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(appointmentRepository.findByFormTemplate(template, pageable))
                .thenReturn(new PageImpl<>(List.of(a)));

        Page<AppointmentResponse> result = service.getByTemplate(1L, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ─── getAvailableSlotsRange ───────────────────────────────────────────────

    @Test
    @DisplayName("getAvailableSlotsRange: retorna slots para múltiplos dias")
    void getAvailableSlotsRange_success() {
        LocalDate from = LocalDate.now().plusDays(1);
        LocalDate to = from.plusDays(2);

        when(formTemplateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(appointmentRepository.findByFormTemplateAndSlotDate(eq(template), any()))
                .thenReturn(List.of());

        List<AvailableSlotsResponse> result = service.getAvailableSlotsRange(1L, from, to);

        assertThat(result).hasSize(3); // from, from+1, from+2 (inclusive)
    }

    // ─── buildDedupKey (via book) ─────────────────────────────────────────────

    @Test
    @DisplayName("book: chave de dedup é construída em ordem alfabética (case-insensitive)")
    void book_dedupKey_alphabeticOrder() {
        LocalDate date = LocalDate.now().plusDays(1);
        LocalTime time = LocalTime.of(8, 0);

        template.getDedupFields().addAll(Set.of("Nome", "CPF"));

        BookAppointmentRequest request = new BookAppointmentRequest(
                1L, date, time, "Ana", "119",
                Map.of("CPF", "111", "Nome", "Ana"));

        Appointment saved = Appointment.builder()
                .id(1L).formTemplate(template).slotDate(date).slotTime(time)
                .status(AppointmentStatus.AGENDADO).createdAt(LocalDateTime.now()).build();

        when(formTemplateRepository.findByIdWithLock(1L)).thenReturn(Optional.of(template));
        when(appointmentRepository.existsByFormTemplateAndSlotDateAndDedupKeyAndStatus(
                eq(template), eq(date), eq("cpf=111|nome=ana"), eq(AppointmentStatus.AGENDADO)))
                .thenReturn(false);
        when(appointmentRepository.countByFormTemplateAndSlotDateAndSlotTimeAndStatus(
                template, date, time, AppointmentStatus.AGENDADO)).thenReturn(0L);
        when(appointmentRepository.save(any())).thenReturn(saved);

        service.book(request);

        verify(appointmentRepository).existsByFormTemplateAndSlotDateAndDedupKeyAndStatus(
                eq(template), eq(date), eq("cpf=111|nome=ana"), eq(AppointmentStatus.AGENDADO));
    }
}
