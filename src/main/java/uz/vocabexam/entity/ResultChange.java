package uz.vocabexam.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** E'lon qilingan natijadagi ball o'zgarishi: o'quvchiga ham ko'rsatiladi. */
@Entity @Table(name = "result_changes")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ResultChange {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_id", nullable = false)
    private ExamResult result;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy;

    @Column(name = "old_score", nullable = false) private int oldScore;
    @Column(name = "old_max",   nullable = false) private int oldMax;
    @Column(name = "new_score", nullable = false) private int newScore;
    @Column(name = "new_max",   nullable = false) private int newMax;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Builder.Default
    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt = LocalDateTime.now();

    /** O'zgartirgan o'qituvchining ismi (o'quvchiga ko'rsatish uchun). */
    public String getChangedByName() {
        return changedBy != null ? changedBy.getFullName() : null;
    }
}
