package com.cadastro.fabiano.demo.exception;

import com.cadastro.fabiano.demo.dto.response.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("handleDuplicate: retorna 409 para DuplicateBookingException")
    void handleDuplicate_returns409() {
        DuplicateBookingException ex = new DuplicateBookingException("Já cadastrado");

        ResponseEntity<ErrorResponse> response = handler.handleDuplicate(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Já cadastrado");
    }

    @Test
    @DisplayName("handleSlotFull: retorna 409 para SlotFullException")
    void handleSlotFull_returns409() {
        SlotFullException ex = new SlotFullException("Horário lotado");

        ResponseEntity<ErrorResponse> response = handler.handleSlotFull(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Horário lotado");
    }

    @Test
    @DisplayName("handleRuntime: retorna 400 para RuntimeException")
    void handleRuntime_returns400() {
        RuntimeException ex = new RuntimeException("Erro de negócio");

        ResponseEntity<ErrorResponse> response = handler.handleRuntime(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Erro de negócio");
    }

    @Test
    @DisplayName("DuplicateBookingException: preserva mensagem no construtor")
    void duplicateBookingException_message() {
        DuplicateBookingException ex = new DuplicateBookingException("CPF duplicado");
        assertThat(ex.getMessage()).isEqualTo("CPF duplicado");
    }

    @Test
    @DisplayName("SlotFullException: preserva mensagem no construtor")
    void slotFullException_message() {
        SlotFullException ex = new SlotFullException("Slot cheio");
        assertThat(ex.getMessage()).isEqualTo("Slot cheio");
    }
}
