package com.cadastro.fabiano.demo.config;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Ponto unico de registro das metricas de negocio (FABIANO-25).
 *
 * Existe como componente, e nao como chamadas soltas de MeterRegistry pelos
 * servicos, por tres motivos praticos:
 *
 * 1. Nome de metrica escrito a mao em oito arquivos e nome errado em um deles
 *    mais cedo ou mais tarde - e o painel que consulta o nome certo devolve
 *    vazio sem dar erro.
 * 2. A regra de cardinalidade fica em um lugar so. O motivo da recusa de
 *    agendamento e um enum: nao ha como passar id de template por engano.
 * 3. Todos os metodos daqui devolvem void. Nos testes, um mock desta classe
 *    nao faz nada e nao quebra nada - se fosse MeterRegistry direto, o mock
 *    devolveria null em counter() e estouraria NullPointerException.
 *
 * Convencao de nome: ponto, nunca underline, e SEM sufixo de unidade. O
 * Micrometer traduz o ponto para underline e acrescenta o sufixo certo no
 * Prometheus - "_total" para contador, "_seconds" para timer. Um timer
 * chamado "upload.duracao.segundos" viraria "upload_duracao_segundos_seconds".
 */
@Component
public class MetricasDeNegocio {

    private static final String SUBMISSAO = "formulario.submissao";
    private static final String TEMPLATE = "formulario.template";
    private static final String AGENDAMENTO = "agendamento";
    private static final String AGENDAMENTO_RECUSADO = "agendamento.recusado";
    private static final String AGENDAMENTO_LOCK = "agendamento.lock.espera";
    private static final String PRESENCA_IMPORTACAO = "presenca.importacao";
    private static final String PRESENCA_IMPORTACAO_LINHAS = "presenca.importacao.linhas";
    private static final String PRESENCA_MARCACAO = "presenca.marcacao";
    private static final String UPLOAD = "upload.imagem";
    private static final String UPLOAD_DURACAO = "upload.imagem.duracao";
    private static final String UPLOAD_BYTES = "upload.imagem.bytes";
    private static final String ERRO_TRATADO = "erro.tratado";

    /**
     * Motivos possiveis de recusa de agendamento, como enum e nao como String.
     *
     * Cada recusa conta uma historia diferente, e "slot_lotado" recorrente no
     * mesmo horario e informacao comercial - o cliente precisa de mais
     * capacidade. Do lado de fora todos viram o mesmo 400 ou 409, e sem este
     * rotulo nao ha como distinguir um do outro depois do fato.
     */
    public enum MotivoDeRecusa {
        SEM_AGENDA("sem_agenda"),
        DATA_PASSADA("data_passada"),
        DATA_MUITO_DISTANTE("data_muito_distante"),
        HORARIO_INVALIDO("horario_invalido"),
        DUPLICADO("duplicado"),
        SLOT_LOTADO("slot_lotado");

        private final String rotulo;

        MotivoDeRecusa(String rotulo) {
            this.rotulo = rotulo;
        }
    }

    private final MeterRegistry registro;

    public MetricasDeNegocio(MeterRegistry registro) {
        this.registro = registro;
    }

    // ------------------------------------------------------------------
    // Formulario
    // ------------------------------------------------------------------

    public void submissaoRecebida() {
        contar(SUBMISSAO, "resultado", "sucesso");
    }

    public void submissaoFalhou() {
        contar(SUBMISSAO, "resultado", "erro");
    }

    public void templateCriado() {
        contar(TEMPLATE, "evento", "criado");
    }

    public void templateEditado() {
        contar(TEMPLATE, "evento", "editado");
    }

    public void templateExcluido() {
        contar(TEMPLATE, "evento", "excluido");
    }

    // ------------------------------------------------------------------
    // Agendamento
    // ------------------------------------------------------------------

    public void agendamentoCriado() {
        contar(AGENDAMENTO, "evento", "criado");
    }

    public void agendamentoCancelado() {
        contar(AGENDAMENTO, "evento", "cancelado");
    }

    public void agendamentoRecusado(MotivoDeRecusa motivo) {
        contar(AGENDAMENTO_RECUSADO, "motivo", motivo.rotulo);
    }

    /**
     * Tempo gasto para obter o lock pessimista do template.
     *
     * Sob concorrencia no mesmo evento, este numero e o que separa "o sistema
     * esta lento" de "duas pessoas tentaram agendar o mesmo horario ao mesmo
     * tempo" - que sao problemas completamente diferentes.
     */
    public void esperaPeloLock(long nanos) {
        registro.timer(AGENDAMENTO_LOCK).record(nanos, TimeUnit.NANOSECONDS);
    }

    // ------------------------------------------------------------------
    // Presenca
    // ------------------------------------------------------------------

    public void importacaoDePresenca(int linhas) {
        contar(PRESENCA_IMPORTACAO, "resultado", "sucesso");
        DistributionSummary.builder(PRESENCA_IMPORTACAO_LINHAS)
                .description("Quantidade de linhas por importacao de presenca")
                .register(registro)
                .record(linhas);
    }

    public void importacaoDePresencaFalhou() {
        contar(PRESENCA_IMPORTACAO, "resultado", "erro");
    }

    public void presencaMarcada() {
        contar(PRESENCA_MARCACAO, "tipo", "titular");
    }

    public void presencaDeAcompanhanteMarcada() {
        contar(PRESENCA_MARCACAO, "tipo", "acompanhante");
    }

    public void convidadoIncluidoPeloPublico() {
        contar(PRESENCA_MARCACAO, "tipo", "convidado_publico");
    }

    // ------------------------------------------------------------------
    // Upload de imagem
    // ------------------------------------------------------------------

    public void uploadConcluido(long nanos, long bytes) {
        contar(UPLOAD, "resultado", "sucesso");
        registro.timer(UPLOAD_DURACAO).record(nanos, TimeUnit.NANOSECONDS);
        // Sem o tamanho, uma subida no tempo de upload e ambigua: pode ser o
        // armazenamento degradado ou apenas arquivo maior.
        DistributionSummary.builder(UPLOAD_BYTES)
                .baseUnit("bytes")
                .description("Tamanho das imagens enviadas")
                .register(registro)
                .record(bytes);
    }

    public void uploadFalhou(long nanos) {
        contar(UPLOAD, "resultado", "erro");
        registro.timer(UPLOAD_DURACAO).record(nanos, TimeUnit.NANOSECONDS);
    }

    // ------------------------------------------------------------------
    // Erros tratados
    // ------------------------------------------------------------------

    /**
     * Erro que a aplicacao tratou e devolveu como resposta de negocio.
     *
     * Nao aparece como 5xx e nao entra na taxa de erro do dashboard, mas um
     * pico de 409 significa que algo mudou no comportamento dos usuarios - ou
     * no sistema. O rotulo "tipo" e o nome simples da excecao, conjunto
     * fechado pelas classes que existem no projeto; ainda assim ha um teto de
     * cardinalidade configurado no ObservabilityConfig como rede de seguranca.
     */
    public void erroTratado(String tipoDaExcecao, int status) {
        registro.counter(ERRO_TRATADO,
                "tipo", tipoDaExcecao,
                "status", String.valueOf(status)).increment();
    }

    private void contar(String metrica, String chave, String valor) {
        registro.counter(metrica, chave, valor).increment();
    }
}
