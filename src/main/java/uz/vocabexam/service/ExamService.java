package uz.vocabexam.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.vocabexam.entity.*;
import uz.vocabexam.exception.*;
import uz.vocabexam.repository.*;
import uz.vocabexam.security.SecurityUtils;
import java.io.IOException;
import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
public class ExamService {
    private final ExamRepository examRepository;
    private final ExamVariantRepository variantRepository;
    private final WordRepository wordRepository;
    private final WordSetRepository wordSetRepository;
    private final UserRepository userRepository;
    private final PdfGeneratorService pdfGeneratorService;
    private final ExamAssignmentRepository assignmentRepository;
    private final DataCleanupService cleanupService;

    public Page<Exam> getByTeacher(UUID teacherId, Pageable pageable) {
        return examRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId, pageable);
    }
    public Exam getById(UUID id) {
        return examRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Imtihon topilmadi"));
    }
    public void checkOwnership(Exam exam, UUID teacherId) {
        if (SecurityUtils.isAdmin()) return;
        if (!exam.getTeacher().getId().equals(teacherId)) throw new AccessForbiddenException("Ruxsat yo'q");
    }
    /** Imtihonni faqat egasi (yoki admin) uchun qaytaradi. */
    public Exam getOwned(UUID id) {
        Exam exam = getById(id);
        SecurityUtils.assertOwnerOrAdmin(exam.getTeacher().getId());
        return exam;
    }

    @Transactional
    public Exam createAndGenerate(String title, ExamType type, UUID wordSetId,
                                   UUID teacherId, int questionCount, int variantCount) {
        return createAndGenerate(title, type, wordSetId, teacherId, questionCount, variantCount,
            List.of(QuestionType.SENTENCE));
    }

    @Transactional
    public Exam createAndGenerate(String title, ExamType type, UUID wordSetId, UUID teacherId,
                                   int questionCount, int variantCount, List<QuestionType> questionTypes) {
        validateParams(questionCount, variantCount);
        WordSet ws = wordSetRepository.findById(wordSetId)
            .orElseThrow(() -> new ResourceNotFoundException("So'zlar to'plami topilmadi"));
        SecurityUtils.assertOwnerOrAdmin(ws.getTeacher().getId());
        List<Word> allWords = wordRepository.findByWordSetId(wordSetId);
        if (allWords.size() < questionCount)
            throw new BadRequestException("Faylda " + allWords.size() + " so'z bor, " + questionCount + " so'z so'raldi");

        List<QuestionType> types = type == ExamType.ONLINE && questionTypes != null && !questionTypes.isEmpty()
            ? questionTypes : List.of(QuestionType.SENTENCE);
        validateQuestionTypes(types, allWords);

        User teacher = userRepository.findById(teacherId)
            .orElseThrow(() -> new ResourceNotFoundException("O'qituvchi topilmadi"));

        Exam exam = Exam.builder().title(title).type(type).wordSet(ws).teacher(teacher)
            .questionCount(questionCount).variantCount(variantCount)
            .status(ExamStatus.DRAFT).build();
        exam.setQuestionTypeList(types);
        examRepository.save(exam);

        if (type == ExamType.PDF) {
            generatePdfVariants(exam, allWords);
        }
        exam.setStatus(ExamStatus.GENERATED);
        return examRepository.save(exam);
    }

    private void generatePdfVariants(Exam exam, List<Word> allWords) {
        for (int v = 1; v <= exam.getVariantCount(); v++) {
            List<Word> shuffled = new ArrayList<>(allWords);
            Collections.shuffle(shuffled);
            List<Word> selected = shuffled.subList(0, exam.getQuestionCount());

            ExamVariant variant = ExamVariant.builder()
                .exam(exam).variantNumber(v).words(new ArrayList<>(selected)).build();
            try {
                String pdfPath = pdfGeneratorService.generateVariant(exam.getTitle(), v, selected, exam.getId());
                variant.setPdfPath(pdfPath);
            } catch (IOException e) {
                log.error("PDF generatsiya xatosi: {}", e.getMessage());
                throw new RuntimeException("PDF yaratishda xato: " + e.getMessage());
            }
            variantRepository.save(variant);
        }
    }

    /** Imtihon barcha topshiriqlari, javoblari va natijalari bilan birga o'chiriladi. */
    @Transactional
    public void delete(UUID id, UUID teacherId) {
        checkOwnership(getById(id), teacherId);
        cleanupService.purgeExamAssignments(id);
        examRepository.delete(getById(id));
    }

    @Transactional
    public Exam rename(UUID id, String title, UUID teacherId) {
        Exam exam = getById(id); checkOwnership(exam, teacherId);
        if (title == null || title.isBlank()) throw new BadRequestException("Sarlavha bo'sh bo'lmasin");
        exam.setTitle(title.trim());
        return examRepository.save(exam);
    }

    public List<ExamVariant> getVariants(UUID examId) {
        return variantRepository.findByExamIdOrderByVariantNumber(examId);
    }

    private void validateQuestionTypes(List<QuestionType> types, List<Word> words) {
        if (types.stream().noneMatch(QuestionType::isAutoGraded)) return;
        long missing = words.stream().filter(w -> w.getTranslation() == null || w.getTranslation().isBlank()).count();
        if (missing > 0)
            throw new BadRequestException("Tarjima va test savollari uchun har bir so'zda tarjima bo'lishi kerak. "
                + "Tarjimasi yo'q so'zlar: " + missing + " ta");
        long distinct = words.stream().map(w -> AnswerGrader.normalize(w.getTranslation())).distinct().count();
        if (types.contains(QuestionType.MULTIPLE_CHOICE) && distinct < 4)
            throw new BadRequestException("Test savoli uchun kamida 4 xil tarjima kerak");
    }

    private void validateParams(int questionCount, int variantCount) {
        if (questionCount < 5 || questionCount > 100)
            throw new BadRequestException("Savol soni 5 dan 100 gacha bo'lishi kerak");
        if (variantCount < 1 || variantCount > 10)
            throw new BadRequestException("Variant soni 1 dan 10 gacha bo'lishi kerak");
    }
}
