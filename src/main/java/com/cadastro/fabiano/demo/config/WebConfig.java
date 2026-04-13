package com.cadastro.fabiano.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
@Profile("dev")
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();

        registry.addResourceHandler("/files/**")
                .addResourceLocations(uploadPath)
                // Cache de 1 ano: cada upload gera nome UUID único,
                // então a imagem nunca muda — pode ser cacheada ao máximo
                // ATENÇÃO: em produção (Railway/Heroku) a pasta uploads/ é
                // apagada a cada deploy. Para persistência real, use S3 ou R2
                .setCachePeriod(31536000);
    }
}
