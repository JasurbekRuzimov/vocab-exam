package uz.vocabexam.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.*;

@Entity @Table(name = "exams")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Exam {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExamType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "word_set_id", nullable = false)
    private WordSet wordSet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Builder.Default
    @Column(name = "variant_count", nullable = false)
    private int variantCount = 1;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExamStatus status = ExamStatus.DRAFT;

    /** Savol turlari, vergul bilan saqlanadi (masalan "MULTIPLE_CHOICE,TRANSLATE_EN_UZ"). */
    @Builder.Default
    @Column(name = "question_types", nullable = false, length = 200)
    private String questionTypes = QuestionType.SENTENCE.name();

    public List<QuestionType> getQuestionTypeList() {
        return Arrays.stream(questionTypes.split(","))
            .map(String::trim).filter(s -> !s.isEmpty())
            .map(QuestionType::valueOf).toList();
    }

    public void setQuestionTypeList(Collection<QuestionType> types) {
        this.questionTypes = String.join(",", types.stream().distinct().map(Enum::name).toList());
    }

    @Builder.Default
    @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExamVariant> variants = new ArrayList<>();

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}