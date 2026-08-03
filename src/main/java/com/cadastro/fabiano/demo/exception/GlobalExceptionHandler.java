package com.cadastro.fabiano.demo.exception;

import com.cadastro.fabiano.demo.config.MetricasDeNegocio;
import com.cadastro.fabiano.demo.dto.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MetricasDeNegocio metricas;

    public GlobalExceptionHandler(MetricasDeNegocio metricas) {
        this.metricas = metricas;
    }

    /**
     * Erro tratado nao aparece como 5xx e nao entra na taxa de erro do
     * dashboard - mas continua sendo erro. Um pico de 409 significa que algo
     * mudou no comportamento dos usuarios, ou no sistema.
     */
    private void contar(RuntimeException ex, HttpStatus status) {
        metricas.erroTratado(ex.getClass().getSimpleName(), status.value());
    }

    /** Usuário já cadastrado — exibe mensagem amigável sem stack trace */
    @ExceptionHandler(DuplicateBookingException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateBookingException ex) {
        contar(ex, HttpStatus.CONFLICT);
        return ResponseEntity
                .status(HttpStatus.CONFLICT)          // 409
                .body(new ErrorResponse(ex.getMessage()));
    }

    /** Horário lotado */
    @ExceptionHandler(SlotFullException.class)
    public ResponseEntity<ErrorResponse> handleSlotFull(SlotFullException ex) {
        contar(ex, HttpStatus.CONFLICT);
        return ResponseEntity
                .status(HttpStatus.CONFLICT)          // 409
                .body(new ErrorResponse(ex.getMessage()));
    }

    /** Erros de negócio genéricos (validação, not found, etc.) */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException ex) {
        contar(ex, HttpStatus.BAD_REQUEST);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)       // 400
                .body(new ErrorResponse(ex.getMessage()));
    }
}
