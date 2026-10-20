package uz.vocabexam.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.util.*;

@Entity @Table(name = "exam_variants")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExamVariant {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore // orqaga havola: JSON da cheksiz aylanishning oldini oladi
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @Column(name = "variant_number", nullable = false)
    private int variantNumber;

    @Column(name = "pdf_path")
    private String pdfPath;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "exam_variant_words",
        joinColumns = @JoinColumn(name = "variant_id"),
        inverseJoinColumns = @JoinColumn(name = "word_id"))
    @OrderColumn(name = "position")
    private List<Word> words = new ArrayList<>();
}
