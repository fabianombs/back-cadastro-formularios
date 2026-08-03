package com.cadastro.fabiano.demo.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Configuracao das metricas exportadas para o Prometheus.
 *
 * Tudo aqui e feito por codigo, e nao por propriedade em application.properties,
 * por um motivo que ja custou caro neste projeto: propriedade do Spring escrita
 * errado e IGNORADA EM SILENCIO. Nada falha, a metrica simplesmente nao aparece,
 * e a descoberta acontece no dia em que alguem precisa dela. Bean quebra o build.
 */
@Configuration
public class ObservabilityConfig {

    private static final String APLICACAO = "fabiano-back";

    /** Teto de valores distintos de uri antes de agrupar o excedente. */
    private static final int MAX_URIS = 100;

    /**
     * Tags aplicadas a TODA metrica registrada.
     *
     * Nao usei management.metrics.tags porque essa propriedade foi depreciada no
     * Spring Boot 3.2 e depois des-depreciada: a substituta proposta
     * (management.observations.key-values) so alcanca metricas criadas via
     * Observation, deixando as de JVM e HikariCP sem tag. O customizer alcanca
     * todo medidor, independente da API que o criou.
     */
    @Bean
    MeterRegistryCustomizer<MeterRegistry> tagsComuns(Environment ambiente) {
        String[] perfis = ambiente.getActiveProfiles();
        String perfil = perfis.length > 0 ? perfis[0] : "desconhecido";

        // A tag environment permite que um unico Prometheus colete producao e
        // homologacao sem misturar as series no mesmo grafico.
        return registry -> registry.config().commonTags(
                "application", APLICACAO,
                "environment", perfil);
    }

    /**
     * Teto de cardinalidade para a metrica de requisicoes HTTP.
     *
     * Cada valor distinto de uri vira uma serie temporal propria. Um endpoint com
     * caminho variavel que escape do template - um 404 de rota inexistente, por
     * exemplo - geraria series sem limite e derrubaria o Prometheus por consumo
     * de memoria. Passando de MAX_URIS, o excedente e descartado em vez de
     * crescer para sempre.
     */
    @Bean
    MeterFilter limiteDeCardinalidadeDeUri() {
        return MeterFilter.maximumAllowableTags(
                "http.server.requests", "uri", MAX_URIS, MeterFilter.deny());
    }
}
