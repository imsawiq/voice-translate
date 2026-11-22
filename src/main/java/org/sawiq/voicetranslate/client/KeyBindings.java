package org.sawiq.voicetranslate.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.sawiq.voicetranslate.VoiceTranslate;

/** The mod's keys, under their own heading in the controls screen. */
public final class KeyBindings {
    public static final Identifier CATEGORY_ID = Identifier.fromNamespaceAndPath(VoiceTranslate.MOD_ID, "main");

    private final KeyMapping openSettings;
    private final KeyMapping toggle;
    private final KeyMapping openHistory;
    private final KeyMapping toggleMySpeech;

    /** @param category the heading; each loader registers it its own way */
    public KeyBindings(KeyMapping.Category category) {
        this.openSettings = new KeyMapping("key.voice-translate.settings", InputConstants.KEY_J, category);
        this.toggle = new KeyMapping("key.voice-translate.toggle", InputConstants.UNKNOWN.getValue(), category);
        this.openHistory = new KeyMapping("key.voice-translate.history", InputConstants.UNKNOWN.getValue(), category);
        this.toggleMySpeech = new KeyMapping("key.voice-translate.my_speech", InputConstants.UNKNOWN.getValue(), category);
    }

    public List<KeyMapping> all() {
        return List.of(this.openSettings, this.toggle, this.openHistory, this.toggleMySpeech);
    }

    KeyMapping openSettings() {
        return this.openSettings;
    }

    KeyMapping toggle() {
        return this.toggle;
    }

    KeyMapping toggleMySpeech() {
        return this.toggleMySpeech;
    }

    KeyMapping openHistory() {
        return this.openHistory;
    }
}
