package uz.vocabexam.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import uz.vocabexam.exception.BadRequestException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * So'zlar faylini o'qiydi. Har satrda bitta so'z, ixtiyoriy tarjima va misol gap bilan:
 * <pre>
 * apple
 * brave - jasur
 * travel — sayohat qilmoq | I love to travel.
 * river\tdaryo
 * </pre>
 * Ajratuvchi: tab, " - ", " – ", " — ", " = " yoki ":"; misol gap "|" dan keyin.
 */
@Service @Slf4j
public class WordFileParserService {

    public record ParsedWord(String text, String translation, String example) {}

    private static final Pattern SEPARATOR = Pattern.compile("\\t|\\s+[-–—=]\\s+|\\s*:\\s+");

    public List<ParsedWord> parse(MultipartFile file) {
        String filename = Objects.requireNonNull(file.getOriginalFilename()).toLowerCase();
        List<String> lines;
        if (filename.endsWith(".txt"))       lines = readTxt(file);
        else if (filename.endsWith(".docx")) lines = readDocx(file);
        else throw new BadRequestException("Faqat .txt va .docx formatlar qabul qilinadi");
        return lines.stream().map(WordFileParserService::parseLine).filter(Objects::nonNull).toList();
    }

    static ParsedWord parseLine(String line) {
        String raw = line == null ? "" : line.trim();
        if (raw.isEmpty()) return null;

        String example = null;
        int bar = raw.indexOf('|');
        if (bar >= 0) {
            example = blankToNull(raw.substring(bar + 1).trim());
            raw = raw.substring(0, bar).trim();
        }
        String[] parts = SEPARATOR.split(raw, 2);
        String word = cleanWord(parts[0]);
        if (word.isEmpty()) return null;
        String translation = parts.length > 1 ? blankToNull(parts[1].trim()) : null;
        return new ParsedWord(word, translation, example);
    }

    private List<String> readTxt(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        } catch (IOException e) {
            throw new BadRequestException("TXT faylni o'qishda xato: " + e.getMessage());
        }
    }

    private List<String> readDocx(MultipartFile file) {
        try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
            return doc.getParagraphs().stream().map(p -> p.getText()).toList();
        } catch (IOException e) {
            throw new BadRequestException("DOCX faylni o'qishda xato: " + e.getMessage());
        }
    }

    private static String cleanWord(String raw) {
        return raw.replaceAll("[^a-zA-Z\\s'-]", "").trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
