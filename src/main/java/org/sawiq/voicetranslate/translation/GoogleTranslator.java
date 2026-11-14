package org.sawiq.voicetranslate.translation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import org.sawiq.voicetranslate.net.Http;
import org.sawiq.voicetranslate.net.ServiceException;

/**
 * Google Translate through the endpoints its own browser widgets use, which
 * need no key.
 *
 * <p>Fast and good enough for most languages, and the reason the mod works
 * the moment it is installed. These are not official APIs, so Google
 * throttles a burst of requests now and then; there are two of them with
 * separate limits, and when one answers "too many requests" the other is
 * used for a while.</p>
 */
public final class GoogleTranslator implements Translator {
    private static final String TRANSLATE_ENDPOINT = "https://translate.googleapis.com/translate_a/single?client=gtx&dt=t";
    private static final String DICTIONARY_ENDPOINT = "https://clients5.google.com/translate_a/t?client=dict-chrome-ex";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final long SWITCH_BACK_AFTER_MILLIS = 120_000L;

    /** When the translate endpoint last refused; until the cooldown ends the dictionary one is used. */
    private volatile long translateEndpointThrottledAt;

    @Override
    public TranslationResult translate(TranslationRequest request) throws ServiceException {
        boolean preferDictionary = System.currentTimeMillis() - this.translateEndpointThrottledAt < SWITCH_BACK_AFTER_MILLIS;
        try {
            return preferDictionary ? viaDictionary(request) : viaTranslate(request);
        } catch (ServiceException exception) {
            if (exception.kind() != ServiceException.Kind.RATE_LIMITED && exception.kind() != ServiceException.Kind.NETWORK) {
                throw exception;
            }
            if (!preferDictionary) {
                this.translateEndpointThrottledAt = System.currentTimeMillis();
            }
            return preferDictionary ? viaTranslate(request) : viaDictionary(request);
        }
    }

    private TranslationResult viaTranslate(TranslationRequest request) throws ServiceException {
        JsonElement answer = get(TRANSLATE_ENDPOINT + query(request));
        return parseTranslate(answer);
    }

    private TranslationResult viaDictionary(TranslationRequest request) throws ServiceException {
        JsonElement answer = get(DICTIONARY_ENDPOINT + query(request));
        return parseDictionary(answer);
    }

    private static String query(TranslationRequest request) {
        String source = request.sourceLanguage().isEmpty() ? "auto" : googleCode(request.sourceLanguage());
        return "&sl=" + Http.encode(source)
                + "&tl=" + Http.encode(googleCode(request.targetLanguage()))
                + "&q=" + Http.encode(request.textWithGlossary());
    }

    private static JsonElement get(String url) throws ServiceException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
        return Http.sendForJson(Http.CLIENT, request, "Google Translate");
    }

    /**
     * A nested array: the first element holds the translated sentences, each
     * as [translation, original, ...], and the third the detected language.
     */
    static TranslationResult parseTranslate(JsonElement answer) throws ServiceException {
        try {
            JsonArray root = answer.getAsJsonArray();
            StringBuilder text = new StringBuilder();
            for (JsonElement sentence : root.get(0).getAsJsonArray()) {
                JsonElement part = sentence.getAsJsonArray().get(0);
                if (!part.isJsonNull()) {
                    text.append(part.getAsString());
                }
            }
            String detected = root.size() > 2 && root.get(2).isJsonPrimitive() ? root.get(2).getAsString() : "";
            return new TranslationResult(text.toString().strip(), detected);
        } catch (RuntimeException exception) {
            throw unexpected(exception);
        }
    }

    /**
     * ["translation"] when the source language was given, or
     * [["translation", "detected"]] when it was detected.
     */
    static TranslationResult parseDictionary(JsonElement answer) throws ServiceException {
        try {
            JsonElement first = answer.getAsJsonArray().get(0);
            if (first.isJsonArray()) {
                JsonArray pair = first.getAsJsonArray();
                return new TranslationResult(pair.get(0).getAsString().strip(), pair.size() > 1 ? pair.get(1).getAsString() : "");
            }
            return new TranslationResult(first.getAsString().strip(), "");
        } catch (RuntimeException exception) {
            throw unexpected(exception);
        }
    }

    private static ServiceException unexpected(RuntimeException cause) {
        return new ServiceException(ServiceException.Kind.BAD_RESPONSE, "Google Translate answered in an unexpected shape", cause);
    }

    private static String googleCode(String code) {
        return switch (code) {
            case "zh" -> "zh-CN";
            case "he" -> "iw";
            default -> code;
        };
    }
}
