package uz.vocabexam.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AnswerGraderTest {

    @Test
    void acceptsAnyVariantIgnoringCaseSpacesAndPunctuation() {
        assertThat(AnswerGrader.isCorrect("  Chiroyli! ", "chiroyli, go'zal")).isTrue();
        assertThat(AnswerGrader.isCorrect("go'zal", "chiroyli, go'zal")).isTrue();
        assertThat(AnswerGrader.isCorrect("sayohat  qilmoq", "sayohat qilmoq; safar")).isTrue();
        assertThat(AnswerGrader.isCorrect("xunuk", "chiroyli, go'zal")).isFalse();
    }

    @Test
    void treatsUzbekApostropheFormsAsEqual() {
        assertThat(AnswerGrader.isCorrect("go‘zal", "go'zal")).isTrue();
        assertThat(AnswerGrader.isCorrect("goʻzal", "go'zal")).isTrue();
        assertThat(AnswerGrader.isCorrect("go`zal", "go’zal")).isTrue();
    }

    @Test
    void allowsOneTypoOnlyForLongerWords() {
        assertThat(AnswerGrader.isCorrect("chirolyi", "chiroyli")).isFalse();   // 2 ta farq
        assertThat(AnswerGrader.isCorrect("chiroyl", "chiroyli")).isTrue();     // 1 harf tushib qolgan
        assertThat(AnswerGrader.isCorrect("olm", "olma")).isFalse();            // qisqa so'zda yumshoqlik yo'q
    }

    @Test
    void emptyAnswerIsNeverCorrect() {
        assertThat(AnswerGrader.isCorrect("", "olma")).isFalse();
        assertThat(AnswerGrader.isCorrect(null, "olma")).isFalse();
        assertThat(AnswerGrader.isExactChoice(null, "olma")).isFalse();
    }

    @Test
    void choiceMustMatchExactly() {
        assertThat(AnswerGrader.isExactChoice("daryo", "daryo")).isTrue();
        assertThat(AnswerGrader.isExactChoice("dary", "daryo")).isFalse();
    }
}
