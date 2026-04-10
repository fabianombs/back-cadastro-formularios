package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.config.JwtService;
import com.cadastro.fabiano.demo.dto.request.CreateFormSubmissionRequest;
import com.cadastro.fabiano.demo.dto.response.FormSubmissionResponse;
import com.cadastro.fabiano.demo.service.CustomUserDetailsService;
import com.cadastro.fabiano.demo.service.FormSubmissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
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
        value = FormSubmissionController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class FormSubmissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FormSubmissionService submissionService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private FormSubmissionResponse buildResponse() {
        return new FormSubmissionResponse(1L, 10L, Map.of("Nome", "João"), LocalDateTime.now());
    }

    @Test
    @DisplayName("POST /form-submissions: envia submissão")
    void submitForm_success() throws Exception {
        CreateFormSubmissionRequest request = new CreateFormSubmissionRequest(10L, Map.of("Nome", "João"));
        when(submissionService.submitForm(any())).thenReturn(buildResponse());

        mockMvc.perform(post("/form-submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("GET /form-submissions/template/{id}: lista submissões por template")
    void getByTemplate_success() throws Exception {
        when(submissionService.getSubmissionsByTemplate(eq(10L), any()))
                .thenReturn(new PageImpl<>(List.of(buildResponse())));

        mockMvc.perform(get("/form-submissions/template/10"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /form-submissions/slug/{slug}: lista submissões por slug")
    void getBySlug_success() throws Exception {
        when(submissionService.getSubmissionsBySlug(eq("form"), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/form-submissions/slug/form"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /form-submissions/{id}: exclui submissão")
    void delete_success() throws Exception {
        doNothing().when(submissionService).deleteSubmission(1L);

        mockMvc.perform(delete("/form-submissions/1"))
                .andExpect(status().isNoContent());
    }
}
