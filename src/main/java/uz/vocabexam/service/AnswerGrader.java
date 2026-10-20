package uz.vocabexam.service;

import java.text.Normalizer;
import java.util.*;

/**
 * Tarjima va test javoblarini avtomatik tekshirish.
 * Katta-kichik harf, ortiqcha bo'sh joy, tinish belgilari va o'zbekcha apostrof
 * shakllari (o' o‘ oʻ o’ o`) farqi hisobga olinmaydi; 5+ harfli javobda bitta harf xatosiga yo'l qo'yiladi.
 */
public final class AnswerGrader {
    private AnswerGrader() {}

    /** Tarjimadagi variantlar: "chiroyli, go'zal; xushro'y" → [chiroyli, go'zal, xushro'y]. */
    public static List<String> variants(String expected) {
        if (expected == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : expected.split("[,;/]")) {
            String n = normalize(part);
            if (!n.isEmpty()) out.add(n);
        }
        return out;
    }

    public static boolean isCorrect(String answer, String expected) {
        String a = normalize(answer);
        if (a.isEmpty()) return false;
        for (String v : variants(expected)) {
            if (a.equals(v)) return true;
            if (v.length() >= 5 && levenshtein(a, v) <= 1) return true;
        }
        return false;
    }

    /** Test savoli: tanlangan variant aynan to'g'ri variant bo'lishi kerak (yumshoqliksiz). */
    public static boolean isExactChoice(String answer, String expected) {
        return !normalize(answer).isEmpty() && normalize(answer).equals(normalize(expected));
    }

    static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
            .replaceAll("[‘’ʻʼ`´]", "'")
            .replaceAll("[^\\p{L}\\p{N}' -]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        return n;
    }

    static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }
}
