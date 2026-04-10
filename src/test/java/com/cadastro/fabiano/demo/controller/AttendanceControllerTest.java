package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.dto.request.ImportAttendanceRequest;
import com.cadastro.fabiano.demo.dto.request.MarkAttendanceRequest;
import com.cadastro.fabiano.demo.dto.response.AttendanceRecordResponse;
import com.cadastro.fabiano.demo.config.JwtService;
import com.cadastro.fabiano.demo.service.AttendanceService;
import com.cadastro.fabiano.demo.service.CustomUserDetailsService;
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
        value = AttendanceController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class AttendanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AttendanceService attendanceService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private AttendanceRecordResponse buildRecord(Long id) {
        return new AttendanceRecordResponse(id, 1L, Map.of("Nome", "João"),
                false, null, null, 1, LocalDateTime.now());
    }

    @Test
    @DisplayName("POST /attendance/template/{id}/import: importa lista")
    void importAttendance_success() throws Exception {
        ImportAttendanceRequest request = new ImportAttendanceRequest(
                List.of(Map.of("Nome", "João")));
        when(attendanceService.importAttendance(eq(1L), any()))
                .thenReturn(List.of(buildRecord(1L)));

        mockMvc.perform(post("/attendance/template/1/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    @DisplayName("GET /attendance/template/{id}: lista registros paginados")
    void getByTemplate_success() throws Exception {
        when(attendanceService.getByTemplate(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/attendance/template/1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /attendance/{id}/mark: marca presença")
    void markAttendance_success() throws Exception {
        MarkAttendanceRequest request = new MarkAttendanceRequest(true, "OK");
        when(attendanceService.markAttendance(eq(5L), any())).thenReturn(buildRecord(5L));

        mockMvc.perform(patch("/attendance/5/mark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    @DisplayName("PATCH /attendance/{id}/data: atualiza dados da linha")
    void updateRowData_success() throws Exception {
        Map<String, String> rowData = Map.of("Nome", "Maria");
        when(attendanceService.updateRowData(eq(5L), any())).thenReturn(buildRecord(5L));

        mockMvc.perform(patch("/attendance/5/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rowData)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /attendance/{id}: exclui registro")
    void deleteRecord_success() throws Exception {
        doNothing().when(attendanceService).deleteRecord(1L);

        mockMvc.perform(delete("/attendance/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /attendance/template/existence: verifica existência por template IDs")
    void getAttendanceExistence_success() throws Exception {
        when(attendanceService.attendanceExistsForTemplates(any()))
                .thenReturn(Map.of(1L, true, 2L, false));

        mockMvc.perform(get("/attendance/template/existence")
                        .param("templateIds", "1", "2"))
                .andExpect(status().isOk());
    }
}
