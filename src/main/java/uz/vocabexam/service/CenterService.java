package uz.vocabexam.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.vocabexam.entity.*;
import uz.vocabexam.exception.*;
import uz.vocabexam.repository.*;
import java.util.*;
import java.util.regex.Pattern;

/** O'quv markazlarini boshqarish (faqat platforma admini). */
@Service @RequiredArgsConstructor
public class CenterService {
    private final CenterRepository centerRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    private static final Pattern SLUG  = Pattern.compile("[a-z0-9-]{2,50}");
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    /** Markaz va undagi foydalanuvchilar soni (rollar bo'yicha). */
    public record CenterRow(Center center, long directors, long teachers, long students) {}

    public List<CenterRow> listWithCounts() {
        Map<UUID, Map<Role, Long>> counts = new HashMap<>();
        for (Object[] r : userRepository.countByCenterAndRole())
            counts.computeIfAbsent((UUID) r[0], k -> new EnumMap<>(Role.class)).put((Role) r[1], (Long) r[2]);
        return centerRepository.findAllByOrderByName().stream().map(c -> {
            Map<Role, Long> m = counts.getOrDefault(c.getId(), Map.of());
            return new CenterRow(c, m.getOrDefault(Role.CENTER_ADMIN, 0L),
                m.getOrDefault(Role.TEACHER, 0L), m.getOrDefault(Role.STUDENT, 0L));
        }).toList();
    }

    public List<Center> getAll() {
        return centerRepository.findAllByOrderByName();
    }

    public Center getById(UUID id) {
        return centerRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Markaz topilmadi"));
    }

    /** Yangi markaz va uning direktori bitta amalda yaratiladi. */
    @Transactional
    public Center create(String name, String slug, String logoUrl, String color,
                         String directorName, String directorUsername, String directorPassword) {
        String s = slug == null ? "" : slug.trim().toLowerCase();
        if (!SLUG.matcher(s).matches())
            throw new BadRequestException("Qisqa nom 2-50 belgi: lotin harflari, raqamlar va -");
        if (centerRepository.existsBySlug(s)) throw new BadRequestException("Bu qisqa nom band: " + s);
        Center c = Center.builder().slug(s).build();
        apply(c, name, logoUrl, color);
        c = centerRepository.save(c);
        if (directorUsername != null && !directorUsername.isBlank())
            userService.create(directorName == null || directorName.isBlank() ? name + " direktori" : directorName,
                directorUsername, null, directorPassword, Role.CENTER_ADMIN, c.getId());
        return c;
    }

    @Transactional
    public Center update(UUID id, String name, String logoUrl, String color) {
        Center c = getById(id);
        apply(c, name, logoUrl, color);
        return centerRepository.save(c);
    }

    @Transactional
    public Center setActive(UUID id, boolean active) {
        Center c = getById(id);
        c.setActive(active);
        return centerRepository.save(c);
    }

    private static void apply(Center c, String name, String logoUrl, String color) {
        if (name == null || name.isBlank()) throw new BadRequestException("Markaz nomini yozing");
        String logo = logoUrl == null || logoUrl.isBlank() ? null : logoUrl.trim();
        if (logo != null && !logo.matches("https?://\\S{3,490}"))
            throw new BadRequestException("Logotip havolasi http:// yoki https:// bilan boshlansin");
        String col = color == null || color.isBlank() ? "#1E3A5F" : color.trim();
        if (!COLOR.matcher(col).matches()) throw new BadRequestException("Rang #RRGGBB ko'rinishida bo'lsin");
        c.setName(name.trim());
        c.setLogoUrl(logo);
        c.setPrimaryColor(col);
    }
}
