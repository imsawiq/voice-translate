package org.sawiq.voicetranslate.dub;

import org.sawiq.voicetranslate.net.ServiceException;

/** Speaks text. Blocks; called from the dubbing workers only. */
public interface VoiceSynthesizer {
    /**
     * @param language  ISO 639-1 code; one of {@link DubLanguages#SUPPORTED}
     * @param voice     whose voice to use, or null for the engine's own
     * @return 48 kHz mono PCM, the rate both voice mods play
     */
    short[] speak(String text, String language, VoiceSample voice) throws ServiceException;
}
