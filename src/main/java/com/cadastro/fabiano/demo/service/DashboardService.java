package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.dto.response.DashboardResponse;
import com.cadastro.fabiano.demo.dto.response.TemplateStatResponse;
import com.cadastro.fabiano.demo.entity.AppointmentStatus;
import com.cadastro.fabiano.demo.entity.Client;
import com.cadastro.fabiano.demo.entity.FormTemplate;
import com.cadastro.fabiano.demo.entity.QuizConfig;
import com.cadastro.fabiano.demo.repository.AppointmentRepository;
import com.cadastro.fabiano.demo.repository.AttendanceRecordRepository;
import com.cadastro.fabiano.demo.repository.ClientRepository;
import com.cadastro.fabiano.demo.repository.FormSubmissionRepository;
import com.cadastro.fabiano.demo.repository.FormTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final FormTemplateRepository templateRepository;
    private final FormSubmissionRepository submissionRepository;
    private final AppointmentRepository appointmentRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final ClientRepository clientRepository;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // URL do frontend — links públicos do quiz apontam para o Angular, não para a API
    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    // Admin: vê todos os templates e todos os clientes
    // O dashboard percorre os templates contando campos, quiz e survey - todos
    // LAZY. Com open-in-view=false (FABIANO-37) isso precisa acontecer dentro da
    // transacao, senao a contagem estoura LazyInitializationException.
    @Transactional(readOnly = true)
    public DashboardResponse getSummary(Pageable pageable) {
        Page<FormTemplate> page = templateRepository.findAll(pageable);
        long totalClients = clientRepository.count();

        return buildResponse(page, totalClients, true);
    }

    // Client: vê apenas os templates do seu cliente
    @Transactional(readOnly = true)
    public DashboardResponse getSummaryForClient(String username, Pageable pageable) {
        Client client = clientRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        Page<FormTemplate> page = templateRepository.findByClient(client, pageable);

        return buildResponse(page, page.getTotalElements(), false, client);
    }

    private DashboardResponse buildResponse(Page<FormTemplate> page, long totalClients, boolean isAdmin) {
        return buildResponse(page, totalClients, isAdmin, null);
    }

    private DashboardResponse buildResponse(Page<FormTemplate> page, long totalClients, boolean isAdmin, Client client) {
        List<FormTemplate> templates = page.getContent();
        List<Long> templateIds = templates.stream().map(FormTemplate::getId).toList();

        // Tres consultas agregadas no lugar de seis por template. Antes, uma pagina
        // de 20 templates custava 120 idas ao banco so para montar os numeros do
        // painel - o custo dominante desta rota (FABIANO-38).
        // O guarda de lista vazia existe porque IN () e SQL invalido.
        Map<Long, Long> submissoesPorTemplate = templateIds.isEmpty() ? Map.of()
                : submissionRepository.countGroupedByTemplateIds(templateIds).stream()
                        .collect(Collectors.toMap(
                                FormSubmissionRepository.SubmissionCountByTemplate::getTemplateId,
                                FormSubmissionRepository.SubmissionCountByTemplate::getTotal));

        Map<Long, AppointmentRepository.AppointmentCountByTemplate> agendamentosPorTemplate =
                templateIds.isEmpty() ? Map.of()
                : appointmentRepository.countGroupedByTemplateIds(
                                templateIds, AppointmentStatus.AGENDADO, AppointmentStatus.CANCELADO).stream()
                        .collect(Collectors.toMap(
                                AppointmentRepository.AppointmentCountByTemplate::getTemplateId, r -> r));

        Map<Long, AttendanceRecordRepository.AttendanceStatsByTemplate> presencasPorTemplate =
                templateIds.isEmpty() ? Map.of()
                : attendanceRecordRepository.countGroupedByTemplateIds(templateIds).stream()
                        .collect(Collectors.toMap(
                                AttendanceRecordRepository.AttendanceStatsByTemplate::getTemplateId, r -> r));

        List<TemplateStatResponse> templateStats = templates.stream().map(t -> {
            // Template sem nenhum registro nao aparece no GROUP BY: ausencia e zero.
            long submissions = submissoesPorTemplate.getOrDefault(t.getId(), 0L);

            AppointmentRepository.AppointmentCountByTemplate ag = agendamentosPorTemplate.get(t.getId());
            long apptTotal     = ag != null ? ag.getTotal() : 0L;
            long apptConfirmed = ag != null ? ag.getConfirmed() : 0L;
            long apptCancelled = ag != null ? ag.getCancelled() : 0L;

            AttendanceRecordRepository.AttendanceStatsByTemplate pr = presencasPorTemplate.get(t.getId());
            long attTotal   = pr != null ? pr.getTotal() : 0L;
            long attPresent = pr != null ? pr.getPresent() : 0L;

            String clientName = t.getClient() != null ? t.getClient().getName() : null;

            // Quiz associado diretamente ao template (nova arquitetura independente)
            QuizConfig quiz = t.getQuiz();
            boolean hasQuiz = quiz != null && quiz.isActive();
            String quizLink    = hasQuiz ? frontendUrl + "/quiz/" + quiz.getSlug() : null;
            String rankingLink = hasQuiz ? frontendUrl + "/quiz/" + quiz.getSlug() + "/ranking" : null;

            return new TemplateStatResponse(
                    t.getId(), t.getName(), t.getSlug(), clientName,
                    t.isHasSchedule(), t.getFields().size(), submissions,
                    apptTotal, apptConfirmed, apptCancelled,
                    attTotal, attPresent,
                    hasQuiz, quizLink, rankingLink
            );
        }).toList();

        long totalSubmissions      = templateStats.stream().mapToLong(TemplateStatResponse::submissionCount).sum();
        long totalAppointments     = templateStats.stream().mapToLong(TemplateStatResponse::appointmentTotal).sum();
        long confirmedAppointments = templateStats.stream().mapToLong(TemplateStatResponse::appointmentConfirmed).sum();
        long cancelledAppointments = templateStats.stream().mapToLong(TemplateStatResponse::appointmentCancelled).sum();
        long totalAttendance       = templateStats.stream().mapToLong(TemplateStatResponse::attendanceTotal).sum();
        long presentAttendance     = templateStats.stream().mapToLong(TemplateStatResponse::attendancePresent).sum();

        long formTemplateCount;
        long appointmentTemplateCount;
        long attendanceTemplateCount;

        long globalTotalSubmissions = 0;
        long globalTotalAppointments = 0;
        long globalConfirmedAppointments = 0;
        long globalCancelledAppointments = 0;
        long globalTotalAttendanceRecords = 0;
        long globalPresentAttendanceRecords = 0;

        if (isAdmin) {
            formTemplateCount = templateRepository.countByHasScheduleFalseAndHasAttendanceFalse();
            appointmentTemplateCount = templateRepository.countByHasScheduleTrue();
            attendanceTemplateCount = templateRepository.countByHasScheduleFalseAndHasAttendanceTrue();

            globalTotalSubmissions = submissionRepository.count();
            globalTotalAppointments = appointmentRepository.count();
            globalConfirmedAppointments = appointmentRepository.countByStatus(AppointmentStatus.AGENDADO);
            globalCancelledAppointments = appointmentRepository.countByStatus(AppointmentStatus.CANCELADO);
            globalTotalAttendanceRecords = attendanceRecordRepository.count();
            globalPresentAttendanceRecords = attendanceRecordRepository.countByAttended(true);
        } else {
            formTemplateCount = templateRepository.countByClientAndHasScheduleFalseAndHasAttendanceFalse(client);
            appointmentTemplateCount = templateRepository.countByClientAndHasScheduleTrue(client);
            attendanceTemplateCount = templateRepository.countByClientAndHasScheduleFalseAndHasAttendanceTrue(client);

            globalTotalSubmissions = submissionRepository.countByTemplate_Client(client);
            globalTotalAppointments = appointmentRepository.countByFormTemplate_Client(client);
            globalConfirmedAppointments = appointmentRepository.countByFormTemplate_ClientAndStatus(client, AppointmentStatus.AGENDADO);
            globalCancelledAppointments = appointmentRepository.countByFormTemplate_ClientAndStatus(client, AppointmentStatus.CANCELADO);
            globalTotalAttendanceRecords = attendanceRecordRepository.countByFormTemplate_Client(client);
            globalPresentAttendanceRecords = attendanceRecordRepository.countByFormTemplate_ClientAndAttended(client, true);
        }

        return new DashboardResponse(
                page.getTotalElements(), totalClients,
                totalSubmissions, totalAppointments, confirmedAppointments, cancelledAppointments,
                totalAttendance, presentAttendance,
                formTemplateCount, appointmentTemplateCount, attendanceTemplateCount,
                templateStats,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                globalTotalSubmissions, globalTotalAppointments, globalConfirmedAppointments,
                globalCancelledAppointments, globalTotalAttendanceRecords, globalPresentAttendanceRecords
        );
    }
}
