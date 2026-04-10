package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.dto.request.BookAppointmentRequest;
import com.cadastro.fabiano.demo.dto.response.AppointmentResponse;
import com.cadastro.fabiano.demo.dto.response.AvailableSlotsResponse;
import com.cadastro.fabiano.demo.entity.AppointmentStatus;
import com.cadastro.fabiano.demo.config.JwtService;
import com.cadastro.fabiano.demo.service.AppointmentService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = AppointmentController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class AppointmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AppointmentService appointmentService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("GET /appointments/template/{id}/slots: retorna slots do dia")
    void getSlots_success() throws Exception {
        LocalDate date = LocalDate.now().plusDays(1);
        AvailableSlotsResponse response = new AvailableSlotsResponse(date,
                List.of(new AvailableSlotsResponse.SlotInfo(LocalTime.of(8, 0), true, 0, 2)));

        when(appointmentService.getAvailableSlots(eq(1L), any())).thenReturn(response);

        mockMvc.perform(get("/appointments/template/1/slots")
                        .param("date", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[0].available").value(true));
    }

    @Test
    @DisplayName("GET /appointments/template/{id}/slots/range: retorna slots de múltiplos dias")
    void getSlotsRange_success() throws Exception {
        LocalDate from = LocalDate.now().plusDays(1);
        LocalDate to = from.plusDays(1);

        when(appointmentService.getAvailableSlotsRange(eq(1L), any(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/appointments/template/1/slots/range")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /appointments/book: cria agendamento")
    void book_success() throws Exception {
        BookAppointmentRequest request = new BookAppointmentRequest(
                1L, LocalDate.now().plusDays(1), LocalTime.of(8, 0),
                "João", "11999990000", Map.of());

        AppointmentResponse resp = new AppointmentResponse(
                10L, 1L, "Form", LocalDate.now().plusDays(1), LocalTime.of(8, 0),
                AppointmentStatus.AGENDADO, "João", "11999990000",
                null, null, Map.of(), LocalDateTime.now());

        when(appointmentService.book(any())).thenReturn(resp);

        mockMvc.perform(post("/appointments/book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    @DisplayName("GET /appointments/template/{id}: lista agendamentos paginados")
    void getByTemplate_success() throws Exception {
        when(appointmentService.getByTemplate(eq(1L), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/appointments/template/1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /appointments/{id}: exclui agendamento")
    void delete_success() throws Exception {
        doNothing().when(appointmentService).deleteAppointment(1L);

        mockMvc.perform(delete("/appointments/1"))
                .andExpect(status().isNoContent());
    }
}
