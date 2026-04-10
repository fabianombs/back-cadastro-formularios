package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.config.JwtService;
import com.cadastro.fabiano.demo.service.CustomUserDetailsService;
import com.cadastro.fabiano.demo.service.ImageStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = ImageUploadController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class ImageUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ImageStorageService imageStorageService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("POST /uploads/image: faz upload de imagem com sucesso")
    void uploadImage_success() throws Exception {
        when(imageStorageService.store(any())).thenReturn("http://localhost:8080/files/img.jpg");

        MockMultipartFile file = new MockMultipartFile(
                "file", "img.jpg", "image/jpeg", "fake-image-bytes".getBytes());

        mockMvc.perform(multipart("/uploads/image").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("http://localhost:8080/files/img.jpg"));
    }

    @Test
    @DisplayName("POST /uploads/image: retorna 400 quando upload falha")
    void uploadImage_error() throws Exception {
        when(imageStorageService.store(any())).thenThrow(new RuntimeException("Tipo não permitido"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "img.gif", "image/gif", "fake".getBytes());

        mockMvc.perform(multipart("/uploads/image").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Tipo não permitido"));
    }

    @Test
    @DisplayName("DELETE /uploads/image: remove imagem")
    void deleteImage_success() throws Exception {
        doNothing().when(imageStorageService).delete(any());

        mockMvc.perform(delete("/uploads/image")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("url", "http://localhost:8080/files/img.jpg"))))
                .andExpect(status().isNoContent());
    }
}
