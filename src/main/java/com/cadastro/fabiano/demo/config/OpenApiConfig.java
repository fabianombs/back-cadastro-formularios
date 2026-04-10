package com.cadastro.fabiano.demo.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração do OpenAPI/Swagger para documentação automática da API.
 *
 * <p>Acesso à UI: <a href="http://localhost:8080/swagger-ui.html">http://localhost:8080/swagger-ui.html</a></p>
 * <p>JSON spec: <a href="http://localhost:8080/v3/api-docs">http://localhost:8080/v3/api-docs</a></p>
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CadastroFabiano API")
                        .description("""
                                API para gerenciamento de formulários dinâmicos, agendamentos e controle de presença.

                                ## Autenticação
                                A maioria dos endpoints requer um token JWT no header `Authorization: Bearer <token>`.
                                Utilize `/auth/login` para obter o token.

                                ## Rotas Públicas
                                As rotas de formulários, agendamentos e presença são públicas e não requerem autenticação.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Equipe CadastroFabiano")
                                .email("contato@cadastrofabiano.com"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Informe o token JWT obtido no endpoint /auth/login")));
    }
}
