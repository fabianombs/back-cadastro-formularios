package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.dto.request.CreateFormTemplateRequest;
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
                List.of(), false, false, null, null);
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
                "Novo Form", 1L, List.of(), null, null);
        when(templateService.createTemplate(any(), eq(1L))).thenReturn(buildResponse());

        mockMvc.perform(post("/form-templates/create/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
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
        UpdateFormTemplateRequest request = new UpdateFormTemplateRequest("Novo Nome", null, null);
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
