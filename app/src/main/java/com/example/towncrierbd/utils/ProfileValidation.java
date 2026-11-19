package com.example.towncrierbd.utils;
import java.text.Normalizer;
public final class ProfileValidation {
    private ProfileValidation() {}
    public static String name(String raw) { return Normalizer.normalize(raw == null ? "" : raw,Normalizer.Form.NFC).trim().replaceAll("\\s+"," "); }
    public static boolean validName(String raw) {
        String value=name(raw);
        return value.length()<=80 && value.codePoints().filter(Character::isLetter).count()>=2
            && value.matches("[\\p{L}\\p{M}]+(?:(?:[ '\u2019\\-]|\\. ?)[\\p{L}\\p{M}]+)*\\.?");
    }
    public static String phone(String raw) { String v=raw==null?"":raw.replaceAll("[\\s-]","");if(v.startsWith("+880"))return "0"+v.substring(4);if(v.startsWith("880"))return "0"+v.substring(3);return v; }
    public static boolean validPhone(String raw) { String v=phone(raw);return v.isEmpty()||v.matches("01[3-9][0-9]{8}"); }
}
