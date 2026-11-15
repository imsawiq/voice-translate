package org.sawiq.voicetranslate.dub;

import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.sawiq.voicetranslate.VoiceTranslate;
import org.sawiq.voicetranslate.net.ServiceException;

/**
 * Picks who speaks a sentence: the speaker's copied voice when the player
 * installed and turned on voice copying, and there is enough of the
 * speaker to copy; a matching Piper voice otherwise, and whenever the
 * copying engine is not up. A dub in a stranger's voice beats none.
 */
public final class DubVoices {
    private final PiperSynthesizer piper;
    private final ChatterboxSynthesizer clone;
    private final VoiceSamples samples;
    private final BooleanSupplier isCloneWanted;

    public DubVoices(PiperSynthesizer piper, ChatterboxSynthesizer clone, VoiceSamples samples,
                     BooleanSupplier isCloneWanted) {
        this.piper = piper;
        this.clone = clone;
        this.samples = samples;
        this.isCloneWanted = isCloneWanted;
    }

    public boolean speaks(String language) {
        return this.piper.speaks(language) || isCloning(language);
    }

    /** @return 48 kHz mono PCM; empty when nobody has a voice for the language */
    public short[] speak(String text, String language, UUID speaker) throws ServiceException {
        VoiceSample sample = this.samples.sampleFor(speaker);
        if (sample != null && isCloning(language)) {
            try {
                return this.clone.speak(text, language, sample);
            } catch (ServiceException exception) {
                if (!this.piper.speaks(language)) {
                    throw exception;
                }
                VoiceTranslate.LOGGER.debug("Copied voice failed, using Piper: {}", exception.getMessage());
            }
        }
        if (!this.piper.speaks(language)) {
            return new short[0];
        }
        return this.piper.speak(text, language, sample != null ? sample : this.samples.partialSampleFor(speaker));
    }

    private boolean isCloning(String language) {
        return this.isCloneWanted.getAsBoolean() && this.clone.isRunning() && this.clone.speaks(language);
    }
}
