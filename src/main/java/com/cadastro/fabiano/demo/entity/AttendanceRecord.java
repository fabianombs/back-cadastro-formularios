package com.cadastro.fabiano.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "attendance_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_template_id", nullable = false)
    private FormTemplate formTemplate;

    @ElementCollection
    @CollectionTable(
        name = "attendance_record_data",
        joinColumns = @JoinColumn(name = "record_id")
    )
    @MapKeyColumn(name = "col_key")
    @Column(name = "col_value", columnDefinition = "TEXT")
    @Builder.Default
    private Map<String, String> rowData = new HashMap<>();

    @Column(nullable = false)
    @Builder.Default
    private boolean attended = false;

    @Column(name = "attended_at")
    private LocalDateTime attendedAt;

    @Column(name = "notes")
    private String notes;

    // Quantidade de acompanhantes que o convidado trouxe ao evento
    @Column(name = "companions_count", nullable = false)
    @Builder.Default
    private int companionsCount = 0;

    @Column(name = "row_order")
    private Integer rowOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
