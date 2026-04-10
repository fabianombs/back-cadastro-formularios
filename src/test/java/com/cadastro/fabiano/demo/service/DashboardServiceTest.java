package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.dto.response.DashboardResponse;
import com.cadastro.fabiano.demo.entity.AppointmentStatus;
import com.cadastro.fabiano.demo.entity.Client;
import com.cadastro.fabiano.demo.entity.FormTemplate;
import com.cadastro.fabiano.demo.repository.AppointmentRepository;
import com.cadastro.fabiano.demo.repository.AttendanceRecordRepository;
import com.cadastro.fabiano.demo.repository.ClientRepository;
import com.cadastro.fabiano.demo.repository.FormSubmissionRepository;
import com.cadastro.fabiano.demo.repository.FormTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private FormTemplateRepository templateRepository;

    @Mock
    private FormSubmissionRepository submissionRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private ClientRepository clientRepository;

    @InjectMocks
    private DashboardService service;

    private Client client;
    private FormTemplate template;

    @BeforeEach
    void setUp() {
        client = Client.builder()
                .id(1L)
                .name("Cliente Teste")
                .username("cliente_teste")
                .build();

        template = FormTemplate.builder()
                .id(10L)
                .name("Form X")
                .slug("form-x")
                .client(client)
                .hasSchedule(false)
                .hasAttendance(false)
                .fields(new ArrayList<>())
                .build();
    }

    // ─── getSummary (Admin) ───────────────────────────────────────────────────

    @Test
    @DisplayName("getSummary: retorna estatísticas globais para admin")
    void getSummary_admin_success() {
        PageRequest pageable = PageRequest.of(0, 10);

        when(templateRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(template)));
        when(clientRepository.count()).thenReturn(5L);

        // Estatísticas por template
        when(submissionRepository.countByTemplate_Id(10L)).thenReturn(3L);
        when(appointmentRepository.countByFormTemplate(template)).thenReturn(2L);
        when(appointmentRepository.countByFormTemplateAndStatus(template, AppointmentStatus.AGENDADO)).thenReturn(1L);
        when(appointmentRepository.countByFormTemplateAndStatus(template, AppointmentStatus.CANCELADO)).thenReturn(1L);
        when(attendanceRecordRepository.countByFormTemplate(template)).thenReturn(0L);
        when(attendanceRecordRepository.countByFormTemplateAndAttended(template, true)).thenReturn(0L);

        // Contagens globais de tipo de template
        when(templateRepository.countByHasScheduleFalseAndHasAttendanceFalse()).thenReturn(1L);
        when(templateRepository.countByHasScheduleTrue()).thenReturn(0L);
        when(templateRepository.countByHasScheduleFalseAndHasAttendanceTrue()).thenReturn(0L);

        // Totais globais
        when(submissionRepository.count()).thenReturn(10L);
        when(appointmentRepository.count()).thenReturn(5L);
        when(appointmentRepository.countByStatus(AppointmentStatus.AGENDADO)).thenReturn(3L);
        when(appointmentRepository.countByStatus(AppointmentStatus.CANCELADO)).thenReturn(2L);
        when(attendanceRecordRepository.count()).thenReturn(20L);
        when(attendanceRecordRepository.countByAttended(true)).thenReturn(15L);

        DashboardResponse response = service.getSummary(pageable);

        assertThat(response.totalTemplates()).isEqualTo(1);
        assertThat(response.totalClients()).isEqualTo(5);
        assertThat(response.globalTotalSubmissions()).isEqualTo(10);
        assertThat(response.globalTotalAppointments()).isEqualTo(5);
        assertThat(response.globalTotalAttendanceRecords()).isEqualTo(20);
    }

    // ─── getSummaryForClient ──────────────────────────────────────────────────

    @Test
    @DisplayName("getSummaryForClient: retorna estatísticas do cliente")
    void getSummaryForClient_success() {
        PageRequest pageable = PageRequest.of(0, 10);

        when(clientRepository.findByUsername("cliente_teste")).thenReturn(Optional.of(client));
        when(templateRepository.findByClient(client, pageable))
                .thenReturn(new PageImpl<>(List.of(template)));

        // Estatísticas por template
        when(submissionRepository.countByTemplate_Id(10L)).thenReturn(1L);
        when(appointmentRepository.countByFormTemplate(template)).thenReturn(0L);
        when(appointmentRepository.countByFormTemplateAndStatus(template, AppointmentStatus.AGENDADO)).thenReturn(0L);
        when(appointmentRepository.countByFormTemplateAndStatus(template, AppointmentStatus.CANCELADO)).thenReturn(0L);
        when(attendanceRecordRepository.countByFormTemplate(template)).thenReturn(0L);
        when(attendanceRecordRepository.countByFormTemplateAndAttended(template, true)).thenReturn(0L);

        // Contagens de tipo por cliente
        when(templateRepository.countByClientAndHasScheduleFalseAndHasAttendanceFalse(client)).thenReturn(1L);
        when(templateRepository.countByClientAndHasScheduleTrue(client)).thenReturn(0L);
        when(templateRepository.countByClientAndHasScheduleFalseAndHasAttendanceTrue(client)).thenReturn(0L);

        // Totais globais do cliente
        when(submissionRepository.countByTemplate_Client(client)).thenReturn(1L);
        when(appointmentRepository.countByFormTemplate_Client(client)).thenReturn(0L);
        when(appointmentRepository.countByFormTemplate_ClientAndStatus(client, AppointmentStatus.AGENDADO)).thenReturn(0L);
        when(appointmentRepository.countByFormTemplate_ClientAndStatus(client, AppointmentStatus.CANCELADO)).thenReturn(0L);
        when(attendanceRecordRepository.countByFormTemplate_Client(client)).thenReturn(0L);
        when(attendanceRecordRepository.countByFormTemplate_ClientAndAttended(client, true)).thenReturn(0L);

        DashboardResponse response = service.getSummaryForClient("cliente_teste", pageable);

        assertThat(response.totalTemplates()).isEqualTo(1);
        assertThat(response.globalTotalSubmissions()).isEqualTo(1);
    }

    @Test
    @DisplayName("getSummaryForClient: lança exceção se cliente não encontrado")
    void getSummaryForClient_clientNotFound_throws() {
        when(clientRepository.findByUsername("naoexiste")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSummaryForClient("naoexiste", PageRequest.of(0, 10)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("não encontrado");
    }
}
