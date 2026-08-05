package com.cadastro.fabiano.demo.exception;

import com.cadastro.fabiano.demo.config.MetricasDeNegocio;
import com.cadastro.fabiano.demo.dto.response.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Excecoes de regra de negocio sao lancadas como RuntimeException "pelada",
    // com mensagem para o usuario. Qualquer SUBCLASSE que chegue aqui e algo que
    // ninguem previu - falha de infraestrutura, bug, sessao de JPA fechada - e
    // merece rastro completo. Sem essa distincao, ou o log fica cheio de pilha
    // de "Template nao encontrado", ou um erro de verdade passa despercebido.
    private static boolean ehInesperada(RuntimeException ex) {
        return ex.getClass() != RuntimeException.class;
    }

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

    /**
     * Erros de negócio genéricos (validação, not found, etc.).
     *
     * <p>Este handler captura QUALQUER RuntimeException e devolve 400. Isso e
     * conveniente para regra de negocio e perigoso para todo o resto: ate
     * 03/08/2026 ele nao registrava nada, entao uma falha de infraestrutura
     * chegava ao usuario como "400 Bad Request" mudo, sem uma linha no log.
     * Foi exatamente assim que uma LazyInitializationException ficou invisivel
     * durante o FABIANO-37.</p>
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException ex) {
        contar(ex, HttpStatus.BAD_REQUEST);

        if (ehInesperada(ex)) {
            log.warn("Erro nao previsto devolvido como 400. tipo={}", ex.getClass().getName(), ex);
        } else {
            log.warn("Regra de negocio recusou a requisicao. motivo={}", ex.getMessage());
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)       // 400
                .body(new ErrorResponse(ex.getMessage()));
    }
}
