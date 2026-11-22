package org.sawiq.voicetranslate.speech;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.sawiq.voicetranslate.audio.Pcm;
import org.sawiq.voicetranslate.net.Http;
import org.sawiq.voicetranslate.net.ServiceException;

/**
 * Whisper running on this computer, in the engine the mod installs.
 *
 * <p>Nothing leaves the computer, there is no key and no bill, and with a
 * graphics card large-v3-turbo answers in a fraction of a second, which is
 * why this is the default.</p>
 */
public final class LocalWhisperRecognizer implements SpeechRecognizer {
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final Supplier<String> engineAddress;

    /** @param engineAddress the running engine's base URL, or null while it is not running */
    public LocalWhisperRecognizer(Supplier<String> engineAddress) {
        this.engineAddress = engineAddress;
    }

    @Override
    public Transcript transcribe(RecognitionRequest request) throws ServiceException {
        String address = this.engineAddress.get();
        if (address == null) {
            throw new ServiceException(ServiceException.Kind.NOT_READY, "The speech engine is not running");
        }
        String query = "?beam=" + (request.isDraft() ? 1 : 5)
                + "&language=" + Http.encode(request.languageHint())
                + "&prompt=" + Http.encode(request.prompt());
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(address + "/transcribe" + query))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(Pcm.toLittleEndianBytes(request.samples())))
                .build();
        JsonObject answer = Http.sendForJson(Http.LOCAL, httpRequest, "The speech engine").getAsJsonObject();
        return new Transcript(
                stringOrEmpty(answer, "text"),
                stringOrEmpty(answer, "language"),
                answer.has("language_probability") ? answer.get("language_probability").getAsDouble() : 0.0,
                readSegments(answer.getAsJsonArray("segments")));
    }

    @Override
    public int parallelism() {
        return 1;
    }

    @Override
    public boolean isCheapPerRequest() {
        return true;
    }

    static List<Transcript.Segment> readSegments(JsonArray array) {
        List<Transcript.Segment> segments = new ArrayList<>();
        if (array == null) {
            return segments;
        }
        for (JsonElement element : array) {
            JsonObject segment = element.getAsJsonObject();
            segments.add(new Transcript.Segment(
                    stringOrEmpty(segment, "text"),
                    numberOr(segment, "avg_logprob", 0.0),
                    numberOr(segment, "no_speech_prob", 0.0),
                    numberOr(segment, "compression_ratio", 1.0)));
        }
        return segments;
    }

    static String stringOrEmpty(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static double numberOr(JsonObject object, String key, double fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsDouble();
    }
}
