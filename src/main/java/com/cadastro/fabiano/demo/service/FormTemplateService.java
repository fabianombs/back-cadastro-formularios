package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.dto.request.CreateFormTemplateRequest;
import com.cadastro.fabiano.demo.dto.response.FormFieldResponse;
import com.cadastro.fabiano.demo.dto.response.FormTemplateResponse;
import com.cadastro.fabiano.demo.entity.FormField;
import com.cadastro.fabiano.demo.entity.FormTemplate;
import com.cadastro.fabiano.demo.entity.User;
import com.cadastro.fabiano.demo.repository.FormTemplateRepository;
import com.cadastro.fabiano.demo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FormTemplateService {

    private final FormTemplateRepository templateRepository;
    private final UserRepository userRepository;

    public FormTemplateService(FormTemplateRepository templateRepository,
                               UserRepository userRepository) {
        this.templateRepository = templateRepository;
        this.userRepository = userRepository;
    }

    // Lista templates do usuário logado
    public List<FormTemplateResponse> findTemplatesByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
        return templateRepository.findByClient(user).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // Lista todos os templates (ADMIN)
    public List<FormTemplateResponse> findAllTemplates() {
        return templateRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // Cria template e associa a um cliente (ADMIN)
    @Transactional
    public FormTemplateResponse createTemplate(CreateFormTemplateRequest request, Long clientId) {
        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        FormTemplate template = new FormTemplate();
        template.setName(request.name());
        template.setClient(client);

        // Cria campos e associa ao template
        List<FormField> fields = request.fields().stream()
                .map(f -> {
                    FormField field = new FormField();
                    field.setLabel(f.label());
                    field.setType(f.type());
                    field.setFormTemplate(template); // importante: evita null no form_template_id
                    return field;
                })
                .collect(Collectors.toList());

        template.setFields(fields);

        FormTemplate savedTemplate = templateRepository.save(template);
        return toResponse(savedTemplate);
    }

    // Mapeia FormTemplate -> FormTemplateResponse
    private FormTemplateResponse toResponse(FormTemplate template) {
        List<FormFieldResponse> fields = template.getFields().stream()
                .map(f -> new FormFieldResponse(f.getId(), f.getLabel(), f.getType()))
                .collect(Collectors.toList());
        return new FormTemplateResponse(template.getId(), template.getName(), fields);
    }
}