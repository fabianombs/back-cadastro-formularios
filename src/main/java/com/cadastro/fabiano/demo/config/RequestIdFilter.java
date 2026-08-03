package com.cadastro.fabiano.demo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Correlaciona todas as linhas de log de uma mesma requisicao.
 *
 * O log estruturado do Spring Boot inclui automaticamente o conteudo do MDC no
 * JSON, entao basta colocar as chaves aqui. Com o requestId devolvido no header
 * da resposta, um erro relatado pelo usuario vira uma consulta unica no Loki:
 * o cliente manda o identificador e o log inteiro daquela requisicao aparece.
 *
 * Roda antes de tudo (HIGHEST_PRECEDENCE) para que ate falha de autenticacao
 * ja saia correlacionada.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";

    /**
     * O identificador recebido de fora e reaproveitado para permitir rastrear a
     * mesma chamada atravessando sistemas. Mas ele vem do cliente, entao passa
     * por filtro: sem restricao, daria para injetar conteudo forjado no log ou
     * mandar um valor gigante em toda requisicao.
     */
    private static final Pattern ACEITAVEL = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String requestId = identificador(request.getHeader(HEADER));

        MDC.put("requestId", requestId);
        MDC.put("method", request.getMethod());
        MDC.put("path", request.getRequestURI());

        // Escrito antes do doFilter: depois que a resposta e enviada, nao ha
        // mais como acrescentar cabecalho.
        response.setHeader(HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Obrigatorio: a thread volta para o pool e atenderia a proxima
            // requisicao carregando o MDC da anterior.
            MDC.clear();
        }
    }

    private String identificador(String recebido) {
        if (recebido != null && ACEITAVEL.matcher(recebido).matches()) {
            return recebido;
        }
        return UUID.randomUUID().toString();
    }
}
