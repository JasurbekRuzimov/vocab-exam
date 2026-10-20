package uz.vocabexam.entity;

/** Onlayn imtihondagi savol turlari. SENTENCE dan boshqalari avtomatik baholanadi. */
public enum QuestionType {
    SENTENCE("Gap tuzish", false),
    TRANSLATE_EN_UZ("Tarjima: EN → UZ", true),
    TRANSLATE_UZ_EN("Tarjima: UZ → EN", true),
    MULTIPLE_CHOICE("Test (4 variant)", true);

    private final String label;
    private final boolean autoGraded;

    QuestionType(String label, boolean autoGraded) {
        this.label = label;
        this.autoGraded = autoGraded;
    }

    public String getLabel()      { return label; }
    public boolean isAutoGraded() { return autoGraded; }
}
