package com.cadastro.fabiano.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_template_id", nullable = false)
    private FormTemplate formTemplate;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "slot_time", nullable = false)
    private LocalTime slotTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    @Column(name = "booked_by_name")
    private String bookedByName;

    @Column(name = "booked_by_contact")
    private String bookedByContact;

    @Column(name = "cancelled_by")
    private String cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    // Valores extras do agendamento: sem batch, uma consulta por linha na
    // listagem de agendamentos.
    @ElementCollection
    @BatchSize(size = 100)
    @CollectionTable(
        name = "appointment_extra_values",
        joinColumns = @JoinColumn(name = "appointment_id")
    )
    @MapKeyColumn(name = "field_label")
    @Column(name = "field_value", columnDefinition = "TEXT")
    @Builder.Default
    private Map<String, String> extraValues = new HashMap<>();

    /**
     * Chave de deduplicação calculada no momento do booking.
     * Formato: "campo1=valor1|campo2=valor2" (campos ordenados alfabeticamente).
     * Null quando o template não possui dedupFields configurados.
     */
    @Column(name = "dedup_key", length = 1000)
    private String dedupKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = AppointmentStatus.AGENDADO;
        }
    }
}
