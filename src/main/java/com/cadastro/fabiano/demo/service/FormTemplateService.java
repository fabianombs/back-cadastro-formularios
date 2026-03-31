package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.dto.request.CreateFormTemplateRequest;
import com.cadastro.fabiano.demo.dto.request.ScheduleConfigRequest;
import com.cadastro.fabiano.demo.dto.response.FormFieldResponse;
import com.cadastro.fabiano.demo.dto.response.FormTemplateResponse;
import com.cadastro.fabiano.demo.dto.response.ScheduleConfigResponse;
import com.cadastro.fabiano.demo.entity.Client;
import com.cadastro.fabiano.demo.entity.FormField;
import com.cadastro.fabiano.demo.entity.FormTemplate;
import com.cadastro.fabiano.demo.entity.User;
import com.cadastro.fabiano.demo.repository.ClientRepository;
import com.cadastro.fabiano.demo.repository.FormTemplateRepository;
import com.cadastro.fabiano.demo.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FormTemplateService {

    private final FormTemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;

    public FormTemplateService(FormTemplateRepository templateRepository,
                               UserRepository userRepository,
                               ClientRepository clientRepository) {
        this.templateRepository = templateRepository;
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
    }

    // ==========================
    // ADMIN - TODOS OS FORMS
    // ==========================
    public Page<FormTemplateResponse> findAllTemplates(Pageable pageable) {
        return templateRepository.findAll(pageable)
                .map(this::toResponse);
    }

    // ==========================
    // CLIENTE - MEUS FORMS
    // ==========================
    public Page<FormTemplateResponse> findTemplatesByUsername(String username, Pageable pageable) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        Client client = clientRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        return templateRepository.findByClient(client, pageable)
                .map(this::toResponse);
    }

    // ==========================
    // CRIAR TEMPLATE
    // ==========================
    @Transactional
    public FormTemplateResponse createTemplate(CreateFormTemplateRequest request, Long clientId) {

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        FormTemplate template = new FormTemplate();
        template.setName(request.name());
        template.setClient(client);

        template.setSlug(generateUniqueSlug(request.name()));

        // Configuração de agenda (opcional)
        if (request.scheduleConfig() != null) {
            ScheduleConfigRequest sc = request.scheduleConfig();
            template.setHasSchedule(true);
            template.setScheduleStartTime(sc.startTime());
            template.setScheduleEndTime(sc.endTime());
            template.setSlotDurationMinutes(sc.slotDurationMinutes());
            template.setMaxDaysAhead(sc.maxDaysAhead());
        }

        List<FormField> fields = request.fields().stream()
                .map(f -> {
                    FormField field = new FormField();
                    field.setLabel(f.label());
                    field.setType(f.type());
                    field.setRequired(f.required());
                    field.setFormTemplate(template);
                    return field;
                })
                .collect(java.util.stream.Collectors.toList());

        template.setFields(fields);

        FormTemplate saved = templateRepository.save(template);
        return toResponse(saved);
    }

    // ==========================
    // BUSCAR POR SLUG
    // ==========================
    public FormTemplateResponse findBySlug(String slug) {
        FormTemplate template = templateRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Template não encontrado"));

        return toResponse(template);
    }

    // ==========================
    // SLUG
    // ==========================
    private String generateSlug(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", "-");
    }

    private String generateUniqueSlug(String name) {
        String base = generateSlug(name);
        String slug = base;
        int count = 1;

        while (templateRepository.existsBySlug(slug)) {
            slug = base + "-" + count++;
        }

        return slug;
    }

    // ==========================
    // DTO
    // ==========================
    private FormTemplateResponse toResponse(FormTemplate template) {

        List<FormFieldResponse> fields = template.getFields().stream()
                .map(f -> new FormFieldResponse(
                        f.getId(),
                        f.getLabel(),
                        f.getType(),
                        f.isRequired()
                ))
                .toList();

        ScheduleConfigResponse scheduleConfig = null;
        if (template.isHasSchedule()) {
            scheduleConfig = new ScheduleConfigResponse(
                    template.getScheduleStartTime(),
                    template.getScheduleEndTime(),
                    template.getSlotDurationMinutes(),
                    template.getMaxDaysAhead()
            );
        }

        return new FormTemplateResponse(
                template.getId(),
                template.getName(),
                template.getSlug(),
                template.getClient().getName(),
                fields,
                template.isHasSchedule(),
                scheduleConfig
        );
    }
}