package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.dto.response.DashboardResponse;
import com.cadastro.fabiano.demo.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Estatísticas e resumo de uso da plataforma")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO', 'CLIENT')")
    @Operation(summary = "Obter resumo do dashboard",
            description = """
                    Retorna estatísticas paginadas de templates, submissões, agendamentos e presença.
                    - **ADMIN/FUNCIONARIO**: visão global de todos os clientes
                    - **CLIENT**: visão restrita aos próprios templates
                    """)
    public ResponseEntity<DashboardResponse> getSummary(
            Authentication authentication,
            Pageable pageable) {

        boolean isAdminOrFuncionario = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_FUNCIONARIO"));

        DashboardResponse response = isAdminOrFuncionario
                ? dashboardService.getSummary(pageable)
                : dashboardService.getSummaryForClient(authentication.getName(), pageable);

        return ResponseEntity.ok(response);
    }
}
