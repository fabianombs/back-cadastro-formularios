# =============================================================================
# Backend CadastroFabiano — imagem de producao (FABIANO-12)
# =============================================================================

# ---- Stage 1: build ---------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copia so o POM primeiro: o cache de dependencias sobrevive a mudancas de codigo
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# Explode o jar em camadas para o Docker cachear por camada.
# So a camada 'application' muda a cada deploy — as dependencias (o grosso do
# tamanho) sao reaproveitadas, o que deixa o push pro GHCR bem mais rapido.
RUN java -Djarmode=layertools -jar target/*.jar extract --destination extracted

# ---- Stage 2: runtime -------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

RUN apk add --no-cache curl

# Ordem importa: da camada mais estavel para a mais volatil
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

RUN chown -R app:app /app
USER app
EXPOSE 8080

# MaxRAMPercentage limita o heap ao tamanho do CONTAINER. Sem isso a JVM olha a
# RAM do host inteiro e o OOM killer aparece quando Prometheus/Grafana/Loki
# estiverem dividindo os 2 GB da t3.small com a aplicacao.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"

# start-period de 90s: Spring Boot leva 8-15s para subir e o Flyway pode
# demorar mais na primeira vez. Sem essa folga o container vira unhealthy antes
# de terminar o boot e dispara rollback falso-positivo.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# JarLauncher mudou de pacote no Spring Boot 3.2+ (ganhou o '.launch.')
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
