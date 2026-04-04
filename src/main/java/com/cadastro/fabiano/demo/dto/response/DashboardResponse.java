package com.cadastro.fabiano.demo.dto.response;

import java.util.List;

public record DashboardResponse(
        long totalTemplates,
        long totalClients,
        long totalSubmissions,
        long totalAppointments,
        long confirmedAppointments,
        long cancelledAppointments,
        long totalAttendanceRecords,
        long presentAttendanceRecords,
        long formTemplateCount,
        long appointmentTemplateCount,
        long attendanceTemplateCount,
        List<TemplateStatResponse> templates,
        int page,
        int size,
        long totalElements,
        int totalPages,
        // Totais globais para evitar segunda chamada
        long globalTotalSubmissions,
        long globalTotalAppointments,
        long globalConfirmedAppointments,
        long globalCancelledAppointments,
        long globalTotalAttendanceRecords,
        long globalPresentAttendanceRecords
) {}
