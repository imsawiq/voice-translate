package org.sawiq.voicetranslate.language;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A language players can speak or read subtitles in.
 *
 * <p>Only languages that Whisper recognises well and that every translator
 * offers are listed; a longer list would mostly offer choices that disappoint.</p>
 *
 * @param code        ISO 639-1, as Whisper and Google use it
 * @param whisperName the English name OpenAI-style APIs report the language by
 * @param nativeName  how the language names itself, for the settings screen
 */
public record Language(String code, String whisperName, String nativeName) {
    public static final List<Language> ALL = List.of(
            new Language("en", "english", "English"),
            new Language("ru", "russian", "Русский"),
            new Language("uk", "ukrainian", "Українська"),
            new Language("be", "belarusian", "Беларуская"),
            new Language("kk", "kazakh", "Қазақ тілі"),
            new Language("de", "german", "Deutsch"),
            new Language("fr", "french", "Français"),
            new Language("es", "spanish", "Español"),
            new Language("pt", "portuguese", "Português"),
            new Language("it", "italian", "Italiano"),
            new Language("pl", "polish", "Polski"),
            new Language("cs", "czech", "Čeština"),
            new Language("sk", "slovak", "Slovenčina"),
            new Language("nl", "dutch", "Nederlands"),
            new Language("sv", "swedish", "Svenska"),
            new Language("da", "danish", "Dansk"),
            new Language("no", "norwegian", "Norsk"),
            new Language("fi", "finnish", "Suomi"),
            new Language("hu", "hungarian", "Magyar"),
            new Language("ro", "romanian", "Română"),
            new Language("bg", "bulgarian", "Български"),
            new Language("sr", "serbian", "Српски"),
            new Language("hr", "croatian", "Hrvatski"),
            new Language("el", "greek", "Ελληνικά"),
            new Language("tr", "turkish", "Türkçe"),
            new Language("az", "azerbaijani", "Azərbaycan"),
            new Language("ka", "georgian", "ქართული"),
            new Language("hy", "armenian", "Հայերեն"),
            new Language("he", "hebrew", "עברית"),
            new Language("ar", "arabic", "العربية"),
            new Language("fa", "persian", "فارسی"),
            new Language("hi", "hindi", "हिन्दी"),
            new Language("zh", "chinese", "中文"),
            new Language("ja", "japanese", "日本語"),
            new Language("ko", "korean", "한국어"),
            new Language("vi", "vietnamese", "Tiếng Việt"),
            new Language("th", "thai", "ไทย"),
            new Language("id", "indonesian", "Bahasa Indonesia"),
            new Language("lt", "lithuanian", "Lietuvių"),
            new Language("lv", "latvian", "Latviešu"),
            new Language("et", "estonian", "Eesti")
    );

    public static Optional<Language> byCode(String code) {
        if (code == null || code.isEmpty()) {
            return Optional.empty();
        }
        String normalized = normalizeCode(code);
        return ALL.stream().filter(language -> language.code.equals(normalized)).findFirst();
    }

    public static Optional<Language> byWhisperName(String name) {
        String lowered = name.toLowerCase(Locale.ROOT).strip();
        return ALL.stream()
                .filter(language -> language.whisperName.equals(lowered) || language.code.equals(lowered))
                .findFirst();
    }

    /**
     * The language Minecraft is set to, such as {@code ru_ru} or
     * {@code pt_br}; English when it is one this mod does not list.
     */
    public static Language fromGameLanguage(String minecraftCode) {
        String prefix = minecraftCode == null ? "en" : minecraftCode.split("[_-]", 2)[0];
        return byCode(prefix).orElse(ALL.getFirst());
    }

    /** Maps the codes other services use for the same language onto Whisper's. */
    private static String normalizeCode(String code) {
        String base = code.toLowerCase(Locale.ROOT).split("[_-]", 2)[0];
        return switch (base) {
            case "iw" -> "he";
            case "nb", "nn" -> "no";
            default -> base;
        };
    }
}
