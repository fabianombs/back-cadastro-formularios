package com.cadastro.fabiano.demo.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    // Token de coleta de metricas. Vazio (o padrao) mantem /actuator/prometheus
    // exigindo autenticacao normal - so quem define a variavel habilita a coleta.
    @Value("${metrics.scrape-token:}")
    private String metricsScrapeToken;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/files/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Excecao unica: o Prometheus apresentando o token de coleta.
                        // JWT nao serve para isso porque expira em 24h e ninguem vai
                        // renovar token de um coletor a cada dia.
                        .requestMatchers(this::coletorAutorizado).permitAll()
                        // Todo o resto do actuator exige autenticacao. /actuator/prometheus
                        // entrega nomes de endpoint, versao de JVM e volume de trafego -
                        // mapa da aplicacao para quem estiver olhando.
                        .requestMatchers("/actuator/**").authenticated()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/form-submissions/**").permitAll()
                        .requestMatchers("/forms/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/form-templates/slug/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/form-templates/view/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/files/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/clients/*/templates").permitAll()
                        .requestMatchers(HttpMethod.GET, "/appointments/template/*/slots").permitAll()
                        .requestMatchers(HttpMethod.GET, "/appointments/template/*/slots/range").permitAll()
                        .requestMatchers(HttpMethod.POST, "/appointments/book").permitAll()
                        .requestMatchers("/attendance/**").permitAll()
                        .requestMatchers("/equipment/**").permitAll()
                        .requestMatchers("/appointments/**").permitAll()
                        // Endpoints públicos do quiz (jogador + ranking)
                        .requestMatchers(HttpMethod.GET,  "/quizzes/slug/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/quizzes/slug/*/sessions").permitAll()
                        .requestMatchers(HttpMethod.POST, "/quizzes/sessions/*/answers").permitAll()
                        .requestMatchers(HttpMethod.POST, "/quizzes/sessions/*/complete").permitAll()
                        // Endpoints públicos da pesquisa de satisfação
                        .requestMatchers(HttpMethod.GET,  "/surveys/slug/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/surveys/slug/*/responses").permitAll()
                        .requestMatchers("/dashboard/**").authenticated()
                        .anyRequest().authenticated()
                )
                // Retorna 401 para requests sem token — Spring Security 6 padrão é 403, o que
                // impede o interceptor Angular de detectar sessão expirada e redirecionar para login
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, authEx) ->
                                res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Libera /actuator/prometheus para quem apresentar o token de coleta.
     *
     * Alternativas descartadas: mover o actuator para uma porta propria levaria o
     * /actuator/health junto e quebraria o health-gate do deploy-safe.sh; e deixar
     * o endpoint aberto so com bloqueio no nginx nao protege enquanto a porta 8080
     * estiver exposta no security group.
     */
    private boolean coletorAutorizado(jakarta.servlet.http.HttpServletRequest request) {
        if (metricsScrapeToken == null || metricsScrapeToken.isBlank()) {
            return false;
        }
        if (!"/actuator/prometheus".equals(request.getRequestURI())) {
            return false;
        }
        // Duas formas aceitas. O header proprio e o mais simples de testar com
        // curl; o Authorization com tipo "Metrics-Token" e o que o Prometheus
        // consegue enviar nativamente, sem depender de versao que suporte
        // header arbitrario. O tipo NAO e "Bearer" de proposito: assim o
        // JwtAuthenticationFilter, que so reage a "Bearer ", ignora este header.
        String enviado = request.getHeader("X-Metrics-Token");

        if (enviado == null) {
            String autorizacao = request.getHeader("Authorization");
            if (autorizacao != null && autorizacao.startsWith("Metrics-Token ")) {
                enviado = autorizacao.substring("Metrics-Token ".length());
            }
        }

        if (enviado == null) {
            return false;
        }
        // MessageDigest.isEqual compara em tempo constante. Um equals() comum
        // retorna mais rapido quanto antes os bytes divergem, e isso permite
        // descobrir o token caractere a caractere medindo o tempo de resposta.
        return MessageDigest.isEqual(
                enviado.getBytes(StandardCharsets.UTF_8),
                metricsScrapeToken.getBytes(StandardCharsets.UTF_8));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        // ── Endpoints públicos (formulários, agendamentos, presença) ──────────
        // Aceita qualquer origem: mobile, tablet, totem, qualquer dispositivo na rede
        CorsConfiguration publicConfig = new CorsConfiguration();
        publicConfig.setAllowedOriginPatterns(List.of("*"));
        // DELETE necessário para remover acompanhantes via painel (endpoint público)
        publicConfig.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        publicConfig.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        // Sem expor, o navegador esconde o header da resposta e o front nao
        // consegue mostrar o identificador para o usuario relatar um erro.
        publicConfig.setExposedHeaders(List.of("X-Request-Id"));
        publicConfig.setAllowCredentials(false);

        // ── Endpoints privados (admin, criação de templates, etc.) ────────────
        // Aceita apenas as origens configuradas (painel administrativo)
        CorsConfiguration privateConfig = new CorsConfiguration();
        privateConfig.setAllowedOriginPatterns(Arrays.asList(allowedOrigins.split(",")));
        privateConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        privateConfig.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        // Sem expor, o navegador esconde o header da resposta e o front nao
        // consegue mostrar o identificador para o usuario relatar um erro.
        privateConfig.setExposedHeaders(List.of("X-Request-Id"));
        privateConfig.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // Rotas consumidas pelos links públicos dos formulários
        source.registerCorsConfiguration("/form-templates/slug/**", publicConfig);
        source.registerCorsConfiguration("/form-templates/view/**", publicConfig);
        source.registerCorsConfiguration("/form-submissions/**",    publicConfig);
        source.registerCorsConfiguration("/appointments/**",        publicConfig);
        source.registerCorsConfiguration("/attendance/**",          publicConfig);
        source.registerCorsConfiguration("/equipment/**",           publicConfig);
        source.registerCorsConfiguration("/files/**",               publicConfig);
        source.registerCorsConfiguration("/quizzes/slug/**",        publicConfig);
        source.registerCorsConfiguration("/quizzes/sessions/**",    publicConfig);
        source.registerCorsConfiguration("/surveys/slug/**",        publicConfig);

        // Tudo mais: painel admin, autenticação, criação/edição de templates
        source.registerCorsConfiguration("/**", privateConfig);

        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}