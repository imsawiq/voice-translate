package org.sawiq.voicetranslate.translation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Locale;
import org.sawiq.voicetranslate.net.Http;
import org.sawiq.voicetranslate.net.ServiceException;

/**
 * DeepL, the most natural translation for European languages. The free plan
 * covers half a million characters a month, which is a lot of talking.
 *
 * <p>The player's previous lines go in DeepL's {@code context} field: they
 * steer the translation without being translated or billed.</p>
 */
public final class DeepLTranslator implements Translator {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final String apiKey;

    public DeepLTranslator(String apiKey) {
        this.apiKey = apiKey.strip();
    }

    @Override
    public TranslationResult translate(TranslationRequest request) throws ServiceException {
        if (this.apiKey.isEmpty()) {
            throw new ServiceException(ServiceException.Kind.CONFIGURATION, "No DeepL key is set");
        }
        JsonObject body = new JsonObject();
        JsonArray texts = new JsonArray();
        texts.add(request.textWithGlossary());
        body.add("text", texts);
        body.addProperty("target_lang", targetCode(request.targetLanguage()));
        if (!request.sourceLanguage().isEmpty()) {
            body.addProperty("source_lang", request.sourceLanguage().toUpperCase(Locale.ROOT));
        }
        if (!request.context().isEmpty()) {
            body.addProperty("context", String.join("\n", request.context()));
        }

        HttpRequest httpRequest = HttpRequest.newBuilder(endpoint())
                .timeout(TIMEOUT)
                .header("Authorization", "DeepL-Auth-Key " + this.apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        JsonObject answer = Http.sendForJson(Http.CLIENT, httpRequest, "DeepL").getAsJsonObject();
        try {
            JsonObject first = answer.getAsJsonArray("translations").get(0).getAsJsonObject();
            String detected = first.has("detected_source_language")
                    ? first.get("detected_source_language").getAsString().toLowerCase(Locale.ROOT)
                    : "";
            return new TranslationResult(first.get("text").getAsString().strip(), detected);
        } catch (RuntimeException exception) {
            throw new ServiceException(ServiceException.Kind.BAD_RESPONSE, "DeepL answered in an unexpected shape", exception);
        }
    }

    /** Keys of the free plan end in ":fx" and only work on the free host. */
    private URI endpoint() {
        String host = this.apiKey.endsWith(":fx") ? "api-free.deepl.com" : "api.deepl.com";
        return URI.create("https://" + host + "/v2/translate");
    }

    /** DeepL wants a regional variant for a few targets. */
    private static String targetCode(String code) {
        return switch (code) {
            case "en" -> "EN-US";
            case "pt" -> "PT-BR";
            case "zh" -> "ZH-HANS";
            case "no" -> "NB";
            default -> code.toUpperCase(Locale.ROOT);
        };
    }
}
