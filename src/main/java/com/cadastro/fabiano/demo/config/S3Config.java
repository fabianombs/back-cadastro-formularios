package com.cadastro.fabiano.demo.config;

import com.cadastro.fabiano.demo.service.ImageStorageService;
import com.cadastro.fabiano.demo.service.LocalImageStorageService;
import com.cadastro.fabiano.demo.service.S3ImageStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {

    // ── Dev: armazenamento local ──────────────────────────────────────────────

    @Bean
    @Profile("dev")
    public ImageStorageService localImageStorageService(
            @Value("${app.upload-dir:uploads}") String uploadDir,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        return new LocalImageStorageService(uploadDir, baseUrl);
    }

    // ── Prod / Homolog: AWS S3 ────────────────────────────────────────────────

    @Bean
    @Profile("!dev")
    public S3Client s3Client(@Value("${aws.region}") String region) {
        // POR QUE NAO HA MAIS aws.access-key-id AQUI (FABIANO-79)
        //
        // Antes este bean montava um StaticCredentialsProvider a partir de dois
        // @Value obrigatorios. Isso tinha duas consequencias:
        //
        //   1. A aplicacao NUNCA usava o papel da instancia, mesmo existindo um.
        //      Nao era questao de precedencia — o codigo simplesmente nao
        //      consultava a cadeia de credenciais.
        //   2. Remover as variaveis do .env nao migrava para o papel: derrubava
        //      a aplicacao, porque o placeholder ${AWS_ACCESS_KEY_ID} deixava de
        //      resolver e o bean falhava na criacao.
        //
        // O DefaultCredentialsProvider percorre a cadeia padrao, nesta ordem:
        // propriedades de sistema, VARIAVEIS DE AMBIENTE, arquivo de perfil e,
        // por ultimo, o papel da instancia via IMDS.
        //
        // Isso torna a migracao um passo reversivel em vez de uma virada: as
        // variaveis do .env chamam-se AWS_ACCESS_KEY_ID e AWS_SECRET_ACCESS_KEY,
        // que sao exatamente os nomes que a cadeia procura. Enquanto elas
        // existirem, o comportamento e identico ao de antes. Quando forem
        // removidas, a aplicacao cai sozinha no papel da instancia — sem
        // recompilar e sem editar nada.
        //
        // Chave estatica nao expira: vaza inteira em um log, num 'docker
        // inspect' colado num chat, ou numa copia do .env para outra maquina.
        // Credencial de papel dura minutos e se renova sozinha. E o mesmo
        // argumento que motivou trocar chave por OIDC no GitHub (FABIANO-58).
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    @Profile("!dev")
    public ImageStorageService s3ImageStorageService(
            S3Client s3Client,
            @Value("${aws.s3.bucket}") String bucket,
            @Value("${aws.s3.base-url}") String s3BaseUrl) {
        return new S3ImageStorageService(s3Client, bucket, s3BaseUrl);
    }
}
