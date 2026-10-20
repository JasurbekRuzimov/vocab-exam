package uz.vocabexam.service;

import uz.vocabexam.entity.QuestionType;
import uz.vocabexam.entity.Word;
import java.util.List;

/**
 * O'quvchiga ko'rsatiladigan bitta savol.
 *
 * @param prompt   ekranda ko'rinadigan so'z (UZ → EN da tarjima, qolganlarida inglizcha so'z)
 * @param options  faqat test savolida: 4 ta tarjima varianti
 * @param expected to'g'ri javob (o'quvchiga topshirishdan oldin ko'rsatilmaydi)
 */
public record ExamQuestion(Word word, QuestionType type, String prompt, List<String> options, String expected) {

    public String instruction() {
        return switch (type) {
            case SENTENCE        -> "Shu so'z bilan inglizcha gap tuzing";
            case TRANSLATE_EN_UZ -> "O'zbekchaga tarjima qiling";
            case TRANSLATE_UZ_EN -> "Inglizchaga tarjima qiling";
            case MULTIPLE_CHOICE -> "To'g'ri tarjimani tanlang";
        };
    }
}
