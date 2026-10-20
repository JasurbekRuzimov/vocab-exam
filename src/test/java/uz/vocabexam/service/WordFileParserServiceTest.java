package uz.vocabexam.service;

import org.junit.jupiter.api.Test;
import uz.vocabexam.service.WordFileParserService.ParsedWord;
import static org.assertj.core.api.Assertions.assertThat;

class WordFileParserServiceTest {

    @Test
    void parsesWordTranslationAndExample() {
        assertThat(WordFileParserService.parseLine("Travel - sayohat qilmoq | I love to travel."))
            .isEqualTo(new ParsedWord("travel", "sayohat qilmoq", "I love to travel."));
        assertThat(WordFileParserService.parseLine("brave — jasur"))
            .isEqualTo(new ParsedWord("brave", "jasur", null));
        assertThat(WordFileParserService.parseLine("river\tdaryo"))
            .isEqualTo(new ParsedWord("river", "daryo", null));
        assertThat(WordFileParserService.parseLine("honest: halol, rostgo'y"))
            .isEqualTo(new ParsedWord("honest", "halol, rostgo'y", null));
    }

    @Test
    void plainWordAndHyphenatedWordStayWhole() {
        assertThat(WordFileParserService.parseLine("1. apple")).isEqualTo(new ParsedWord("apple", null, null));
        assertThat(WordFileParserService.parseLine("well-known")).isEqualTo(new ParsedWord("well-known", null, null));
        assertThat(WordFileParserService.parseLine("   ")).isNull();
    }
}
