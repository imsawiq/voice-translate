package org.sawiq.voicetranslate.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.sawiq.voicetranslate.VoiceTranslate;
import org.sawiq.voicetranslate.translation.GameGlossary;

/**
 * Builds the {@link GameGlossary} for the reader's language from the
 * game's own language files, in the background.
 */
final class GlossaryLoader {
    private volatile GameGlossary glossary = GameGlossary.EMPTY;
    private String requestedFor = "";

    GameGlossary current() {
        return this.glossary;
    }

    /**
     * Starts building the glossary for a target language, unless it is the
     * one already built or being built. Game thread.
     *
     * @param onReady run on the game thread once the new glossary is in place
     */
    void request(String targetLanguage, Runnable onReady) {
        if (targetLanguage.equals(this.requestedFor)) {
            return;
        }
        this.requestedFor = targetLanguage;
        this.glossary = GameGlossary.EMPTY;
        Minecraft client = Minecraft.getInstance();
        String minecraftCode = minecraftCodeFor(targetLanguage, client);
        if (minecraftCode == null || minecraftCode.equals("en_us")) {
            return;
        }
        ResourceManager resources = client.getResourceManager();
        boolean capitalizesNouns = targetLanguage.equals("de");
        CompletableFuture.supplyAsync(() -> GameGlossary.from(read(resources, "en_us"), read(resources, minecraftCode), capitalizesNouns))
                .thenAcceptAsync(built -> {
                    if (targetLanguage.equals(this.requestedFor)) {
                        this.glossary = built;
                        VoiceTranslate.LOGGER.info("Game glossary for {}: {} names", minecraftCode, built.size());
                        onReady.run();
                    }
                }, client)
                .exceptionally(failure -> {
                    VoiceTranslate.LOGGER.warn("Could not build the game glossary for {}", minecraftCode, failure);
                    return null;
                });
    }

    /** The game's language code for an ISO code: the one the game is set to if it matches, else the main variant. */
    private static String minecraftCodeFor(String iso, Minecraft client) {
        String selected = client.options.languageCode.toLowerCase(Locale.ROOT);
        if (selected.startsWith(iso + "_")) {
            return selected;
        }
        String preferred = switch (iso) {
            case "zh" -> "zh_cn";
            case "pt" -> "pt_br";
            case "he" -> "he_il";
            case "no" -> "nb_no";
            default -> iso + "_" + iso;
        };
        Map<String, ?> available = client.getLanguageManager().getLanguages();
        if (available.containsKey(preferred)) {
            return preferred;
        }
        return available.keySet().stream().filter(code -> code.startsWith(iso + "_")).findFirst().orElse(null);
    }

    /** Every pack's copy of the language file, later packs overriding earlier ones, as the game does. */
    private static Map<String, String> read(ResourceManager resources, String code) {
        Map<String, String> strings = new HashMap<>();
        for (Resource resource : resources.getResourceStack(Identifier.withDefaultNamespace("lang/" + code + ".json"))) {
            try (Reader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        strings.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            } catch (IOException | RuntimeException exception) {
                VoiceTranslate.LOGGER.debug("Skipping an unreadable {} language file", code, exception);
            }
        }
        return strings;
    }
}
