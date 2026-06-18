package uz.vocabexam.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import uz.vocabexam.exception.BadRequestException;
import java.io.*;
import java.util.*;
import java.util.stream.*;

@Service @Slf4j
public class WordFileParserService {

    public List<String> parse(MultipartFile file) {
        String filename = Objects.requireNonNull(file.getOriginalFilename()).toLowerCase();
        if (filename.endsWith(".txt"))  return parseTxt(file);
        if (filename.endsWith(".docx")) return parseDocx(file);
        throw new BadRequestException("Faqat .txt va .docx formatlar qabul qilinadi");
    }

    private List<String> parseTxt(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), "UTF-8"))) {
            return reader.lines()
                .map(String::trim)
                .filter(l -> !l.isEmpty())
                .map(this::cleanWord)
                .filter(w -> !w.isEmpty())
                .collect(Collectors.toList());
        } catch (IOException e) {
            throw new BadRequestException("TXT faylni o'qishda xato: " + e.getMessage());
        }
    }

    private List<String> parseDocx(MultipartFile file) {
        try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
            return doc.getParagraphs().stream()
                .map(p -> p.getText().trim())
                .filter(t -> !t.isEmpty())
                .map(this::cleanWord)
                .filter(w -> !w.isEmpty())
                .collect(Collectors.toList());
        } catch (IOException e) {
            throw new BadRequestException("DOCX faylni o'qishda xato: " + e.getMessage());
        }
    }

    private String cleanWord(String raw) {
        return raw.replaceAll("[^a-zA-Z\\s'-]", "").trim().toLowerCase();
    }
}
