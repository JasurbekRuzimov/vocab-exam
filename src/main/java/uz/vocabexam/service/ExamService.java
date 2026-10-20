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
        validateParams(questionCount, variantCount);
        WordSet ws = wordSetRepository.findById(wordSetId)
            .orElseThrow(() -> new ResourceNotFoundException("So'zlar to'plami topilmadi"));
        SecurityUtils.assertOwnerOrAdmin(ws.getTeacher().getId());
        List<Word> allWords = wordRepository.findByWordSetId(wordSetId);
        if (allWords.size() < questionCount)
            throw new BadRequestException("Faylda " + allWords.size() + " so'z bor, " + questionCount + " so'z so'raldi");

        User teacher = userRepository.findById(teacherId)
            .orElseThrow(() -> new ResourceNotFoundException("O'qituvchi topilmadi"));

        Exam exam = Exam.builder().title(title).type(type).wordSet(ws).teacher(teacher)
            .questionCount(questionCount).variantCount(variantCount)
            .status(ExamStatus.DRAFT).build();
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

    @Transactional
    public void delete(UUID id, UUID teacherId) {
        Exam exam = getById(id); checkOwnership(exam, teacherId);
        if (assignmentRepository.existsByExamIdAndStatus(id, AssignmentStatus.ACTIVE))
            throw new BadRequestException("Aktiv topshiriqlari bor imtihonni o'chirib bo'lmaydi");
        examRepository.delete(exam);
    }

    public List<ExamVariant> getVariants(UUID examId) {
        return variantRepository.findByExamIdOrderByVariantNumber(examId);
    }

    private void validateParams(int questionCount, int variantCount) {
        if (questionCount < 5 || questionCount > 100)
            throw new BadRequestException("Savol soni 5 dan 100 gacha bo'lishi kerak");
        if (variantCount < 1 || variantCount > 10)
            throw new BadRequestException("Variant soni 1 dan 10 gacha bo'lishi kerak");
    }
}
