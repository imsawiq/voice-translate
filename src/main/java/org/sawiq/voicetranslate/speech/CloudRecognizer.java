package org.sawiq.voicetranslate.speech;

import com.google.gson.JsonObject;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import org.sawiq.voicetranslate.audio.Pcm;
import org.sawiq.voicetranslate.language.Language;
import org.sawiq.voicetranslate.net.Http;
import org.sawiq.voicetranslate.net.Multipart;
import org.sawiq.voicetranslate.net.ServiceException;

/**
 * Any speech service that speaks OpenAI's transcription API: Groq, OpenAI
 * itself, or a self-hosted server such as speaches.
 *
 * <p>Whisper models are asked for {@code verbose_json}, which carries the
 * detected language and the per-segment confidence the hallucination filter
 * relies on. OpenAI's newer gpt-4o transcription models only answer plain
 * JSON; for those the language is left to the translator to detect.</p>
 */
public final class CloudRecognizer implements SpeechRecognizer {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final String serviceName;
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public CloudRecognizer(String serviceName, String baseUrl, String apiKey, String model) {
        this.serviceName = serviceName;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public Transcript transcribe(RecognitionRequest request) throws ServiceException {
        if (this.model.isBlank()) {
            throw new ServiceException(ServiceException.Kind.CONFIGURATION, "No speech model is set for " + this.serviceName);
        }
        boolean isVerbose = !this.model.startsWith("gpt-");
        Multipart form = new Multipart()
                .file("file", "speech.wav", "audio/wav", Pcm.toWav(request.samples(), Pcm.SPEECH_SAMPLE_RATE))
                .field("model", this.model)
                .field("temperature", "0")
                .field("response_format", isVerbose ? "verbose_json" : "json");
        if (!request.languageHint().isEmpty()) {
            form.field("language", request.languageHint());
        }
        if (!request.prompt().isEmpty()) {
            form.field("prompt", request.prompt());
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(Http.endpoint(this.baseUrl, "/audio/transcriptions"))
                .timeout(TIMEOUT)
                .header("Content-Type", form.contentType())
                .POST(HttpRequest.BodyPublishers.ofByteArray(form.build()));
        if (!this.apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + this.apiKey.strip());
        }
        JsonObject answer = Http.sendForJson(Http.CLIENT, builder.build(), this.serviceName).getAsJsonObject();

        String language = request.languageHint();
        double probability = language.isEmpty() ? 0.0 : 1.0;
        String reported = LocalWhisperRecognizer.stringOrEmpty(answer, "language");
        if (language.isEmpty() && !reported.isEmpty()) {
            // OpenAI-style APIs report "english" rather than "en".
            language = Language.byWhisperName(reported).map(Language::code).orElse("");
            probability = language.isEmpty() ? 0.0 : 0.8;
        }
        List<Transcript.Segment> segments = isVerbose
                ? LocalWhisperRecognizer.readSegments(answer.getAsJsonArray("segments"))
                : List.of();
        return new Transcript(LocalWhisperRecognizer.stringOrEmpty(answer, "text").strip(), language, probability, segments);
    }

    @Override
    public int parallelism() {
        return 3;
    }

    @Override
    public boolean isCheapPerRequest() {
        return false;
    }
}
