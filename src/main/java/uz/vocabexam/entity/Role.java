package uz.vocabexam.entity;

public enum Role {
    /** Platforma admini: barcha markazlarni boshqaradi. */
    SUPER_ADMIN("Platforma admini"),
    /** Markaz direktori: o'z markazidagi foydalanuvchi va guruhlarni boshqaradi, statistikani ko'radi. */
    CENTER_ADMIN("Markaz direktori"),
    TEACHER("O'qituvchi"),
    STUDENT("O'quvchi");

    private final String label;

    Role(String label) { this.label = label; }

    public String getLabel() { return label; }
}
