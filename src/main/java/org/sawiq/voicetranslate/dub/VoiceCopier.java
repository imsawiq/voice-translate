package org.sawiq.voicetranslate.dub;

import com.google.gson.JsonObject;
import java.util.Base64;
import java.util.function.Supplier;
import org.sawiq.voicetranslate.audio.Pcm;
import org.sawiq.voicetranslate.net.ServiceException;

/**
 * The optional voice copying engine on this computer: OpenVoice turns what a
 * Piper voice said into the speaker's own voice, copied from a few seconds
 * of their speech, and marks it with an inaudible watermark. Runs on the
 * processor, faster than real time.
 */
public final class VoiceCopier {
    private static final int RATE = 48_000;

    private final Supplier<String> engineAddress;

    /** @param engineAddress the running voice copying engine's base URL, or null while it is not running */
    public VoiceCopier(Supplier<String> engineAddress) {
        this.engineAddress = engineAddress;
    }

    /** Whether the engine is up to take a sentence right now. */
    public boolean isRunning() {
        return this.engineAddress.get() != null;
    }

    /**
     * @param spoken 48 kHz mono speech in a Piper voice
     * @param voice  the speaker to sound like
     * @return the same speech in their voice, 48 kHz mono
     */
    public short[] copy(short[] spoken, VoiceSample voice) throws ServiceException {
        JsonObject body = new JsonObject();
        body.addProperty("audio", Base64.getEncoder().encodeToString(Pcm.toLittleEndianBytes(spoken)));
        body.addProperty("rate", RATE);
        body.addProperty("speaker", voice.key());
        body.addProperty("reference", Base64.getEncoder().encodeToString(Pcm.toLittleEndianBytes(voice.samples())));
        return EngineAudio.post(this.engineAddress.get(), "/convert", body, "voice copying engine");
    }
}
