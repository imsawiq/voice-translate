package org.sawiq.voicetranslate.dub;

import org.sawiq.voicetranslate.net.ServiceException;

/** Speaks text. Blocks; called from the dubbing workers only. */
public interface VoiceSynthesizer {
    /** Whether it has a voice for this ISO 639-1 language. */
    boolean speaks(String language);

    /**
     * @param voice whose voice to use or match, or null for the engine's own
     * @return 48 kHz mono PCM, the rate both voice mods play; empty when there is no voice for the language
     */
    short[] speak(String text, String language, VoiceSample voice) throws ServiceException;
}
