package com.cadastro.fabiano.demo.config;

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
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

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
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/form-submissions/**").permitAll()
                        .requestMatchers("/forms/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/form-templates/slug/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/files/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/clients/*/templates").permitAll()
                        .requestMatchers(HttpMethod.GET, "/appointments/template/*/slots").permitAll()
                        .requestMatchers(HttpMethod.GET, "/appointments/template/*/slots/range").permitAll()
                        .requestMatchers(HttpMethod.POST, "/appointments/book").permitAll()
                        .requestMatchers("/attendance/**").permitAll()
                        .requestMatchers("/appointments/**").permitAll()
                        .requestMatchers("/dashboard/**").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        // ── Endpoints públicos (formulários, agendamentos, presença) ──────────
        // Aceita qualquer origem: mobile, tablet, totem, qualquer dispositivo na rede
        CorsConfiguration publicConfig = new CorsConfiguration();
        publicConfig.setAllowedOriginPatterns(List.of("*"));
        publicConfig.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        publicConfig.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        publicConfig.setAllowCredentials(false);

        // ── Endpoints privados (admin, criação de templates, etc.) ────────────
        // Aceita apenas as origens configuradas (painel administrativo)
        CorsConfiguration privateConfig = new CorsConfiguration();
        privateConfig.setAllowedOriginPatterns(Arrays.asList(allowedOrigins.split(",")));
        privateConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        privateConfig.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        privateConfig.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // Rotas consumidas pelos links públicos dos formulários
        source.registerCorsConfiguration("/form-templates/slug/**", publicConfig);
        source.registerCorsConfiguration("/form-submissions/**",    publicConfig);
        source.registerCorsConfiguration("/appointments/**",        publicConfig);
        source.registerCorsConfiguration("/attendance/**",          publicConfig);
        source.registerCorsConfiguration("/files/**",               publicConfig);

        // Tudo mais: painel admin, autenticação, criação/edição de templates
        source.registerCorsConfiguration("/**", privateConfig);

        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}