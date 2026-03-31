package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.dto.request.CreateFormSubmissionRequest;
import com.cadastro.fabiano.demo.dto.response.FormSubmissionResponse;
import com.cadastro.fabiano.demo.entity.FormSubmission;
import com.cadastro.fabiano.demo.entity.FormTemplate;
import com.cadastro.fabiano.demo.repository.FormSubmissionRepository;
import com.cadastro.fabiano.demo.repository.FormTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FormSubmissionService {

    private final FormSubmissionRepository submissionRepository;
    private final FormTemplateRepository templateRepository;

    public FormSubmissionService(FormSubmissionRepository submissionRepository,
                                 FormTemplateRepository templateRepository) {
        this.submissionRepository = submissionRepository;
        this.templateRepository = templateRepository;
    }

    // =========================
    // CRIAR SUBMISSÃO
    // =========================
    @Transactional
    public FormSubmissionResponse submitForm(CreateFormSubmissionRequest request) {

        FormTemplate template = templateRepository.findById(request.templateId())
                .orElseThrow(() -> new RuntimeException("Template não encontrado"));

        FormSubmission submission = FormSubmission.builder()
                .template(template)
                .values(request.values())
                .build();

        submissionRepository.save(submission);

        return toResponse(submission);
    }

    // =========================
    // LISTAR POR TEMPLATE ID
    // =========================
    public Page<FormSubmissionResponse> getSubmissionsByTemplate(Long templateId, Pageable pageable) {
        return submissionRepository.findByTemplate_Id(templateId, pageable)
                .map(this::toResponse);
    }

    // =========================
    // LISTAR POR SLUG
    // =========================
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
                s.getValues(),
                s.getCreatedAt()
        );
    }
}