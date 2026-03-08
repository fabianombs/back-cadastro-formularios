package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.dto.request.CreateFormSubmissionRequest;
import com.cadastro.fabiano.demo.dto.response.FormSubmissionResponse;
import com.cadastro.fabiano.demo.entity.FormSubmission;
import com.cadastro.fabiano.demo.entity.FormTemplate;
import com.cadastro.fabiano.demo.repository.FormSubmissionRepository;
import com.cadastro.fabiano.demo.repository.FormTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FormSubmissionService {

    private final FormSubmissionRepository submissionRepository;
    private final FormTemplateRepository templateRepository;

    public FormSubmissionService(FormSubmissionRepository submissionRepository,
                                 FormTemplateRepository templateRepository) {
        this.submissionRepository = submissionRepository;
        this.templateRepository = templateRepository;
    }

    @Transactional
    public FormSubmissionResponse submitForm(CreateFormSubmissionRequest request) {
        FormTemplate template = templateRepository.findById(request.templateId())
                .orElseThrow(() -> new RuntimeException("Template não encontrado"));

        FormSubmission submission = FormSubmission.builder()
                .template(template)
                .values(request.values())
                .build();

        submissionRepository.save(submission);

        return new FormSubmissionResponse(
                submission.getId(),
                template.getId(),
                submission.getValues(),
                submission.getCreatedAt()
        );
    }

    public List<FormSubmissionResponse> getSubmissionsByTemplate(Long templateId) {
        return submissionRepository.findByTemplate_Id(templateId).stream()
                .map(s -> new FormSubmissionResponse(
                        s.getId(),
                        s.getTemplate().getId(),
                        s.getValues(),
                        s.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }
}