package uz.vocabexam.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.vocabexam.entity.*;
import uz.vocabexam.exception.*;
import uz.vocabexam.repository.*;
import uz.vocabexam.security.SecurityUtils;
import java.util.*;

@Service @RequiredArgsConstructor
public class GroupService {
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;

    public List<Group> getAll(UUID teacherId) {
        return teacherId != null ? groupRepository.findByTeacherId(teacherId) : groupRepository.findAll();
    }

    /** Boshqaruv sahifasi uchun: platforma admini hammasini, direktor o'z markazi guruhlarini ko'radi. */
    public List<Group> getManagedList() {
        if (SecurityUtils.isAdmin()) return groupRepository.findAll();
        UUID center = SecurityUtils.currentCenterId();
        if (center == null) throw new AccessForbiddenException("Markaz biriktirilmagan");
        return groupRepository.findByTeacher_Center_Id(center);
    }

    /** Guruhni boshqarish (o'quvchi qo'shish, o'qituvchini almashtirish) — faqat shu markaz direktori yoki platforma admini. */
    public Group getManaged(UUID id) {
        Group g = getById(id);
        SecurityUtils.assertSameCenter(g.getTeacher().getCenterId());
        return g;
    }

    /** Guruh o'qituvchisi joriy foydalanuvchi boshqaradigan markazdagi o'qituvchi bo'lishi kerak. */
    private User requireTeacher(UUID teacherId) {
        User teacher = userRepository.findById(teacherId)
            .orElseThrow(() -> new ResourceNotFoundException("O'qituvchi topilmadi"));
        if (teacher.getRole() != Role.TEACHER) throw new BadRequestException(teacher.getFullName() + " o'qituvchi emas");
        SecurityUtils.assertSameCenter(teacher.getCenterId());
        return teacher;
    }
    public Group getById(UUID id) {
        return groupRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Guruh topilmadi"));
    }
    /** Guruhni faqat uning o'qituvchisi (yoki admin) uchun qaytaradi. */
    public Group getOwned(UUID id) {
        Group g = getById(id);
        SecurityUtils.assertOwnerOrAdmin(g.getTeacher().getId());
        return g;
    }
    @Transactional
    public Group create(String name, UUID teacherId) {
        if (name == null || name.isBlank()) throw new BadRequestException("Guruh nomini yozing");
        User teacher = requireTeacher(teacherId);
        return groupRepository.save(Group.builder().name(name).teacher(teacher).build());
    }
    @Transactional
    public Group update(UUID id, String name, UUID teacherId) {
        Group g = getManaged(id);
        User teacher = requireTeacher(teacherId);
        if (!java.util.Objects.equals(teacher.getCenterId(), g.getTeacher().getCenterId()))
            throw new BadRequestException("O'qituvchi guruh bilan bir markazda bo'lishi kerak");
        g.setName(name); g.setTeacher(teacher);
        return groupRepository.save(g);
    }
    @Transactional
    public void delete(UUID id) { groupRepository.delete(getManaged(id)); }
    @Transactional
    public void addStudents(UUID groupId, List<UUID> studentIds) {
        Group g = getManaged(groupId);
        UUID center = g.getTeacher().getCenterId();
        studentIds.forEach(sid -> {
            User s = userRepository.findById(sid).orElseThrow(() -> new ResourceNotFoundException("Student topilmadi: " + sid));
            if (s.getRole() != Role.STUDENT) throw new BadRequestException(s.getFullName() + " student roli emas");
            if (!java.util.Objects.equals(center, s.getCenterId()))
                throw new BadRequestException(s.getFullName() + " boshqa markaz o'quvchisi");
            g.getStudents().add(s);
        });
        groupRepository.save(g);
    }
    @Transactional
    public void removeStudent(UUID groupId, UUID studentId) {
        Group g = getManaged(groupId);
        g.getStudents().removeIf(s -> s.getId().equals(studentId));
        groupRepository.save(g);
    }
}
