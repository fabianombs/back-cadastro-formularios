package com.cadastro.fabiano.demo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Registra uma linha de log por requisicao concluida.
 *
 * Sem isto o Loki so enxerga o que quebrou: o Spring Boot nao loga nada por
 * requisicao, entao um login bem-sucedido nao deixa rastro nenhum. O log de
 * acesso e o que permite responder "o que aconteceu as 14h32" em vez de
 * apenas "o que falhou".
 *
 * Roda logo DEPOIS do RequestIdFilter (HIGHEST_PRECEDENCE + 1): assim o MDC
 * ja tem requestId e userId quando esta linha e emitida, e ela sai correlata
 * com o cabecalho X-Request-Id devolvido ao cliente.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("acesso");

    // Resposta acima deste tempo vira WARN mesmo tendo status 2xx: requisicao
    // lenta nao aparece em contador de erro, mas e o primeiro sintoma de
    // saturacao do pool ou de consulta sem indice.
    private static final long LENTO_MS = 2000;

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao,
                                    HttpServletResponse resposta,
                                    FilterChain corrente)
            throws ServletException, IOException {

        long inicio = System.nanoTime();
        try {
            corrente.doFilter(requisicao, resposta);
        } finally {
            long duracaoMs = (System.nanoTime() - inicio) / 1_000_000;
            int status = resposta.getStatus();

            // Vao para o MDC e, dai, para o metadado estruturado no Loki -
            // permitem filtrar por status ou por rota sem depender de casar
            // texto na mensagem.
            MDC.put("status", String.valueOf(status));
            MDC.put("duracaoMs", String.valueOf(duracaoMs));
            try {
                String linha = requisicao.getMethod() + " "
                        + requisicao.getRequestURI()
                        + " -> " + status + " em " + duracaoMs + " ms";
                if (status >= 500) {
                    log.warn(linha);
                } else if (duracaoMs >= LENTO_MS) {
                    log.warn(linha + " (lenta)");
                } else {
                    log.info(linha);
                }
            } finally {
                // O RequestIdFilter faz MDC.clear() no fim, mas limpar aqui o
                // que foi posto aqui mantem o filtro autocontido - se um dia
                // ele for reordenado, nao vaza status de uma requisicao para
                // a linha de log da seguinte na mesma thread.
                MDC.remove("status");
                MDC.remove("duracaoMs");
            }
        }
    }

    /**
     * O /actuator fica de fora de proposito. O Prometheus raspa
     * /actuator/prometheus a cada 15 segundos e o health-gate do deploy bate
     * no /actuator/health: incluir os dois significaria mais de quatro linhas
     * por minuto, para sempre, de ruido puro - e o disco da t2.micro nao
     * sobra para isso.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest requisicao) {
        return requisicao.getRequestURI().startsWith("/actuator");
    }

}

// A query string fica DE FORA da linha. Ela traria a paginacao, que ajuda
// pouco, mas tambem qualquer parametro de busca - e /attendance/template/
// existence e do tipo de rota que recebe documento de pessoa por query.
// Documento de cliente gravado em log fica sete dias no Loki e vaza para
// quem tiver acesso ao painel. O caminho sozinho basta para localizar a
// origem; o restante esta no requestId, que liga a linha a requisicao.

