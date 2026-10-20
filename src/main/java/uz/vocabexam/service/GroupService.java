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
        User teacher = userRepository.findById(teacherId)
            .orElseThrow(() -> new ResourceNotFoundException("O'qituvchi topilmadi"));
        return groupRepository.save(Group.builder().name(name).teacher(teacher).build());
    }
    @Transactional
    public Group update(UUID id, String name, UUID teacherId) {
        Group g = getById(id);
        User teacher = userRepository.findById(teacherId)
            .orElseThrow(() -> new ResourceNotFoundException("O'qituvchi topilmadi"));
        g.setName(name); g.setTeacher(teacher);
        return groupRepository.save(g);
    }
    @Transactional
    public void delete(UUID id) { groupRepository.deleteById(id); }
    @Transactional
    public void addStudents(UUID groupId, List<UUID> studentIds) {
        Group g = getById(groupId);
        studentIds.forEach(sid -> {
            User s = userRepository.findById(sid).orElseThrow(() -> new ResourceNotFoundException("Student topilmadi: " + sid));
            if (s.getRole() != Role.STUDENT) throw new BadRequestException(s.getFullName() + " student roli emas");
            g.getStudents().add(s);
        });
        groupRepository.save(g);
    }
    @Transactional
    public void removeStudent(UUID groupId, UUID studentId) {
        Group g = getById(groupId);
        g.getStudents().removeIf(s -> s.getId().equals(studentId));
        groupRepository.save(g);
    }
}
