package com.cadastro.fabiano.demo.exception;

import com.cadastro.fabiano.demo.config.MetricasDeNegocio;
import com.cadastro.fabiano.demo.dto.response.ErrorResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final SimpleMeterRegistry registro = new SimpleMeterRegistry();
    private final MetricasDeNegocio metricas = new MetricasDeNegocio(registro);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(metricas);

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

    // ------------------------------------------------------------------
    // Metricas de erro tratado (FABIANO-25)
    //
    // Usa SimpleMeterRegistry de verdade, e nao mock: se o nome ou os rotulos
    // da metrica estiverem errados, a busca abaixo nao encontra o contador e o
    // teste falha. Com mock, passaria sem verificar coisa alguma.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("erro_tratado_total: separa o tipo de excecao e o status")
    void erroTratado_registraTipoEStatus() {
        handler.handleDuplicate(new DuplicateBookingException("dup"));
        handler.handleSlotFull(new SlotFullException("cheio"));
        handler.handleSlotFull(new SlotFullException("cheio de novo"));
        handler.handleRuntime(new RuntimeException("generico"));

        assertThat(contador("DuplicateBookingException", 409)).isEqualTo(1.0);
        assertThat(contador("SlotFullException", 409)).isEqualTo(2.0);
        assertThat(contador("RuntimeException", 400)).isEqualTo(1.0);

        // Quatro chamadas, tres combinacoes distintas de tipo e status.
        assertThat(registro.find("erro.tratado").counters()).hasSize(3);
    }

    @Test
    @DisplayName("erro_tratado_total: nenhum rotulo carrega a mensagem do erro")
    void erroTratado_naoVazaMensagem() {
        // A mensagem pode conter dado do usuario - CPF numa mensagem de
        // duplicidade, por exemplo. Como rotulo seria PII no Prometheus e
        // cardinalidade ilimitada ao mesmo tempo.
        handler.handleDuplicate(new DuplicateBookingException("CPF 123.456.789-00 duplicado"));

        registro.find("erro.tratado").counters().forEach(c ->
                c.getId().getTags().forEach(tag ->
                        assertThat(tag.getValue()).doesNotContain("123.456.789")));
    }

    // ------------------------------------------------------------------
    // Infraestrutura indisponivel -> 503 (FABIANO-56)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("handleIndisponivel: banco fora vira 503, nao 400")
    void handleIndisponivel_returns503() {
        // A excecao que a aplicacao produziu de verdade em 05/08/2026, com o
        // RDS de ensaio fora durante o upgrade para o MySQL 8.4.
        var ex = new DataAccessResourceFailureException("Unable to acquire JDBC Connection");

        ResponseEntity<ErrorResponse> response = handler.handleIndisponivel(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("handleIndisponivel: responde com Retry-After")
    void handleIndisponivel_temRetryAfter() {
        var ex = new QueryTimeoutException("statement timeout");

        ResponseEntity<ErrorResponse> response = handler.handleIndisponivel(ex);

        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
    }

    @Test
    @DisplayName("handleIndisponivel: nao vaza host nem string de conexao para o usuario")
    void handleIndisponivel_naoVazaDetalheDeInfra() {
        // A mensagem do Spring costuma trazer host, porta e usuario. Isso nao
        // pode chegar a tela de quem so queria marcar presenca num evento.
        var ex = new DataAccessResourceFailureException(
                "Could not open connection to jdbc:mysql://poc-fabiano-db.abc123.us-east-1.rds.amazonaws.com:3306/poc_fabiano_new");

        ResponseEntity<ErrorResponse> response = handler.handleIndisponivel(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message())
                .doesNotContain("jdbc")
                .doesNotContain("rds.amazonaws.com")
                .doesNotContain("3306")
                .isEqualTo("Sistema temporariamente indisponivel. Tente novamente em instantes.");
    }

    @Test
    @DisplayName("A correcao NAO alargou: erro de negocio continua 400")
    void erroDeNegocio_continua400() {
        // Trava do criterio de aceite. Se um dia alguem mover uma excecao de
        // regra de negocio para o handler de indisponibilidade, o cliente passa
        // a ver "tente de novo" onde deveria corrigir o formulario — e o painel
        // de 5xx acende sem haver incidente nenhum.
        ResponseEntity<ErrorResponse> negocio = handler.handleRuntime(
                new RuntimeException("Template nao encontrado"));

        assertThat(negocio.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(negocio.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNull();
    }

    @Test
    @DisplayName("erro_tratado_total: indisponibilidade e contada como 503")
    void erroTratado_registra503() {
        handler.handleIndisponivel(new DataAccessResourceFailureException("banco fora"));

        assertThat(contador("DataAccessResourceFailureException", 503)).isEqualTo(1.0);
    }

    private double contador(String tipo, int status) {
        var c = registro.find("erro.tratado")
                .tag("tipo", tipo)
                .tag("status", String.valueOf(status))
                .counter();
        return c == null ? -1 : c.count();
    }
}
