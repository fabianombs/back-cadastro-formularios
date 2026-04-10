package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.dto.request.BookAppointmentRequest;
import com.cadastro.fabiano.demo.dto.response.AppointmentResponse;
import com.cadastro.fabiano.demo.dto.response.AvailableSlotsResponse;
import com.cadastro.fabiano.demo.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/appointments")
@Tag(name = "Agendamentos", description = "Consulta de slots disponíveis e realização de agendamentos (endpoints públicos)")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping("/template/{templateId}/slots")
    @SecurityRequirements
    @Operation(summary = "Slots disponíveis por dia",
            description = "Retorna todos os horários do dia com quantidade disponível e total de vagas. Endpoint público")
    @ApiResponse(responseCode = "200", description = "Slots retornados com sucesso")
    public ResponseEntity<AvailableSlotsResponse> getSlots(
            @PathVariable Long templateId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return ResponseEntity.ok(appointmentService.getAvailableSlots(templateId, date));
    }

    @GetMapping("/template/{templateId}/slots/range")
    @SecurityRequirements
    @Operation(summary = "Slots disponíveis em intervalo de datas",
            description = "Retorna slots de múltiplos dias de uma vez. Endpoint público")
    public ResponseEntity<List<AvailableSlotsResponse>> getSlotsRange(
            @PathVariable Long templateId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return ResponseEntity.ok(appointmentService.getAvailableSlotsRange(templateId, from, to));
    }

    @PostMapping("/book")
    @SecurityRequirements
    @Operation(summary = "Realizar agendamento",
            description = "Cria um agendamento para o slot informado. Verifica capacidade e deduplicação. Endpoint público")
    @ApiResponse(responseCode = "200", description = "Agendamento realizado com sucesso")
    @ApiResponse(responseCode = "409", description = "Slot lotado ou agendamento duplicado")
    public ResponseEntity<AppointmentResponse> book(@RequestBody BookAppointmentRequest request) {
        return ResponseEntity.ok(appointmentService.book(request));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancelar agendamento", description = "Marca o agendamento como CANCELADO")
    @ApiResponse(responseCode = "200", description = "Cancelado com sucesso")
    @ApiResponse(responseCode = "400", description = "Agendamento já cancelado ou não encontrado")
    public ResponseEntity<AppointmentResponse> cancel(
            @PathVariable Long id,
            Authentication authentication) {

        String username = authentication.getName();
        return ResponseEntity.ok(appointmentService.cancel(id, username));
    }

    @GetMapping("/template/{templateId}")
    @Operation(summary = "Listar agendamentos do template", description = "Retorna todos os agendamentos de um template paginados")
    public ResponseEntity<Page<AppointmentResponse>> getByTemplate(
            @PathVariable Long templateId,
            Pageable pageable) {
        return ResponseEntity.ok(appointmentService.getByTemplate(templateId, pageable));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir agendamento", description = "Remove permanentemente o agendamento")
    @ApiResponse(responseCode = "204", description = "Excluído com sucesso")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        appointmentService.deleteAppointment(id);
        return ResponseEntity.noContent().build();
    }
}
