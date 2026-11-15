package org.sawiq.voicetranslate.dub;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.sawiq.voicetranslate.net.ServiceException;

/**
 * Piper voices in the speech engine: a sentence in a fraction of a second on
 * any processor, about 60 MB per language, downloaded when first needed.
 * Not the speaker's own voice, but a male or female one to match theirs.
 */
public final class PiperSynthesizer implements VoiceSynthesizer {
    /** A touch quicker than Piper's natural pace, so a dub keeps up with the next sentence. */
    private static final double SPEED = 1.1;
    private static final int MAX_REMEMBERED_VOICES = 64;

    private final Supplier<String> engineAddress;
    private final Map<String, Boolean> isLowByVoice = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > MAX_REMEMBERED_VOICES;
        }
    };

    /** @param engineAddress the running speech engine's base URL, or null while it is not running */
    public PiperSynthesizer(Supplier<String> engineAddress) {
        this.engineAddress = engineAddress;
    }

    @Override
    public boolean speaks(String language) {
        return PiperLanguages.SUPPORTED.contains(language);
    }

    /** @param voice whose voice to match in pitch, or null for a male voice */
    @Override
    public short[] speak(String text, String language, VoiceSample voice) throws ServiceException {
        JsonObject body = new JsonObject();
        body.addProperty("text", text);
        body.addProperty("language", language);
        body.addProperty("voice", voice == null || isLow(voice) ? "male" : "female");
        body.addProperty("speed", SPEED);
        return SpeakRequest.send(this.engineAddress.get(), body, "speech engine");
    }

    private boolean isLow(VoiceSample voice) {
        synchronized (this.isLowByVoice) {
            Boolean known = this.isLowByVoice.get(voice.key());
            if (known != null) {
                return known;
            }
        }
        boolean isLow = VoicePitch.isLow(voice.samples());
        synchronized (this.isLowByVoice) {
            this.isLowByVoice.put(voice.key(), isLow);
        }
        return isLow;
    }
}
