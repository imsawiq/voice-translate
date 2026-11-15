package org.sawiq.voicetranslate.dub;

import com.google.gson.JsonObject;
import java.util.Base64;
import java.util.Set;
import java.util.function.Supplier;
import org.sawiq.voicetranslate.audio.Pcm;
import org.sawiq.voicetranslate.net.ServiceException;

/**
 * The optional voice-copying engine on this computer: Chatterbox, which
 * speaks in each player's own voice, copied from their speech. Heavy and
 * slower than real time, so only used when the player installed it.
 */
public final class ChatterboxSynthesizer implements VoiceSynthesizer {
    /** The languages Chatterbox Multilingual speaks. */
    private static final Set<String> LANGUAGES = Set.of(
            "ar", "da", "de", "el", "en", "es", "fi", "fr", "he", "hi", "it", "ja",
            "ko", "ms", "nl", "no", "pl", "pt", "ru", "sv", "sw", "tr", "zh");

    private final Supplier<String> engineAddress;

    /** @param engineAddress the running voice engine's base URL, or null while it is not running */
    public ChatterboxSynthesizer(Supplier<String> engineAddress) {
        this.engineAddress = engineAddress;
    }

    /** Whether the engine is up to take a sentence right now. */
    public boolean isRunning() {
        return this.engineAddress.get() != null;
    }

    @Override
    public boolean speaks(String language) {
        return LANGUAGES.contains(language);
    }

    @Override
    public short[] speak(String text, String language, VoiceSample voice) throws ServiceException {
        JsonObject body = new JsonObject();
        body.addProperty("text", text);
        body.addProperty("language", language);
        if (voice != null) {
            body.addProperty("speaker", voice.key());
            body.addProperty("reference", Base64.getEncoder().encodeToString(Pcm.toLittleEndianBytes(voice.samples())));
        }
        return SpeakRequest.send(this.engineAddress.get(), body, "voice engine");
    }
}
