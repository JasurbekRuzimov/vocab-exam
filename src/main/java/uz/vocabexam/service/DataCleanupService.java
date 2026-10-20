package uz.vocabexam.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/**
 * O'chirishda bog'liq yozuvlarni tartib bilan tozalaydi: javoblar, xato belgilari,
 * urinishlar, natijalar va ularning tarixi. Bazadagi ON DELETE CASCADE ga tayanmaymiz,
 * shunda o'chirish har qanday bazada bir xil ishlaydi.
 */
@Service @RequiredArgsConstructor
public class DataCleanupService {
    private final EntityManager em;

    /** Topshiriqlarni barcha javoblari va natijalari bilan birga o'chiradi. */
    @Transactional
    public void purgeAssignments(Collection<UUID> assignmentIds) {
        if (assignmentIds.isEmpty()) return;
        // O'chgan topshiriq yoki natijaga olib boradigan bildirishnomalar ham kerak emas
        List<UUID> resultIds = em.createQuery("SELECT r.id FROM ExamResult r WHERE r.assignment.id IN :ids", UUID.class)
            .setParameter("ids", assignmentIds).getResultList();
        for (UUID id : concat(assignmentIds, resultIds))
            em.createQuery("DELETE FROM Notification n WHERE n.link LIKE :p").setParameter("p", "%" + id + "%").executeUpdate();
        exec("DELETE FROM AnswerError e WHERE e.answer.id IN "
            + "(SELECT a.id FROM OnlineAnswer a WHERE a.assignment.id IN :ids)", assignmentIds);
        exec("DELETE FROM OnlineAnswer a WHERE a.assignment.id IN :ids", assignmentIds);
        exec("DELETE FROM ExamAttempt t WHERE t.assignment.id IN :ids", assignmentIds);
        exec("DELETE FROM ResultChange c WHERE c.result.id IN "
            + "(SELECT r.id FROM ExamResult r WHERE r.assignment.id IN :ids)", assignmentIds);
        exec("DELETE FROM ExamResult r WHERE r.assignment.id IN :ids", assignmentIds);
        exec("DELETE FROM ExamAssignment a WHERE a.id IN :ids", assignmentIds);
        em.flush();
        em.clear();
    }

    /** Imtihonning barcha topshiriqlari (va natijalari). */
    @Transactional
    public void purgeExamAssignments(UUID examId) {
        purgeAssignments(ids("SELECT a.id FROM ExamAssignment a WHERE a.exam.id = :id", examId));
    }

    /**
     * Foydalanuvchiga bog'liq hamma narsani tozalaydi: o'quvchi bo'lsa javoblari, natijalari,
     * shaxsiy topshiriqlari va guruh a'zoligi; har qanday rol uchun bildirishnomalar.
     * Jurnal va natija o'zgarishlari tarixida yozuv qoladi, faqat muallif bo'shatiladi.
     */
    @Transactional
    public void purgeUserData(UUID userId) {
        purgeAssignments(ids("SELECT a.id FROM ExamAssignment a WHERE a.student.id = :id", userId));
        List<UUID> id = List.of(userId);
        exec("DELETE FROM AnswerError e WHERE e.answer.id IN "
            + "(SELECT a.id FROM OnlineAnswer a WHERE a.student.id IN :ids)", id);
        exec("DELETE FROM OnlineAnswer a WHERE a.student.id IN :ids", id);
        exec("DELETE FROM ExamAttempt t WHERE t.student.id IN :ids", id);
        exec("DELETE FROM ResultChange c WHERE c.result.id IN "
            + "(SELECT r.id FROM ExamResult r WHERE r.student.id IN :ids)", id);
        exec("DELETE FROM ExamResult r WHERE r.student.id IN :ids", id);
        exec("UPDATE ResultChange c SET c.changedBy = null WHERE c.changedBy.id IN :ids", id);
        exec("UPDATE AuditLog l SET l.user = null WHERE l.user.id IN :ids", id);
        exec("DELETE FROM Notification n WHERE n.user.id IN :ids", id);
        em.createNativeQuery("DELETE FROM group_students WHERE student_id = :id")
            .setParameter("id", userId).executeUpdate();
        em.flush();
        em.clear();
    }

    private static List<UUID> concat(Collection<UUID> a, Collection<UUID> b) {
        List<UUID> all = new ArrayList<>(a); all.addAll(b); return all;
    }

    private void exec(String jpql, Collection<UUID> ids) {
        em.createQuery(jpql).setParameter("ids", ids).executeUpdate();
    }

    private List<UUID> ids(String jpql, UUID id) {
        return em.createQuery(jpql, UUID.class).setParameter("id", id).getResultList();
    }
}
