package uz.vocabexam.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.*;

@Entity @Table(name = "online_answers")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OnlineAnswer {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private ExamAssignment assignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "word_id", nullable = false)
    private Word word;

    @Column(name = "answer_text", columnDefinition = "TEXT")
    private String answerText;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 30)
    private QuestionType questionType = QuestionType.SENTENCE;

    /** Avtomatik baholanadigan savollar uchun: to'g'ri/noto'g'ri. Gap tuzishda null. */
    @Column(name = "is_correct")
    private Boolean correct;

    @Column
    private Integer score;

    @Builder.Default
    @Column(name = "is_submitted", nullable = false)
    private boolean submitted = false;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Builder.Default
    @Column(name = "saved_at", nullable = false)
    private LocalDateTime savedAt = LocalDateTime.now();

    /** To'g'ri javob (gap tuzishda null) — topshirilgandan keyin o'qituvchi va o'quvchiga ko'rsatiladi. */
    @JsonIgnore
    public String getExpectedAnswer() {
        return switch (questionType) {
            case SENTENCE -> null;
            case TRANSLATE_UZ_EN -> word.getText();
            case TRANSLATE_EN_UZ, MULTIPLE_CHOICE -> word.getTranslation();
        };
    }

    /** Savolda o'quvchiga ko'rsatilgan matn. */
    @JsonIgnore
    public String getPrompt() {
        return questionType == QuestionType.TRANSLATE_UZ_EN ? word.getTranslation() : word.getText();
    }

    @Builder.Default
    @OneToMany(mappedBy = "answer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AnswerError> errors = new ArrayList<>();

    /** Javob matnining bir bo'lagi; {@code error} bo'lsa, bu bo'lak xato deb belgilangan. */
    public record Segment(String text, AnswerError error) {}

    /**
     * Javob matnini xatolar bo'yicha bo'laklarga ajratadi: natija sahifasida xato joylari
     * rangli ko'rsatiladi. Bir-birini qoplagan xatolardan birinchisi olinadi.
     */
    @JsonIgnore
    public List<Segment> getSegments() {
        List<Segment> out = new ArrayList<>();
        if (answerText == null || answerText.isEmpty()) return out;
        List<AnswerError> sorted = new ArrayList<>(errors);
        sorted.sort(Comparator.comparingInt(AnswerError::getErrorStart));
        int pos = 0, len = answerText.length();
        for (AnswerError e : sorted) {
            int start = Math.max(e.getErrorStart(), pos), end = Math.min(e.getErrorEnd(), len);
            if (start >= end) continue;
            if (start > pos) out.add(new Segment(answerText.substring(pos, start), null));
            out.add(new Segment(answerText.substring(start, end), e));
            pos = end;
        }
        if (pos < len) out.add(new Segment(answerText.substring(pos), null));
        return out;
    }
}