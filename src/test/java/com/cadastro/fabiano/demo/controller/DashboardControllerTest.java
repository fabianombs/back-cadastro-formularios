package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.config.JwtService;
import com.cadastro.fabiano.demo.dto.response.DashboardResponse;
import com.cadastro.fabiano.demo.service.CustomUserDetailsService;
import com.cadastro.fabiano.demo.service.DashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private DashboardResponse emptyDashboard() {
        return new DashboardResponse(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(), 0, 10, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Test
    @DisplayName("GET /dashboard: retorna resumo para ADMIN")
    @WithMockUser(roles = "ADMIN")
    void getSummary_admin_success() throws Exception {
        when(dashboardService.getSummary(any())).thenReturn(emptyDashboard());

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTemplates").value(0));
    }

    @Test
    @DisplayName("GET /dashboard: retorna resumo para CLIENT")
    @WithMockUser(username = "cliente", roles = "CLIENT")
    void getSummary_client_success() throws Exception {
        when(dashboardService.getSummaryForClient(any(), any())).thenReturn(emptyDashboard());

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk());
    }
}
