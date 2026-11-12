package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.config.MetricasDeNegocio;
import com.cadastro.fabiano.demo.dto.request.CreateFormTemplateRequest;
import com.cadastro.fabiano.demo.dto.request.DuplicateFormTemplateRequest;
import com.cadastro.fabiano.demo.dto.request.ScheduleConfigRequest;
import com.cadastro.fabiano.demo.dto.request.UpdateFormTemplateRequest;
import com.cadastro.fabiano.demo.dto.response.FormTemplateResponse;
import com.cadastro.fabiano.demo.config.JwtService;
import com.cadastro.fabiano.demo.service.CustomUserDetailsService;
import com.cadastro.fabiano.demo.service.FormTemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = FormTemplateController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class FormTemplateControllerTest {

    // O GlobalExceptionHandler e @RestControllerAdvice e entra na fatia
    // do @WebMvcTest. Desde o FABIANO-25 ele depende de MetricasDeNegocio:
    // sem este bean o contexto do teste nem sobe.
    @MockitoBean
    private MetricasDeNegocio metricasDeNegocio;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FormTemplateService templateService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private FormTemplateResponse buildResponse() {
        return new FormTemplateResponse(1L, "Form", "form", "Cliente", "Empresa XYZ",
                List.of(), false, false, null, null, null, false, null,
                false, null, null, null,
                false, null, null, null,
                // view config
                null, false, true, true, true, false,
                false, // viewAllowAddGuest
                // colunas da lista de presenca (5 flags)
                true, true, true, true, true,
                // survey
                false, null, null, null,
                "MEDIUM", null, null, null, null, null, null, null, null, null, null, true, true);
    }

    @Test
    @DisplayName("GET /form-templates/slug/{slug}: retorna template público")
    void getBySlug_success() throws Exception {
        when(templateService.findBySlug("form")).thenReturn(buildResponse());

        mockMvc.perform(get("/form-templates/slug/form"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("form"));
    }

    @Test
    @DisplayName("POST /form-templates/create/{clientId}: cria template")
    void createTemplate_success() throws Exception {
        CreateFormTemplateRequest request = new CreateFormTemplateRequest(
                "Novo Form", 1L, List.of(), null, null, false, null, false, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        when(templateService.createTemplate(any(), eq(1L))).thenReturn(buildResponse());

        mockMvc.perform(post("/form-templates/create/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /form-templates/{id}/duplicate: duplica template para o cliente informado")
    void duplicateTemplate_success() throws Exception {
        when(templateService.duplicateTemplate(eq(1L), eq(new DuplicateFormTemplateRequest(2L, "Cópia"))))
                .thenReturn(buildResponse());

        mockMvc.perform(post("/form-templates/1/duplicate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":2,\"name\":\"Cópia\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("POST /form-templates/{id}/duplicate: template inexistente retorna 400 com a mensagem")
    void duplicateTemplate_notFound() throws Exception {
        when(templateService.duplicateTemplate(eq(99L), any()))
                .thenThrow(new RuntimeException("Template não encontrado"));

        mockMvc.perform(post("/form-templates/99/duplicate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":2,\"name\":\"Cópia\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Template não encontrado"));
    }

    @Test
    @DisplayName("GET /form-templates/{id}/move-submissions/preview: devolve a prévia")
    void previewMoveSubmissions_success() throws Exception {
        when(templateService.previewMoveSubmissions(65L, 63L))
                .thenReturn(new com.cadastro.fabiano.demo.dto.response.MoveSubmissionsPreviewResponse(72, 182, List.of("Bairro")));

        mockMvc.perform(get("/form-templates/65/move-submissions/preview").param("targetId", "63"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCount").value(72))
                .andExpect(jsonPath("$.targetCount").value(182))
                .andExpect(jsonPath("$.missingLabels[0]").value("Bairro"));
    }

    @Test
    @DisplayName("POST /form-templates/{id}/move-submissions: move e devolve o total")
    void moveSubmissions_success() throws Exception {
        when(templateService.moveSubmissions(65L, 63L))
                .thenReturn(new com.cadastro.fabiano.demo.dto.response.MoveSubmissionsResponse(72));

        mockMvc.perform(post("/form-templates/65/move-submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetId\":63}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moved").value(72));
    }

    @Test
    @DisplayName("POST /form-templates/{id}/move-submissions: erro de regra vira 400 com a mensagem")
    void moveSubmissions_ruleError() throws Exception {
        when(templateService.moveSubmissions(65L, 70L))
                .thenThrow(new RuntimeException("Só é possível mover cadastros entre formulários do mesmo cliente"));

        mockMvc.perform(post("/form-templates/65/move-submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetId\":70}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Só é possível mover cadastros entre formulários do mesmo cliente"));
    }

    @Test
    @DisplayName("mover cadastros: os dois endpoints são só para ADMIN")
    void moveSubmissions_adminOnly() throws Exception {
        for (String metodo : List.of("previewMoveSubmissions", "moveSubmissions")) {
            java.lang.reflect.Method m = java.util.Arrays.stream(FormTemplateController.class.getMethods())
                    .filter(x -> x.getName().equals(metodo)).findFirst().orElseThrow();
            org.springframework.security.access.prepost.PreAuthorize pa =
                    m.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
            org.assertj.core.api.Assertions.assertThat(pa).as(metodo).isNotNull();
            org.assertj.core.api.Assertions.assertThat(pa.value()).as(metodo).isEqualTo("hasRole('ADMIN')");
        }
    }

    @Test
    @DisplayName("GET /form-templates: lista todos os templates")
    void getAllTemplates_success() throws Exception {
        when(templateService.findAllTemplates(any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/form-templates"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /form-templates/{id}: atualiza template")
    void updateTemplate_success() throws Exception {
        UpdateFormTemplateRequest request = new UpdateFormTemplateRequest("Novo Nome", null, null, false, null, false, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        when(templateService.updateTemplate(eq(1L), any())).thenReturn(buildResponse());

        mockMvc.perform(put("/form-templates/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /form-templates/{id}: exclui template")
    void deleteTemplate_success() throws Exception {
        doNothing().when(templateService).deleteTemplate(1L);

        mockMvc.perform(delete("/form-templates/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("PATCH /form-templates/{id}/schedule-config: atualiza agenda")
    void updateScheduleConfig_success() throws Exception {
        ScheduleConfigRequest req = new ScheduleConfigRequest(
                LocalTime.of(8, 0), LocalTime.of(17, 0), 30, 7, 2, List.of());
        when(templateService.updateScheduleConfig(eq(1L), any())).thenReturn(buildResponse());

        mockMvc.perform(patch("/form-templates/1/schedule-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }
}
