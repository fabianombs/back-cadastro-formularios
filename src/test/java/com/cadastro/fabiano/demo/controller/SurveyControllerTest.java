package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.config.MetricasDeNegocio;
import com.cadastro.fabiano.demo.config.JwtService;
import com.cadastro.fabiano.demo.dto.request.CreateSurveyRequest;
import com.cadastro.fabiano.demo.dto.request.SubmitSurveyRequest;
import com.cadastro.fabiano.demo.dto.response.SurveyConfigResponse;
import com.cadastro.fabiano.demo.dto.response.SurveyReportResponse;
import com.cadastro.fabiano.demo.service.CustomUserDetailsService;
import com.cadastro.fabiano.demo.service.SurveyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = SurveyController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class SurveyControllerTest {

    // O GlobalExceptionHandler e @RestControllerAdvice e entra na fatia
    // do @WebMvcTest. Desde o FABIANO-25 ele depende de MetricasDeNegocio:
    // sem este bean o contexto do teste nem sobe.
    @MockitoBean
    private MetricasDeNegocio metricasDeNegocio;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private SurveyService surveyService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    private SurveyConfigResponse buildResponse() {
        return new SurveyConfigResponse(
                1L, "NPS", "nps", "Empresa X", null,
                "Como foi?", "Satisfação?", false, "Obrigado!",
                true,
                "http://localhost:4200/survey/nps",
                "http://localhost:4200/survey/nps/report",
                LocalDateTime.now(), LocalDateTime.now(),
                null, null, null, null, null, null, null, null, null,
                50.0, 12.0, 120, 50.0, 55.0,
                true, true, true,
                null, null, null, null, null,
                "Muito Satisfeito", "Satisfeito", "Regular", "Insatisfeito", "Muito Insatisfeito",
                "Sua opinião é muito importante!", "Avaliação registrada com sucesso.",
                "Começar", "Enviar avaliação"
        );
    }

    // ── listAll ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /surveys: lista pesquisas")
    void listAll_success() throws Exception {
        when(surveyService.listAll()).thenReturn(List.of(buildResponse()));

        mockMvc.perform(get("/surveys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("nps"));
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /surveys: cria nova pesquisa")
    void create_success() throws Exception {
        CreateSurveyRequest req = new CreateSurveyRequest("NPS", "nps",
                "Empresa X", null, "Como foi?", "Satisfação?", false, "Obrigado!",
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null,
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null);
        when(surveyService.create(any())).thenReturn(buildResponse());

        mockMvc.perform(post("/surveys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("NPS"));
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /surveys/{id}: retorna pesquisa pelo ID")
    void getById_success() throws Exception {
        when(surveyService.getById(1L)).thenReturn(buildResponse());

        mockMvc.perform(get("/surveys/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /surveys/{id}: atualiza pesquisa")
    void update_success() throws Exception {
        CreateSurveyRequest req = new CreateSurveyRequest("NPS Atualizado", "nps",
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null,
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null);
        when(surveyService.update(eq(1L), any())).thenReturn(buildResponse());

        mockMvc.perform(put("/surveys/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /surveys/{id}: exclui pesquisa")
    void delete_success() throws Exception {
        doNothing().when(surveyService).delete(1L);

        mockMvc.perform(delete("/surveys/1"))
                .andExpect(status().isNoContent());
    }

    // ── toggleActive ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /surveys/{id}/toggle: alterna estado")
    void toggleActive_success() throws Exception {
        when(surveyService.toggleActive(1L)).thenReturn(buildResponse());

        mockMvc.perform(patch("/surveys/1/toggle"))
                .andExpect(status().isOk());
    }

    // ── getReport ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /surveys/{id}/report: retorna relatório")
    void getReport_success() throws Exception {
        SurveyReportResponse report = new SurveyReportResponse(
                1L, "NPS", 10L, 4.2,
                Map.of("Muito Satisfeito", 5L, "Satisfeito", 3L, "Regular", 2L),
                List.of()
        );
        when(surveyService.getReport(1L)).thenReturn(report);

        mockMvc.perform(get("/surveys/1/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalResponses").value(10))
                .andExpect(jsonPath("$.averageScore").value(4.2));
    }

    // ── público ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /surveys/slug/{slug}: retorna pesquisa pública")
    void getBySlug_success() throws Exception {
        when(surveyService.getBySlug("nps")).thenReturn(buildResponse());

        mockMvc.perform(get("/surveys/slug/nps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("nps"));
    }

    @Test
    @DisplayName("POST /surveys/slug/{slug}/responses: submete resposta")
    void submitResponse_success() throws Exception {
        SubmitSurveyRequest req = new SubmitSurveyRequest(5, "Ótimo!", null, null);
        doNothing().when(surveyService).submitResponse(eq("nps"), any());

        mockMvc.perform(post("/surveys/slug/nps/responses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }
}
