package org.sawiq.voicetranslate.translation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.http.HttpRequest;
import java.time.Duration;
import org.sawiq.voicetranslate.language.Language;
import org.sawiq.voicetranslate.net.Http;
import org.sawiq.voicetranslate.net.ServiceException;

/**
 * A language model behind an OpenAI-compatible chat API: OpenAI, Groq,
 * OpenRouter, or one running at home in Ollama or LM Studio.
 *
 * <p>The best translator for voice chat. It knows that "creeper" and
 * "ender pearl" stay as they are, keeps the speaker's tone and swearing,
 * reads the previous lines to make sense of half-finished sentences, and
 * quietly fixes words the speech recognition misheard.</p>
 */
public final class LlmTranslator implements Translator {
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final String SYSTEM_PROMPT = """
            You translate live voice chat between players of the multiplayer game Minecraft.
            The input is a speech-recognition transcript of what one player just said; it may \
            contain recognition mistakes, missing punctuation and unfinished sentences.
            Translate it into %s.
            Rules:
            - Output only the translation, with no quotes, notes, or explanations.
            - Keep the speaker's tone, slang and swearing; do not soften or censor.
            - Keep player names, server names and Minecraft terms the way players of that \
            language say them (for example creeper, nether, ender pearl, redstone, spawn).
            - If a word was clearly misheard, translate what the speaker most likely meant.
            - If the text is already in %s, return it unchanged.
            """;

    private final String serviceName;
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public LlmTranslator(String serviceName, String baseUrl, String apiKey, String model) {
        this.serviceName = serviceName;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public TranslationResult translate(TranslationRequest request) throws ServiceException {
        if (this.model.isBlank()) {
            throw new ServiceException(ServiceException.Kind.CONFIGURATION, "No model is set for " + this.serviceName);
        }
        String target = Language.byCode(request.targetLanguage()).map(Language::whisperName).orElse(request.targetLanguage());

        JsonArray messages = new JsonArray();
        String system = SYSTEM_PROMPT.formatted(target, target);
        if (!request.glossary().isEmpty()) {
            StringBuilder names = new StringBuilder("Official Minecraft names in %s for words in this line, use them:".formatted(target));
            request.glossary().forEach(term -> names.append("\n- ").append(term.english()).append(" = ").append(term.translation()));
            system = system + names;
        }
        messages.add(message("system", system));
        if (!request.context().isEmpty()) {
            messages.add(message("user", "Earlier lines from the same player, for context only, do not translate:\n"
                    + String.join("\n", request.context())));
            messages.add(message("assistant", "Understood."));
        }
        messages.add(message("user", request.text()));

        JsonObject body = new JsonObject();
        body.addProperty("model", this.model);
        body.addProperty("temperature", 0.2);
        body.addProperty("max_tokens", 400);
        body.add("messages", messages);

        HttpRequest.Builder builder = HttpRequest.newBuilder(Http.endpoint(this.baseUrl, "/chat/completions"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (!this.apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + this.apiKey.strip());
        }
        JsonObject answer = Http.sendForJson(Http.CLIENT, builder.build(), this.serviceName).getAsJsonObject();
        try {
            String text = answer.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
            return new TranslationResult(stripQuotes(stripReasoning(text)), "");
        } catch (RuntimeException exception) {
            throw new ServiceException(ServiceException.Kind.BAD_RESPONSE,
                    this.serviceName + " answered in an unexpected shape", exception);
        }
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    /** Reasoning models served through Ollama and others put their thinking in front of the answer. */
    private static String stripReasoning(String text) {
        int end = text.lastIndexOf("</think>");
        return (end >= 0 ? text.substring(end + "</think>".length()) : text).strip();
    }

    private static String stripQuotes(String text) {
        if (text.length() >= 2) {
            char first = text.charAt(0);
            char last = text.charAt(text.length() - 1);
            if ((first == '"' && last == '"') || (first == '«' && last == '»') || (first == '“' && last == '”')) {
                return text.substring(1, text.length() - 1).strip();
            }
        }
        return text;
    }
}
