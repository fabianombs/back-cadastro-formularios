package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.dto.request.AddCompanionRequest;
import com.cadastro.fabiano.demo.dto.request.ImportAttendanceRequest;
import com.cadastro.fabiano.demo.dto.request.MarkAttendanceRequest;
import com.cadastro.fabiano.demo.dto.request.MarkCompanionAttendanceRequest;
import com.cadastro.fabiano.demo.dto.response.AttendanceCompanionResponse;
import com.cadastro.fabiano.demo.dto.response.AttendanceRecordResponse;
import com.cadastro.fabiano.demo.entity.AttendanceCompanion;
import com.cadastro.fabiano.demo.entity.AttendanceRecord;
import com.cadastro.fabiano.demo.entity.FormTemplate;
import com.cadastro.fabiano.demo.repository.AttendanceCompanionRepository;
import com.cadastro.fabiano.demo.repository.AttendanceRecordRepository;
import com.cadastro.fabiano.demo.repository.FormTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class AttendanceService {

    private final AttendanceRecordRepository attendanceRepository;
    private final AttendanceCompanionRepository companionRepository;
    private final FormTemplateRepository templateRepository;

    public AttendanceService(AttendanceRecordRepository attendanceRepository,
                             AttendanceCompanionRepository companionRepository,
                             FormTemplateRepository templateRepository) {
        this.attendanceRepository = attendanceRepository;
        this.companionRepository = companionRepository;
        this.templateRepository = templateRepository;
    }

    /**
     * Importa (ou reimporta) a lista de presença de um template.
     * <p>A operação é destrutiva: exclui todos os registros existentes do template
     * antes de inserir os novos, garantindo que a lista sempre reflita o CSV mais recente.
     * Linhas vazias são descartadas automaticamente.</p>
     *
     * @param templateId ID do template que receberá a lista
     * @param request    objeto contendo as linhas da lista (cada linha é um {@code Map<String,String>})
     * @return lista de {@link AttendanceRecordResponse} com os registros criados, em ordem de importação
     */
    @Transactional
    public List<AttendanceRecordResponse> importAttendance(Long templateId, ImportAttendanceRequest request) {
        FormTemplate template = findTemplate(templateId);
        attendanceRepository.deleteByFormTemplate(template);

        AtomicInteger order = new AtomicInteger(1);
        List<AttendanceRecord> records = request.rows().stream()
                .filter(row -> !row.isEmpty())
                .map(row -> {
                    // Detecta coluna de acompanhantes na planilha importada.
                    // Aceita variações comuns: "Acompanhantes", "Qtd Acompanhantes", "Nº Acompanhantes", etc.
                    int companions = extractCompanionsFromRow(row);
                    return AttendanceRecord.builder()
                            .formTemplate(template)
                            .rowData(row)
                            .attended(false)
                            .companionsCount(companions)
                            .rowOrder(order.getAndIncrement())
                            .build();
                })
                .toList();

        // Captura a ordem das colunas da primeira linha (Jackson desserializa em LinkedHashMap,
        // preservando a ordem do JSON enviado pelo frontend que leu o Excel).
        if (!request.rows().isEmpty()) {
            List<String> colOrder = new ArrayList<>(request.rows().get(0).keySet());
            template.setAttendanceColumnOrder(String.join(",", colOrder));
        }

        template.setHasAttendance(true);

        // Lista de presença é pública por natureza: garante um link de visualização
        // (/view/:token) sem login. Só gera se ainda não houver token, preservando
        // slugs personalizados. Escopado aqui para NÃO afetar quiz/formulário/agendamento.
        if (template.getViewToken() == null || template.getViewToken().isBlank()) {
            template.setViewToken(UUID.randomUUID().toString());
        }

        templateRepository.save(template);

        return attendanceRepository.saveAll(records)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public Page<AttendanceRecordResponse> getByTemplate(Long templateId, Pageable pageable) {
        FormTemplate template = findTemplate(templateId);
        Pageable sorted = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Order.asc("rowOrder"), Sort.Order.asc("createdAt"))
        );
        return attendanceRepository.findByFormTemplateOrderByRowOrderAscCreatedAtAsc(template, sorted)
                .map(this::toResponse);
    }

    /**
     * Atualiza o status de presença de um registro individual.
     * <p>Se {@code attended} for {@code true}, registra o timestamp atual em {@code attendedAt}.
     * Se for {@code false}, limpa o timestamp.</p>
     *
     * @param recordId ID do registro de presença
     * @param request  novo status (attended) e observações opcionais (notes)
     * @return registro atualizado
     * @throws RuntimeException se o registro não for encontrado
     */
    @Transactional
    public AttendanceRecordResponse markAttendance(Long recordId, MarkAttendanceRequest request) {
        AttendanceRecord record = attendanceRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("Registro não encontrado"));
        record.setAttended(request.attended());
        record.setNotes(request.notes());
        record.setAttendedAt(request.attended() ? LocalDateTime.now() : null);

        // Atualiza acompanhantes apenas quando informado explicitamente
        if (request.companionsCount() != null) {
            record.setCompanionsCount(Math.max(0, request.companionsCount()));
        }

        return toResponse(attendanceRepository.save(record));
    }

    @Transactional
    public AttendanceRecordResponse updateRowData(Long recordId, Map<String, String> rowData) {
        AttendanceRecord record = attendanceRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("Registro não encontrado"));
        record.setRowData(rowData);
        return toResponse(attendanceRepository.save(record));
    }

    @Transactional
    public void deleteRecord(Long recordId) {
        attendanceRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("Registro não encontrado"));
        attendanceRepository.deleteById(recordId);
    }

    public Map<Long, Boolean> attendanceExistsForTemplates(List<Long> templateIds) {
        if (templateIds == null || templateIds.isEmpty()) {
            return Map.of();
        }

        return attendanceRepository.countByTemplateIds(templateIds).stream()
                .collect(Collectors.toMap(
                        AttendanceRecordRepository.AttendanceCountByTemplate::getTemplateId,
                        count -> count.getAttendanceCount() > 0
                ));
    }

    /**
     * Adiciona um acompanhante ao convidado informado.
     * Atualiza o cache companions_count no registro pai.
     */
    @Transactional
    public AttendanceRecordResponse addCompanion(Long recordId, AddCompanionRequest request) {
        AttendanceRecord record = attendanceRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("Registro não encontrado"));

        AttendanceCompanion companion = AttendanceCompanion.builder()
                .attendanceRecord(record)
                .name(request.name().trim())
                .phone(request.phone() != null ? request.phone().trim() : null)
                .build();
        companionRepository.save(companion);

        // Atualiza cache de contagem para facilitar stats
        record.setCompanionsCount(record.getCompanionsCount() + 1);
        attendanceRepository.save(record);

        return toResponse(record);
    }

    /**
     * Marca ou desmarca a presença de um acompanhante individualmente.
     * Retorna o registro pai atualizado (com a lista de companions).
     */
    @Transactional
    public AttendanceRecordResponse markCompanionAttendance(Long companionId, MarkCompanionAttendanceRequest request) {
        AttendanceCompanion companion = companionRepository.findById(companionId)
                .orElseThrow(() -> new RuntimeException("Acompanhante não encontrado"));

        companion.setAttended(request.attended());
        companion.setAttendedAt(request.attended() ? LocalDateTime.now() : null);
        companionRepository.save(companion);

        return toResponse(companion.getAttendanceRecord());
    }

    /**
     * Remove um acompanhante pelo id e atualiza o cache companions_count no pai.
     */
    @Transactional
    public AttendanceRecordResponse removeCompanion(Long companionId) {
        AttendanceCompanion companion = companionRepository.findById(companionId)
                .orElseThrow(() -> new RuntimeException("Acompanhante não encontrado"));

        AttendanceRecord record = companion.getAttendanceRecord();
        companionRepository.delete(companion);

        // Garante que o cache não fique negativo
        record.setCompanionsCount(Math.max(0, record.getCompanionsCount() - 1));
        attendanceRepository.save(record);

        return toResponse(record);
    }

    /**
     * Permite ao organizador/cliente adicionar um novo convidado diretamente
     * via link público, sem precisar importar planilha novamente.
     * O rowOrder é calculado como total atual + 1 para manter a ordem de chegada.
     */
    @Transactional
    public AttendanceRecordResponse addPublicGuest(String slug, Map<String, String> rowData) {
        FormTemplate template = templateRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Formulário não encontrado"));

        long nextOrder = attendanceRepository.countByFormTemplate(template) + 1;

        AttendanceRecord record = AttendanceRecord.builder()
                .formTemplate(template)
                .rowData(rowData)
                .attended(false)
                .companionsCount(0)
                .rowOrder((int) nextOrder)
                .build();

        return toResponse(attendanceRepository.save(record));
    }

    private FormTemplate findTemplate(Long templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template não encontrado"));
    }

    /**
     * Tenta ler a quantidade de acompanhantes de uma linha da planilha importada.
     * Busca por chaves que comecem com "acompan" (case-insensitive) para cobrir
     * variações como "Acompanhantes", "Qtd Acompanhantes", "Nº Acompanhantes", etc.
     */
    private int extractCompanionsFromRow(Map<String, String> row) {
        return row.entrySet().stream()
                .filter(e -> e.getKey().trim().toLowerCase().contains("acompan"))
                .findFirst()
                .map(e -> {
                    try {
                        return Math.max(0, Integer.parseInt(e.getValue().trim()));
                    } catch (NumberFormatException ex) {
                        return 0;
                    }
                })
                .orElse(0);
    }

    private AttendanceRecordResponse toResponse(AttendanceRecord r) {
        List<AttendanceCompanionResponse> companions = companionRepository
                .findByAttendanceRecordOrderByCreatedAtAsc(r)
                .stream()
                .map(c -> new AttendanceCompanionResponse(
                        c.getId(),
                        r.getId(),
                        c.getName(),
                        c.getPhone(),
                        c.isAttended(),
                        c.getAttendedAt(),
                        c.getCreatedAt()
                ))
                .toList();

        return new AttendanceRecordResponse(
                r.getId(),
                r.getFormTemplate().getId(),
                r.getRowData(),
                r.isAttended(),
                r.getAttendedAt(),
                r.getNotes(),
                companions.size(),
                companions,
                r.getRowOrder(),
                r.getCreatedAt()
        );
    }
}
