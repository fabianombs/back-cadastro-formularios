package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.dto.response.DashboardResponse;
import com.cadastro.fabiano.demo.dto.response.TemplateStatResponse;
import com.cadastro.fabiano.demo.entity.AppointmentStatus;
import com.cadastro.fabiano.demo.entity.Client;
import com.cadastro.fabiano.demo.entity.FormTemplate;
import com.cadastro.fabiano.demo.repository.AppointmentRepository;
import com.cadastro.fabiano.demo.repository.AttendanceRecordRepository;
import com.cadastro.fabiano.demo.repository.ClientRepository;
import com.cadastro.fabiano.demo.repository.FormSubmissionRepository;
import com.cadastro.fabiano.demo.repository.FormTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final FormTemplateRepository templateRepository;
    private final FormSubmissionRepository submissionRepository;
    private final AppointmentRepository appointmentRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final ClientRepository clientRepository;

    // Admin: vê todos os templates e todos os clientes
    public DashboardResponse getSummary(Pageable pageable) {
        Page<FormTemplate> page = templateRepository.findAll(pageable);
        long totalClients = clientRepository.count();

        return buildResponse(page, totalClients);
    }

    // Client: vê apenas os templates do seu cliente
    public DashboardResponse getSummaryForClient(String username, Pageable pageable) {
        Client client = clientRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        Page<FormTemplate> page = templateRepository.findByClient(client, pageable);

        return buildResponse(page, page.getTotalElements());
    }

    private DashboardResponse buildResponse(Page<FormTemplate> page, long totalClients) {
        List<FormTemplate> templates = page.getContent();
        List<TemplateStatResponse> templateStats = templates.stream().map(t -> {
            long submissions = submissionRepository.countByTemplate_Id(t.getId());
            long apptTotal = appointmentRepository.countByFormTemplate(t);
            long apptConfirmed = appointmentRepository.countByFormTemplateAndStatus(t, AppointmentStatus.AGENDADO);
            long apptCancelled = appointmentRepository.countByFormTemplateAndStatus(t, AppointmentStatus.CANCELADO);
            long attTotal = attendanceRecordRepository.countByFormTemplate(t);
            long attPresent = attendanceRecordRepository.countByFormTemplateAndAttended(t, true);
            String clientName = t.getClient() != null ? t.getClient().getName() : null;

            return new TemplateStatResponse(
                    t.getId(), t.getName(), t.getSlug(), clientName,
                    t.isHasSchedule(), submissions,
                    apptTotal, apptConfirmed, apptCancelled,
                    attTotal, attPresent
            );
        }).toList();

        long totalSubmissions      = templateStats.stream().mapToLong(TemplateStatResponse::submissionCount).sum();
        long totalAppointments     = templateStats.stream().mapToLong(TemplateStatResponse::appointmentTotal).sum();
        long confirmedAppointments = templateStats.stream().mapToLong(TemplateStatResponse::appointmentConfirmed).sum();
        long cancelledAppointments = templateStats.stream().mapToLong(TemplateStatResponse::appointmentCancelled).sum();
        long totalAttendance       = templateStats.stream().mapToLong(TemplateStatResponse::attendanceTotal).sum();
        long presentAttendance     = templateStats.stream().mapToLong(TemplateStatResponse::attendancePresent).sum();

        return new DashboardResponse(
                page.getTotalElements(), totalClients,
                totalSubmissions, totalAppointments, confirmedAppointments, cancelledAppointments,
                totalAttendance, presentAttendance,
                templateStats,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
