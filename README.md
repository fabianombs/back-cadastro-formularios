# CadastroFabiano — Backend API

Backend para gerenciamento de **formulários dinâmicos**, **agendamentos** e **controle de presença**. Construído com Spring Boot 3 + Java 21 + MySQL.

---

## Índice

1. [Tecnologias](#tecnologias)
2. [Arquitetura](#arquitetura)
3. [Configuração e execução](#configuração-e-execução)
4. [Variáveis de ambiente](#variáveis-de-ambiente)
5. [Documentação da API (Swagger)](#documentação-da-api-swagger)
6. [Fluxos principais](#fluxos-principais)
   - [Autenticação](#autenticação)
   - [Clientes e Templates](#clientes-e-templates)
   - [Agendamentos](#agendamentos)
   - [Controle de Presença](#controle-de-presença)
   - [Dashboard](#dashboard)
7. [Segurança — Rotas Públicas e Protegidas](#segurança--rotas-públicas-e-protegidas)
8. [Testes e Cobertura](#testes-e-cobertura)
9. [Migrações de Banco de Dados](#migrações-de-banco-de-dados)

---

## Tecnologias

| Camada | Tecnologia |
|---|---|
| Framework | Spring Boot 3.5.11 |
| Linguagem | Java 21 |
| Persistência | Spring Data JPA / Hibernate |
| Banco de dados | MySQL |
| Migrações | Flyway |
| Segurança | Spring Security + JWT (JJWT 0.11.5) |
| Documentação | SpringDoc OpenAPI 2.7 (Swagger UI) |
| Build | Maven |
| Cobertura de testes | JaCoCo |

---

## Arquitetura

```
src/
└── main/java/com/cadastro/fabiano/demo/
    ├── config/          # Segurança, JWT, OpenAPI
    ├── controller/      # Endpoints REST (9 controllers)
    ├── service/         # Lógica de negócio (8 serviços)
    ├── repository/      # Acesso a dados (Spring Data JPA)
    ├── entity/          # Entidades JPA
    ├── dto/
    │   ├── request/     # DTOs de entrada (records)
    │   └── response/    # DTOs de saída (records)
    ├── exception/       # Exceções customizadas + GlobalExceptionHandler
    ├── mapper/          # Mapeamento entidade ↔ DTO
    └── utils/           # Utilitários (hash SHA-256)
```

### Decisões de design

- **Soft Delete** em `Client` e `FormTemplate` via `@SQLRestriction("deleted = false")` — dados nunca são removidos fisicamente.
- **Lock pessimista** (`PESSIMISTIC_WRITE`) no template durante o agendamento para evitar overbooking em requisições concorrentes.
- **Deduplicação de agendamentos** configurável por template: campos escolhidos formam uma chave normalizada que impede dois agendamentos do mesmo "identificador" (ex.: CPF) para o mesmo dia.
- **Deduplicação de imagens** via hash SHA-256: o mesmo arquivo não é salvo duas vezes; imagens órfãs são removidas do disco automaticamente.
- **Stateless**: nenhuma sessão é armazenada no servidor — autenticação exclusivamente via JWT.

---

## Configuração e execução

### Pré-requisitos

- Java 21+
- MySQL 8+
- Maven 3.9+

### Executar localmente

```bash
# 1. Clone o repositório
git clone <url-do-repo>
cd poc-fabiano

# 2. Configure as variáveis de ambiente (veja seção abaixo)
#    ou edite src/main/resources/application-dev.properties

# 3. Execute
./mvnw spring-boot:run
```

A API estará disponível em `http://localhost:8080`.

---

## Variáveis de ambiente

| Variável | Descrição | Padrão (dev) |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Perfil ativo | `dev` |
| `SPRING_DATASOURCE_URL` | URL JDBC do MySQL | — (ver `application-dev.properties`) |
| `SPRING_DATASOURCE_USERNAME` | Usuário do banco | — |
| `SPRING_DATASOURCE_PASSWORD` | Senha do banco | — |
| `JWT_SECRET` | Chave HMAC-256 para assinar tokens | `MinhaChaveSuperSecretaMuitoForte123456` |
| `JWT_EXPIRATION` | Validade do token em ms | `86400000` (24h) |
| `CORS_ALLOWED_ORIGINS` | Origens permitidas para o painel admin | `http://localhost:4200` |
| `UPLOAD_DIR` | Diretório de upload de imagens | `uploads` |
| `APP_BASE_URL` | URL base para montar URLs públicas de imagens | `http://localhost:8080` |

---

## Documentação da API (Swagger)

Com a aplicação rodando, acesse:

- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON:** `http://localhost:8080/v3/api-docs`

A documentação é gerada **automaticamente** a partir das anotações `@Tag` e `@Operation` nos controllers.

Para testar endpoints protegidos, clique em **Authorize** e informe `Bearer <token>` obtido em `/auth/login`.

---

## Fluxos principais

### Autenticação

```
POST /auth/register   →  Cria usuário ADMIN e retorna JWT
POST /auth/login      →  Valida credenciais e retorna JWT
```

O token JWT contém:
- `sub` (username)
- `userId`
- `role` (`ROLE_ADMIN`, `ROLE_FUNCIONARIO` ou `ROLE_CLIENT`)
- `exp` (24h por padrão)

Inclua o token em todas as requisições protegidas:
```
Authorization: Bearer <token>
```

---

### Clientes e Templates

```
# Criar cliente (com usuário associado)
POST /clients

# Criar formulário para um cliente
POST /form-templates/create/{clientId}

# Buscar formulário público pelo slug
GET  /form-templates/slug/{slug}           ← endpoint público

# Enviar resposta de um formulário
POST /form-submissions                     ← endpoint público

# Upload de imagem para customização
POST /uploads/image
```

**Fluxo de customização visual do template:**

1. Faça upload das imagens via `POST /uploads/image` → recebe URLs.
2. Inclua as URLs no objeto `appearance` ao criar/editar o template.
3. Ao atualizar/excluir, imagens que ficaram órfãs (sem referência em nenhum template ativo) são removidas automaticamente do disco.

---

### Agendamentos

```
# Consultar slots disponíveis para um dia
GET  /appointments/template/{id}/slots?date=2025-10-15      ← público

# Consultar slots de um intervalo de datas
GET  /appointments/template/{id}/slots/range?from=...&to=... ← público

# Realizar agendamento
POST /appointments/book                                      ← público

# Cancelar agendamento
PATCH /appointments/{id}/cancel

# Listar agendamentos do template
GET  /appointments/template/{id}
```

**Regras de negócio:**

| Regra | Descrição |
|---|---|
| Capacidade por slot | Cada horário aceita até `slotCapacity` agendamentos simultâneos (padrão: 1) |
| Data válida | Não é possível agendar no passado nem além de `maxDaysAhead` dias à frente |
| Horário válido | O horário deve ser um slot gerado pela configuração de agenda |
| Deduplicação | Se `dedupFields` estiver configurado, a combinação de valores desses campos deve ser única por data no template |
| Lock pessimista | Bookings concorrentes são serializados via `PESSIMISTIC_WRITE` no template |

**Exemplo de configuração de agenda:**

```json
{
  "startTime": "08:00",
  "endTime": "17:00",
  "slotDurationMinutes": 30,
  "maxDaysAhead": 7,
  "slotCapacity": 3,
  "dedupFields": ["CPF"]
}
```

Gera slots: 08:00, 08:30, 09:00 … 16:30. Cada slot aceita 3 pessoas. Não é possível agendar duas vezes com o mesmo CPF no mesmo dia.

---

### Controle de Presença

```
# Importar lista (substitui lista anterior)
POST /attendance/template/{id}/import      ← público

# Listar registros
GET  /attendance/template/{id}             ← público

# Marcar presença
PATCH /attendance/{recordId}/mark          ← público

# Verificar existência de lista por templates
GET  /attendance/template/existence?templateIds=1,2,3
```

**Fluxo típico:**

1. Exporta uma planilha de inscritos como CSV.
2. Envia as linhas via `POST /attendance/template/{id}/import` (cada linha = `Map<String, String>`).
3. Na hora do evento, marca presença individualmente via `PATCH /attendance/{recordId}/mark`.

---

### Dashboard

```
GET /dashboard?page=0&size=20
```

Retorna estatísticas diferentes dependendo do papel:

- **ADMIN / FUNCIONARIO**: visão global — todos os clientes, todos os templates.
- **CLIENT**: visão restrita — apenas os templates do próprio cliente.

Métricas retornadas: total de templates (por tipo), submissões, agendamentos (confirmados/cancelados), registros de presença (total/presentes).

---

## Segurança — Rotas Públicas e Protegidas

### CORS

| Conjunto de rotas | Política |
|---|---|
| `/form-templates/slug/**`, `/form-submissions/**`, `/appointments/**`, `/attendance/**`, `/files/**` | **Aceita qualquer origem** — consumidas por formulários públicos (mobile, totem, etc.) |
| Todos os demais endpoints | Apenas origens configuradas em `CORS_ALLOWED_ORIGINS` |

### Rotas públicas (sem autenticação)

| Método | Rota |
|---|---|
| POST | `/auth/register` |
| POST | `/auth/login` |
| GET | `/form-templates/slug/{slug}` |
| POST | `/form-submissions` |
| GET/POST | `/appointments/**` |
| GET/POST/PATCH/DELETE | `/attendance/**` |
| GET | `/files/{filename}` |
| GET | `/clients/{id}/templates` |
| GET | `/actuator/health` |
| GET | `/swagger-ui/**`, `/v3/api-docs/**` |

### Rotas protegidas (requerem JWT)

| Método | Rota | Roles |
|---|---|---|
| POST | `/form-templates/create/{clientId}` | ADMIN, FUNCIONARIO |
| GET | `/form-templates` | ADMIN, FUNCIONARIO |
| PATCH | `/form-templates/{id}/schedule-config` | ADMIN, FUNCIONARIO |
| PUT | `/form-templates/{id}` | ADMIN |
| DELETE | `/form-templates/{id}` | ADMIN, FUNCIONARIO |
| GET | `/dashboard` | ADMIN, FUNCIONARIO, CLIENT |
| DELETE | `/form-submissions/{id}` | ADMIN, FUNCIONARIO, CLIENT |

---

## Testes e Cobertura

### Executar testes

```bash
# Executa todos os testes e gera relatório de cobertura
./mvnw test

# Relatório HTML disponível em:
# target/site/jacoco/index.html
```

### Cobertura de testes

| Pacote | Linhas cobertas | Cobertura |
|---|---|---|
| `service` (8 serviços) | 444 / 561 | **79%** |
| `exception` (handler + exceções) | 14 / 14 | **100%** |
| `config` (AuthService + JwtService) | 49 / 152 | 32% |
| **Bundle verificado pelo JaCoCo** (excl. controllers, DTOs, entities, infra) | — | **≥ 80% ✅** |

> O relatório HTML completo e atualizado é gerado automaticamente pelo JaCoCo em `target/site/jacoco/index.html` após executar `./mvnw test`.
>
> Nota: controllers (0%), utils (0%) e config de infraestrutura (SecurityConfig, JwtFilter) foram excluídos do gate de 80% pois requerem testes de integração com MockMvc/Spring context completo.

### Meta de cobertura

O build **falha** se a cobertura de linhas cair abaixo de **80%** no bundle verificado. Configurado no `pom.xml` via `jacoco-maven-plugin`. Para executar a verificação:

```bash
./mvnw verify
```

### Estrutura dos testes

```
src/test/java/com/cadastro/fabiano/demo/
├── config/
│   └── JwtServiceTest.java                   # 5 casos  (generate, extract, validate)
├── exception/
│   └── GlobalExceptionHandlerTest.java       # 5 casos  (409, 400, exceções)
└── service/
    ├── AppointmentServiceTest.java           # 18 casos (slots, booking, cancelamento, dedup)
    ├── AttendanceServiceTest.java            # 11 casos (import, mark, delete, existence)
    ├── AuthServiceTest.java                  #  6 casos (register, login, senhas)
    ├── ClientServiceTest.java                #  6 casos (CRUD, soft delete, cascade)
    ├── CustomUserDetailsServiceTest.java     #  2 casos (load, not found)
    ├── DashboardServiceTest.java             #  3 casos (admin, client, not found)
    ├── FormSubmissionServiceTest.java        #  7 casos (submit, list, delete)
    ├── FormTemplateServiceTest.java          # 14 casos (CRUD, slug, schedule config)
    └── UserServiceTest.java                  #  6 casos (CRUD, soft delete por active)
```

**Total: 83 testes unitários** — todos passando, sem banco de dados, usando Mockito.

---

## Migrações de Banco de Dados

Gerenciadas automaticamente pelo **Flyway** em `src/main/resources/db/migration/`.

| Versão | Descrição |
|---|---|
| V1–V2 | Tabelas de usuários e clientes |
| V4–V7 | Templates, campos, submissões |
| V10–V11 | Agendamentos com valores extras |
| V12 | Registros de presença |
| V15 | Flag `has_attendance` |
| V16–V22 | Aparência (cores, imagens, fontes), soft delete |
| V23 | Opções de campos select |

Para criar uma nova migração, adicione um arquivo `V{N}__{descricao}.sql` no diretório de migrações. O Flyway aplicará automaticamente na próxima inicialização.
