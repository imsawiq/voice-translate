package org.sawiq.voicetranslate.dub;

import java.util.Set;

/** The languages the voice model speaks; a translation into any other is shown, not spoken. */
public final class DubLanguages {
    public static final Set<String> SUPPORTED = Set.of(
            "ar", "da", "de", "el", "en", "es", "fi", "fr", "he", "hi", "it", "ja",
            "ko", "ms", "nl", "no", "pl", "pt", "ru", "sv", "sw", "tr", "zh");

    private DubLanguages() {
    }

    public static boolean isSpoken(String language) {
        return SUPPORTED.contains(language);
    }
}
