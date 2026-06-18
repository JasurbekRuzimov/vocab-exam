package uz.vocabexam.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uz.vocabexam.entity.*;
import uz.vocabexam.exception.*;
import uz.vocabexam.repository.*;
import java.util.*;
import java.util.stream.*;

@Service @RequiredArgsConstructor
public class WordSetService {
    private final WordSetRepository wordSetRepository;
    private final WordRepository wordRepository;
    private final UserRepository userRepository;
    private final WordFileParserService fileParserService;

    public List<WordSet> getByTeacher(UUID teacherId) {
        return wordSetRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId);
    }
    public WordSet getById(UUID id) {
        return wordSetRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("So'zlar to'plami topilmadi"));
    }
    public void checkOwnership(WordSet ws, UUID teacherId) {
        if (!ws.getTeacher().getId().equals(teacherId)) throw new AccessForbiddenException("Ruxsat yo'q");
    }

    @Transactional
    public Map<String, Object> upload(String name, MultipartFile file, UUID teacherId) {
        if (file.getSize() > 5 * 1024 * 1024) throw new BadRequestException("Fayl hajmi 5 MB dan oshmasin");
        User teacher = userRepository.findById(teacherId)
            .orElseThrow(() -> new ResourceNotFoundException("O'qituvchi topilmadi"));
        List<String> parsed = fileParserService.parse(file);
        if (parsed.size() < 5) throw new BadRequestException("Kamida 5 ta so'z bo'lishi shart");

        WordSet ws = WordSet.builder()
            .name(name).teacher(teacher)
            .fileName(file.getOriginalFilename()).build();
        wordSetRepository.save(ws);

        List<String> duplicates = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String w : parsed) {
            if (!seen.add(w)) { duplicates.add(w); continue; }
            wordRepository.save(Word.builder().wordSet(ws).text(w).build());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("wordSet",   ws);
        result.put("wordCount", seen.size());
        result.put("duplicates", duplicates);
        return result;
    }

    @Transactional
    public void rename(UUID id, String newName, UUID teacherId) {
        WordSet ws = getById(id); checkOwnership(ws, teacherId);
        ws.setName(newName); wordSetRepository.save(ws);
    }
    @Transactional
    public void delete(UUID id, UUID teacherId) {
        WordSet ws = getById(id); checkOwnership(ws, teacherId);
        wordSetRepository.delete(ws);
    }
    @Transactional
    public Word addWord(UUID wordSetId, String text, UUID teacherId) {
        WordSet ws = getById(wordSetId); checkOwnership(ws, teacherId);
        if (wordRepository.existsByWordSetIdAndTextIgnoreCase(wordSetId, text))
            throw new BadRequestException("Bu so'z allaqachon mavjud");
        return wordRepository.save(Word.builder().wordSet(ws).text(text.trim().toLowerCase()).build());
    }
    @Transactional
    public void deleteWord(UUID wordId, UUID teacherId) {
        Word w = wordRepository.findById(wordId).orElseThrow(() -> new ResourceNotFoundException("So'z topilmadi"));
        checkOwnership(w.getWordSet(), teacherId);
        wordRepository.delete(w);
    }
    public List<Word> getWords(UUID wordSetId) { return wordRepository.findByWordSetId(wordSetId); }
}
