package uz.vocabexam.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** O'quv markazi: o'z foydalanuvchilari, brendi (nomi, logotipi, rangi) va holati bilan. */
@Entity @Table(name = "centers")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Center {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** Qisqa nom (lotin harflari): keyinchalik subdomen uchun, masalan ilm.examaker.uz. */
    @Column(nullable = false, unique = true, length = 50)
    private String slug;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Builder.Default
    @Column(name = "primary_color", nullable = false, length = 7)
    private String primaryColor = "#1E3A5F";

    /** O'chirilgan markaz foydalanuvchilari tizimga kira olmaydi (masalan obuna tugaganda). */
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
