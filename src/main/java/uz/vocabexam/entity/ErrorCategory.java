package uz.vocabexam.entity;

/** Gap tuzishdagi xato turlari: o'qituvchi bir bosishda tanlaydi, o'quvchi natijada ko'radi. */
public enum ErrorCategory {
    SPELLING("Imlo"),
    ARTICLE("Artikl (a/an/the)"),
    TENSE("Zamon"),
    WORD_USAGE("So'z noto'g'ri ishlatilgan"),
    WORD_ORDER("So'z tartibi"),
    GRAMMAR("Grammatika"),
    PUNCTUATION("Tinish belgisi"),
    OTHER("Boshqa");

    private final String label;

    ErrorCategory(String label) { this.label = label; }

    public String getLabel() { return label; }
}
