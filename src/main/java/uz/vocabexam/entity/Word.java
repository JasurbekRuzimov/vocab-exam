package uz.vocabexam.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity @Table(name = "words")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Word {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore // orqaga havola: JSON da cheksiz aylanishning oldini oladi
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "word_set_id", nullable = false)
    private WordSet wordSet;

    @Column(nullable = false)
    private String text;

    /** O'zbekcha tarjima; bir nechta variant vergul yoki ; bilan: "chiroyli, go'zal". */
    @Column
    private String translation;

    @Column(columnDefinition = "TEXT")
    private String example;
}
