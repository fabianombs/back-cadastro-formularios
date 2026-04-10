package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.dto.request.ClientRequest;
import com.cadastro.fabiano.demo.dto.response.ClientResponse;
import com.cadastro.fabiano.demo.config.JwtService;
import com.cadastro.fabiano.demo.service.ClientService;
import com.cadastro.fabiano.demo.service.CustomUserDetailsService;
import com.cadastro.fabiano.demo.service.FormTemplateService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = ClientController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class ClientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ClientService clientService;

    @MockBean
    private FormTemplateService formTemplateService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("POST /clients: cria cliente")
    void create_success() throws Exception {
        ClientRequest request = new ClientRequest(
                "Empresa XYZ", "xyz@email.com", "11999990000", "Empresa", null, "empresa_xyz");

        ClientResponse response = new ClientResponse(1L, "Empresa XYZ", "xyz@email.com", null, "Empresa", null);
        when(clientService.createClient(any())).thenReturn(response);

        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Empresa XYZ"));
    }

    @Test
    @DisplayName("GET /clients: lista clientes paginados")
    void findAll_success() throws Exception {
        when(clientService.findAll(any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/clients"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /clients/{id}: retorna cliente por ID")
    void findById_success() throws Exception {
        ClientResponse response = new ClientResponse(1L, "Empresa", "e@e.com", null, null, null);
        when(clientService.findById(1L)).thenReturn(response);

        mockMvc.perform(get("/clients/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("GET /clients/{id}/templates: retorna templates do cliente")
    void getTemplatesByClient_success() throws Exception {
        when(formTemplateService.findTemplatesByClientId(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/clients/1/templates"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /clients/{id}: exclui cliente")
    void delete_success() throws Exception {
        mockMvc.perform(delete("/clients/1"))
                .andExpect(status().isOk());
    }
}
