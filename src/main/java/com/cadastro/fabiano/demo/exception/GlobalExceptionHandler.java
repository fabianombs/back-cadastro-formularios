package com.cadastro.fabiano.demo.exception;

import com.cadastro.fabiano.demo.config.MetricasDeNegocio;
import com.cadastro.fabiano.demo.dto.response.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
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
     * Infraestrutura indisponivel — 503, nao 400. (FABIANO-56)
     *
     * <p>Capturado ao vivo em 05/08/2026, durante o upgrade do MySQL de ensaio:
     * com o RDS fora por 2m41s, uma requisicao perfeita recebeu <b>400 Bad
     * Request</b> — ou seja, o sistema disse ao usuario que ELE tinha errado.</p>
     *
     * <p>A diferenca nao e academica. 400 e ruido de cliente; 503 e problema
     * nosso. Contabilizada como 4xx, uma queda de banco fica <b>invisivel</b>
     * para o painel de taxa de erro (FABIANO-27) e para o alerta de 5xx
     * (FABIANO-28) — os dois instrumentos que deveriam gritar exatamente nessa
     * hora. E, para o frontend, 400 e 503 pedem tratamentos opostos: um manda
     * corrigir o campo, o outro manda esperar e repetir.</p>
     *
     * <p>Este handler vem antes do generico por ser mais especifico — o Spring
     * resolve pela distancia na hierarquia, entao o RuntimeException abaixo
     * continua valendo para todo o resto.</p>
     */
    @ExceptionHandler({
            DataAccessResourceFailureException.class,   // banco fora, pool esgotado
            CannotGetJdbcConnectionException.class,     // subclasse da anterior; explicita por clareza
            QueryTimeoutException.class,                // consulta estourou o tempo
            TransientDataAccessResourceException.class  // falha momentanea, vale repetir
    })
    public ResponseEntity<ErrorResponse> handleIndisponivel(DataAccessException ex) {
        contar(ex, HttpStatus.SERVICE_UNAVAILABLE);

        // ERROR e nao WARN: em producao o nivel raiz e WARN, e banco fora nao e
        // aviso — e incidente. Com a pilha, porque a causa raiz costuma estar
        // duas ou tres excecoes abaixo (SQLException dentro de Hikari dentro de
        // Spring), e sem ela o log diz apenas que "algo de banco falhou".
        log.error("Infraestrutura indisponivel devolvida como 503. tipo={}",
                ex.getClass().getSimpleName(), ex);

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)   // 503
                // Retry-After transforma o 503 em instrucao, e nao so em
                // diagnostico: navegador, proxy e robo sabem ler isso. 30s
                // porque a reconexao do HikariCP levou menos que isso quando o
                // RDS voltou, medido no upgrade de 05/08.
                .header(HttpHeaders.RETRY_AFTER, "30")
                // Mensagem FIXA de proposito. A do Spring costuma trazer host,
                // porta e string de conexao — mandar isso para a tela do
                // convidado de um evento seria vazar topologia de rede para
                // quem so queria marcar presenca.
                .body(new ErrorResponse(
                        "Sistema temporariamente indisponivel. Tente novamente em instantes."));
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
