package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.dto.response.AppointmentResponse;
import com.cadastro.fabiano.demo.dto.response.AttendanceRecordResponse;
import com.cadastro.fabiano.demo.dto.response.FormSubmissionResponse;
import com.cadastro.fabiano.demo.dto.response.PaginaResponse;
import com.cadastro.fabiano.demo.service.AppointmentService;
import com.cadastro.fabiano.demo.service.AttendanceService;
import com.cadastro.fabiano.demo.service.FormSubmissionService;
import com.cadastro.fabiano.demo.service.FormTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Endpoints públicos de dados — acessados pelo viewToken do cliente, sem JWT
@RestController
@RequestMapping("/form-templates/view/{viewToken}")
@Tag(name = "Visualização Pública", description = "Dados acessíveis via link do cliente (sem autenticação)")
public class PublicViewDataController {

    private final FormTemplateService templateService;
    private final FormSubmissionService submissionService;
    private final AttendanceService attendanceService;
    private final AppointmentService appointmentService;

    public PublicViewDataController(
            FormTemplateService templateService,
            FormSubmissionService submissionService,
            AttendanceService attendanceService,
            AppointmentService appointmentService) {
        this.templateService = templateService;
        this.submissionService = submissionService;
        this.attendanceService = attendanceService;
        this.appointmentService = appointmentService;
    }

    @GetMapping("/submissions")
    @SecurityRequirements
    @Operation(summary = "Respostas por viewToken", description = "Retorna submissões do template sem exigir JWT")
    public ResponseEntity<PaginaResponse<FormSubmissionResponse>> getSubmissions(
            @PathVariable String viewToken,
            Pageable pageable) {

        // Resolve o viewToken para o ID do template e busca as submissões
        Long templateId = templateService.findByViewToken(viewToken).id();
        return ResponseEntity.ok(PaginaResponse.de(
                submissionService.getSubmissionsByTemplate(templateId, pageable)));
    }

    @GetMapping("/attendance")
    @SecurityRequirements
    @Operation(summary = "Presença por viewToken", description = "Retorna registros de presença sem exigir JWT")
    public ResponseEntity<PaginaResponse<AttendanceRecordResponse>> getAttendance(
            @PathVariable String viewToken,
            Pageable pageable) {

        Long templateId = templateService.findByViewToken(viewToken).id();
        return ResponseEntity.ok(PaginaResponse.de(
                attendanceService.getByTemplate(templateId, pageable)));
    }

    @GetMapping("/appointments")
    @SecurityRequirements
    @Operation(summary = "Agendamentos por viewToken", description = "Retorna agendamentos do template sem exigir JWT")
    public ResponseEntity<PaginaResponse<AppointmentResponse>> getAppointments(
            @PathVariable String viewToken,
            Pageable pageable) {

        Long templateId = templateService.findByViewToken(viewToken).id();
        return ResponseEntity.ok(PaginaResponse.de(
                appointmentService.getByTemplate(templateId, pageable)));
    }
}
