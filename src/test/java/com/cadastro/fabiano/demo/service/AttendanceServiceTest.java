package com.cadastro.fabiano.demo.service;

import com.cadastro.fabiano.demo.dto.request.ImportAttendanceRequest;
import com.cadastro.fabiano.demo.dto.request.MarkAttendanceRequest;
import com.cadastro.fabiano.demo.dto.response.AttendanceRecordResponse;
import com.cadastro.fabiano.demo.entity.AttendanceRecord;
import com.cadastro.fabiano.demo.entity.Client;
import com.cadastro.fabiano.demo.entity.FormTemplate;
import com.cadastro.fabiano.demo.repository.AttendanceRecordRepository;
import com.cadastro.fabiano.demo.repository.FormTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private AttendanceRecordRepository attendanceRepository;

    @Mock
    private FormTemplateRepository templateRepository;

    @InjectMocks
    private AttendanceService service;

    private FormTemplate template;

    @BeforeEach
    void setUp() {
        Client client = Client.builder().id(1L).name("Cliente").username("cliente").build();
        template = FormTemplate.builder()
                .id(1L)
                .name("Lista Presença")
                .slug("lista-presenca")
                .client(client)
                .hasAttendance(false)
                .build();
    }

    // ─── importAttendance ─────────────────────────────────────────────────────

    @Test
    @DisplayName("importAttendance: importa lista e retorna registros")
    void importAttendance_success() {
        List<Map<String, String>> rows = List.of(
                Map.of("Nome", "João", "CPF", "111"),
                Map.of("Nome", "Maria", "CPF", "222")
        );
        ImportAttendanceRequest request = new ImportAttendanceRequest(rows);

        AttendanceRecord r1 = buildRecord(1L, "João", false);
        AttendanceRecord r2 = buildRecord(2L, "Maria", false);

        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(attendanceRepository.saveAll(any())).thenReturn(List.of(r1, r2));
        when(templateRepository.save(any())).thenReturn(template);

        List<AttendanceRecordResponse> result = service.importAttendance(1L, request);

        assertThat(result).hasSize(2);
        verify(attendanceRepository).deleteByFormTemplate(template);
        assertThat(template.isHasAttendance()).isTrue();
    }

    @Test
    @DisplayName("importAttendance: linhas vazias são descartadas")
    void importAttendance_emptyRowsFiltered() {
        List<Map<String, String>> rows = List.of(
                Map.of("Nome", "João"),
                Map.of() // linha vazia — deve ser ignorada
        );
        ImportAttendanceRequest request = new ImportAttendanceRequest(rows);

        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(attendanceRepository.saveAll(any())).thenAnswer(inv -> {
            List<?> saved = inv.getArgument(0);
            assertThat(saved).hasSize(1);
            return saved;
        });
        when(templateRepository.save(any())).thenReturn(template);

        service.importAttendance(1L, request);
    }

    @Test
    @DisplayName("importAttendance: lança exceção se template não encontrado")
    void importAttendance_templateNotFound_throws() {
        when(templateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.importAttendance(99L, new ImportAttendanceRequest(List.of())))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("não encontrado");
    }

    // ─── getByTemplate ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getByTemplate: retorna página de registros ordenados")
    void getByTemplate_success() {
        AttendanceRecord r = buildRecord(1L, "Ana", false);
        PageRequest pageable = PageRequest.of(0, 10);

        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(attendanceRepository.findByFormTemplateOrderByRowOrderAscCreatedAtAsc(eq(template), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(r)));

        Page<AttendanceRecordResponse> result = service.getByTemplate(1L, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ─── markAttendance ───────────────────────────────────────────────────────

    @Test
    @DisplayName("markAttendance: marca presença com sucesso")
    void markAttendance_markPresent() {
        AttendanceRecord record = buildRecord(5L, "Carlos", false);
        MarkAttendanceRequest request = new MarkAttendanceRequest(true, "Presente");

        when(attendanceRepository.findById(5L)).thenReturn(Optional.of(record));
        when(attendanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AttendanceRecordResponse response = service.markAttendance(5L, request);

        assertThat(response.attended()).isTrue();
        assertThat(record.getAttendedAt()).isNotNull();
    }

    @Test
    @DisplayName("markAttendance: remove presença (attended=false limpa attendedAt)")
    void markAttendance_unmarkPresent() {
        AttendanceRecord record = buildRecord(5L, "Carlos", true);
        record.setAttendedAt(LocalDateTime.now());

        MarkAttendanceRequest request = new MarkAttendanceRequest(false, null);

        when(attendanceRepository.findById(5L)).thenReturn(Optional.of(record));
        when(attendanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markAttendance(5L, request);

        assertThat(record.isAttended()).isFalse();
        assertThat(record.getAttendedAt()).isNull();
    }

    @Test
    @DisplayName("markAttendance: lança exceção se registro não encontrado")
    void markAttendance_notFound_throws() {
        when(attendanceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAttendance(99L, new MarkAttendanceRequest(true, null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("não encontrado");
    }

    // ─── deleteRecord ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteRecord: exclui registro com sucesso")
    void deleteRecord_success() {
        AttendanceRecord record = buildRecord(3L, "Pedro", false);

        when(attendanceRepository.findById(3L)).thenReturn(Optional.of(record));

        service.deleteRecord(3L);

        verify(attendanceRepository).deleteById(3L);
    }

    @Test
    @DisplayName("deleteRecord: lança exceção se registro não encontrado")
    void deleteRecord_notFound_throws() {
        when(attendanceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteRecord(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("não encontrado");
    }

    // ─── attendanceExistsForTemplates ─────────────────────────────────────────

    @Test
    @DisplayName("attendanceExistsForTemplates: retorna mapa vazio para lista nula")
    void attendanceExistsForTemplates_nullList_returnsEmpty() {
        Map<Long, Boolean> result = service.attendanceExistsForTemplates(null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("attendanceExistsForTemplates: retorna mapa vazio para lista vazia")
    void attendanceExistsForTemplates_emptyList_returnsEmpty() {
        Map<Long, Boolean> result = service.attendanceExistsForTemplates(List.of());
        assertThat(result).isEmpty();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private AttendanceRecord buildRecord(Long id, String nome, boolean attended) {
        AttendanceRecord r = AttendanceRecord.builder()
                .id(id)
                .formTemplate(template)
                .rowData(Map.of("Nome", nome))
                .attended(attended)
                .rowOrder(id.intValue())
                .createdAt(LocalDateTime.now())
                .build();
        return r;
    }
}
