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

@Service
public class AttendanceService {

    private final AttendanceRecordRepository attendanceRepository;
    private final FormTemplateRepository templateRepository;

    public AttendanceService(AttendanceRecordRepository attendanceRepository,
                             FormTemplateRepository templateRepository) {
        this.attendanceRepository = attendanceRepository;
        this.templateRepository = templateRepository;
    }

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
