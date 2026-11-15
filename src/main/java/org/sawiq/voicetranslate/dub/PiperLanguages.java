package org.sawiq.voicetranslate.dub;

import java.util.Set;

/**
 * The languages Piper voices speak, the same list as VOICES in vt_engine.py.
 * A translation into any other is shown, not spoken.
 */
public final class PiperLanguages {
    public static final Set<String> SUPPORTED = Set.of(
            "ar", "ca", "cs", "cy", "da", "de", "el", "en", "es", "fa", "fi", "fr", "hi", "hu",
            "id", "is", "it", "ka", "kk", "lb", "lv", "ne", "nl", "no", "pl", "pt", "ro", "ru",
            "sk", "sl", "sr", "sv", "sw", "tr", "uk", "vi", "zh");

    private PiperLanguages() {
    }
}
