package uz.vocabexam.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uz.vocabexam.entity.*;
import uz.vocabexam.exception.*;
import uz.vocabexam.repository.*;
import uz.vocabexam.security.SecurityUtils;
import java.util.*;
import java.util.stream.*;

@Service @RequiredArgsConstructor
public class WordSetService {
    private final WordSetRepository wordSetRepository;
    private final WordRepository wordRepository;
    private final UserRepository userRepository;
    private final WordFileParserService fileParserService;
    private final ExamRepository examRepository;
    private final OnlineAnswerRepository answerRepository;
    private final ExamVariantRepository variantRepository;

    public List<WordSet> getByTeacher(UUID teacherId) {
        return wordSetRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId);
    }
    public WordSet getById(UUID id) {
        return wordSetRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("So'zlar to'plami topilmadi"));
    }
    public void checkOwnership(WordSet ws, UUID teacherId) {
        if (SecurityUtils.isAdmin()) return;
        if (!ws.getTeacher().getId().equals(teacherId)) throw new AccessForbiddenException("Ruxsat yo'q");
    }
    /** To'plamni faqat egasi (yoki admin) uchun qaytaradi. */
    public WordSet getOwned(UUID id) {
        WordSet ws = getById(id);
        SecurityUtils.assertOwnerOrAdmin(ws.getTeacher().getId());
        return ws;
    }

    @Transactional
    public Map<String, Object> upload(String name, MultipartFile file, UUID teacherId) {
        if (file.getSize() > 5 * 1024 * 1024) throw new BadRequestException("Fayl hajmi 5 MB dan oshmasin");
        User teacher = userRepository.findById(teacherId)
            .orElseThrow(() -> new ResourceNotFoundException("O'qituvchi topilmadi"));
        List<WordFileParserService.ParsedWord> parsed = fileParserService.parse(file);
        if (parsed.size() < 5) throw new BadRequestException("Kamida 5 ta so'z bo'lishi shart");

        WordSet ws = WordSet.builder()
            .name(name).teacher(teacher)
            .fileName(file.getOriginalFilename()).build();
        wordSetRepository.save(ws);

        List<String> duplicates = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int withTranslation = 0;
        for (WordFileParserService.ParsedWord p : parsed) {
            if (!seen.add(p.text())) { duplicates.add(p.text()); continue; }
            if (p.translation() != null) withTranslation++;
            wordRepository.save(Word.builder().wordSet(ws).text(p.text())
                .translation(p.translation()).example(p.example()).build());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("wordSet",   ws);
        result.put("wordCount", seen.size());
        result.put("duplicates", duplicates);
        result.put("withTranslation", withTranslation);
        return result;
    }

    @Transactional
    public void rename(UUID id, String newName, UUID teacherId) {
        WordSet ws = getById(id); checkOwnership(ws, teacherId);
        if (newName == null || newName.isBlank()) throw new BadRequestException("Nom bo'sh bo'lmasin");
        ws.setName(newName.trim()); wordSetRepository.save(ws);
    }
    @Transactional
    public void delete(UUID id, UUID teacherId) {
        WordSet ws = getById(id); checkOwnership(ws, teacherId);
        long exams = examRepository.countByWordSetId(id);
        if (exams > 0)
            throw new BadRequestException("Bu to'plamdan " + exams + " ta imtihon yaratilgan. Avval o'sha imtihonlarni o'chiring.");
        wordSetRepository.delete(ws);
    }
    @Transactional
    public Word addWord(UUID wordSetId, String text, UUID teacherId) {
        return addWord(wordSetId, text, null, null, teacherId);
    }
    @Transactional
    public Word addWord(UUID wordSetId, String text, String translation, String example, UUID teacherId) {
        WordSet ws = getById(wordSetId); checkOwnership(ws, teacherId);
        if (text == null || text.isBlank()) throw new BadRequestException("So'z bo'sh bo'lmasin");
        if (wordRepository.existsByWordSetIdAndTextIgnoreCase(wordSetId, text.trim()))
            throw new BadRequestException("Bu so'z allaqachon mavjud");
        return wordRepository.save(Word.builder().wordSet(ws).text(text.trim().toLowerCase())
            .translation(trimToNull(translation)).example(trimToNull(example)).build());
    }
    /** So'z kartochkasini (tarjima, misol) tahrirlash. */
    @Transactional
    public Word updateWord(UUID wordSetId, UUID wordId, String translation, String example, UUID teacherId) {
        Word w = wordRepository.findById(wordId).orElseThrow(() -> new ResourceNotFoundException("So'z topilmadi"));
        if (!w.getWordSet().getId().equals(wordSetId)) throw new ResourceNotFoundException("So'z topilmadi");
        checkOwnership(w.getWordSet(), teacherId);
        w.setTranslation(trimToNull(translation));
        w.setExample(trimToNull(example));
        return wordRepository.save(w);
    }
    private static String trimToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    @Transactional
    public void deleteWord(UUID wordId, UUID teacherId) {
        Word w = wordRepository.findById(wordId).orElseThrow(() -> new ResourceNotFoundException("So'z topilmadi"));
        checkOwnership(w.getWordSet(), teacherId);
        // O'quvchi javob bergan yoki PDF variantga tushgan so'z o'chirilsa, natijalar buziladi
        if (answerRepository.existsByWordId(wordId) || variantRepository.existsByWords_Id(wordId))
            throw new BadRequestException("Bu so'z imtihonlarda ishlatilgan, uni o'chirib bo'lmaydi. Tarjimasini tahrirlash mumkin.");
        w.getWordSet().getWords().remove(w);
        wordRepository.delete(w);
    }
    public List<Word> getWords(UUID wordSetId) { return wordRepository.findByWordSetId(wordSetId); }
}
