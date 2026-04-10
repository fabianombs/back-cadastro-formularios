package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.dto.request.ImportAttendanceRequest;
import com.cadastro.fabiano.demo.dto.request.MarkAttendanceRequest;
import com.cadastro.fabiano.demo.dto.response.AttendanceRecordResponse;
import com.cadastro.fabiano.demo.entity.AttendanceRecord;
import com.cadastro.fabiano.demo.entity.FormTemplate;
import com.cadastro.fabiano.demo.repository.AttendanceRecordRepository;
import com.cadastro.fabiano.demo.repository.FormTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class AttendanceService {

    private final AttendanceRecordRepository attendanceRepository;
    private final FormTemplateRepository templateRepository;

    public AttendanceService(AttendanceRecordRepository attendanceRepository,
                             FormTemplateRepository templateRepository) {
        this.attendanceRepository = attendanceRepository;
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
                .map(row -> AttendanceRecord.builder()
                        .formTemplate(template)
                        .rowData(row)
                        .attended(false)
                        .rowOrder(order.getAndIncrement())
                        .build())
                .toList();

        template.setHasAttendance(true);
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

    private FormTemplate findTemplate(Long templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template não encontrado"));
    }

    private AttendanceRecordResponse toResponse(AttendanceRecord r) {
        return new AttendanceRecordResponse(
                r.getId(),
                r.getFormTemplate().getId(),
                r.getRowData(),
                r.isAttended(),
                r.getAttendedAt(),
                r.getNotes(),
                r.getRowOrder(),
                r.getCreatedAt()
        );
    }
}
