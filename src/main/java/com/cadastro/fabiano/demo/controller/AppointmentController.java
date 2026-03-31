package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.dto.request.BookAppointmentRequest;
import com.cadastro.fabiano.demo.dto.response.AppointmentResponse;
import com.cadastro.fabiano.demo.dto.response.AvailableSlotsResponse;
import com.cadastro.fabiano.demo.service.AppointmentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    // Qualquer pessoa pode ver os slots disponíveis de um dia
    @GetMapping("/template/{templateId}/slots")
    public ResponseEntity<AvailableSlotsResponse> getSlots(
            @PathVariable Long templateId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return ResponseEntity.ok(appointmentService.getAvailableSlots(templateId, date));
    }

    // Qualquer pessoa pode ver slots de um range de datas
    @GetMapping("/template/{templateId}/slots/range")
    public ResponseEntity<List<AvailableSlotsResponse>> getSlotsRange(
            @PathVariable Long templateId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return ResponseEntity.ok(appointmentService.getAvailableSlotsRange(templateId, from, to));
    }

    // Qualquer pessoa pode agendar (form público)
    @PostMapping("/book")
    public ResponseEntity<AppointmentResponse> book(@RequestBody BookAppointmentRequest request) {
        return ResponseEntity.ok(appointmentService.book(request));
    }

    // ROLE_CLIENT ou ADMIN pode cancelar
    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN', 'FUNCIONARIO')")
    public ResponseEntity<AppointmentResponse> cancel(
            @PathVariable Long id,
            Authentication authentication) {

        String username = authentication.getName();
        return ResponseEntity.ok(appointmentService.cancel(id, username));
    }

    // Admin/funcionário vê todos os agendamentos do template
    @GetMapping("/template/{templateId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO', 'CLIENT')")
    public ResponseEntity<Page<AppointmentResponse>> getByTemplate(
            @PathVariable Long templateId,
            Pageable pageable) {
        return ResponseEntity.ok(appointmentService.getByTemplate(templateId, pageable));
    }

    // Admin ou client pode excluir um agendamento permanentemente
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENT', 'FUNCIONARIO')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        appointmentService.deleteAppointment(id);
        return ResponseEntity.noContent().build();
    }
}
