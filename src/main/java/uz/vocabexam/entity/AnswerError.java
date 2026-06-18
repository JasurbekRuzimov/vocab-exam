package uz.vocabexam.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "answer_errors")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AnswerError {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id", nullable = false)
    private OnlineAnswer answer;

    @Column(name = "error_start", nullable = false)
    private int errorStart;

    @Column(name = "error_end", nullable = false)
    private int errorEnd;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String comment;

    @Column(name = "correct_hint")
    private String correctHint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
