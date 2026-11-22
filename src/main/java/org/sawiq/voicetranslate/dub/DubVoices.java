package org.sawiq.voicetranslate.dub;

import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.sawiq.voicetranslate.VoiceTranslate;
import org.sawiq.voicetranslate.net.ServiceException;

/**
 * Speaks a sentence for a player: a Piper voice that matches theirs says it,
 * and when the player turned voice copying on, the copying engine is up
 * and there is enough of the speaker to copy, it is turned into their own
 * voice. A copy that fails falls back to the Piper voice: a dub in a
 * stranger's voice beats none.
 */
public final class DubVoices {
    private final PiperSynthesizer piper;
    private final VoiceCopier copier;
    private final VoiceSamples samples;
    private final BooleanSupplier isCopyWanted;

    public DubVoices(PiperSynthesizer piper, VoiceCopier copier, VoiceSamples samples, BooleanSupplier isCopyWanted) {
        this.piper = piper;
        this.copier = copier;
        this.samples = samples;
        this.isCopyWanted = isCopyWanted;
    }

    public boolean speaks(String language) {
        return this.piper.speaks(language);
    }

    /** @return 48 kHz mono PCM; empty when there is no voice for the language */
    public short[] speak(String text, String language, UUID speaker) throws ServiceException {
        VoiceSample sample = this.samples.sampleFor(speaker);
        short[] spoken = this.piper.speak(text, language, sample != null ? sample : this.samples.partialSampleFor(speaker));
        if (spoken.length == 0 || sample == null || !this.isCopyWanted.getAsBoolean() || !this.copier.isRunning()) {
            return spoken;
        }
        try {
            return this.copier.copy(spoken, sample);
        } catch (ServiceException exception) {
            VoiceTranslate.LOGGER.debug("Copying the voice failed, speaking with Piper: {}", exception.getMessage());
            return spoken;
        }
    }
}
