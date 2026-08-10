package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.dto.request.CreateFormSubmissionRequest;
import com.cadastro.fabiano.demo.dto.response.FormSubmissionResponse;
import com.cadastro.fabiano.demo.config.MetricasDeNegocio;
import com.cadastro.fabiano.demo.entity.FormSubmission;
import com.cadastro.fabiano.demo.entity.FormTemplate;
import com.cadastro.fabiano.demo.repository.FormSubmissionRepository;
import com.cadastro.fabiano.demo.repository.FormTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cadastro.fabiano.demo.utils.ColecaoDeSaida;

@Service
public class FormSubmissionService {

    private final FormSubmissionRepository submissionRepository;
    private final FormTemplateRepository templateRepository;
    private final MetricasDeNegocio metricas;

    public FormSubmissionService(FormSubmissionRepository submissionRepository,
                                 FormTemplateRepository templateRepository,
                                 MetricasDeNegocio metricas) {
        this.submissionRepository = submissionRepository;
        this.templateRepository = templateRepository;
        this.metricas = metricas;
    }

    // =========================
    // CRIAR SUBMISSÃO
    // =========================
    @Transactional
    public FormSubmissionResponse submitForm(CreateFormSubmissionRequest request) {
        try {
            FormTemplate template = templateRepository.findById(request.templateId())
                    .orElseThrow(() -> new RuntimeException("Template não encontrado"));

            FormSubmission submission = FormSubmission.builder()
                    .template(template)
                    .values(request.values())
                    .build();

            submissionRepository.save(submission);

            metricas.submissaoRecebida();
            return toResponse(submission);
        } catch (RuntimeException e) {
            // Conta e relanca: o comportamento visto de fora continua identico,
            // so passa a deixar rastro. Queda de submissao com abertura normal
            // e o sinal de formulario quebrado.
            metricas.submissaoFalhou();
            throw e;
        }
    }

    // =========================
    // LISTAR POR TEMPLATE ID
    // =========================
    // readOnly = true nao e otimizacao: e o que mantem a sessao aberta enquanto
    // o toResponse le 'values', que e @ElementCollection e portanto preguicosa.
    // Sem isto, com spring.jpa.open-in-view=false a sessao fecha ao voltar do
    // repositorio e o .map() estoura com LazyInitializationException — que o
    // handler generico converte em HTTP 400, ou seja, "voce errou a requisicao"
    // numa rota publica que estava certa. Medido em homolog, 10/08/2026
    // (FABIANO-37).
    @Transactional(readOnly = true)
    public Page<FormSubmissionResponse> getSubmissionsByTemplate(Long templateId, Pageable pageable) {
        return submissionRepository.findByTemplate_Id(templateId, pageable)
                .map(this::toResponse);
    }

    // =========================
    // LISTAR POR SLUG
    // =========================
    // Mesmo motivo do metodo acima: o .map() abaixo toca a colecao preguicosa.
    @Transactional(readOnly = true)
    public Page<FormSubmissionResponse> getSubmissionsBySlug(String slug, Pageable pageable) {

        FormTemplate template = templateRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Template não encontrado"));

        return submissionRepository.findByTemplate_Id(template.getId(), pageable)
                .map(this::toResponse);
    }

    // =========================
    // DELETAR SUBMISSÃO
    // =========================
    @Transactional
    public void deleteSubmission(Long id) {
        FormSubmission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Resposta não encontrada"));
        submissionRepository.delete(submission);
    }

    // =========================
    // MAPPER
    // =========================
    private FormSubmissionResponse toResponse(FormSubmission s) {
        return new FormSubmissionResponse(
                s.getId(),
                s.getTemplate().getId(),
                ColecaoDeSaida.mapa(s.getValues()),
                s.getCreatedAt()
        );
    }
}