package org.sawiq.voicetranslate.voice.plasmo;

import java.util.ArrayList;
import java.util.List;
import org.sawiq.voicetranslate.client.VoiceTranslateClient;
import org.sawiq.voicetranslate.config.TranslateConfig;
import org.sawiq.voicetranslate.language.Language;
import su.plo.config.entry.BooleanConfigEntry;
import su.plo.config.entry.IntConfigEntry;
import su.plo.slib.api.chat.component.McTextComponent;
import su.plo.voice.api.client.config.addon.AddonConfig;

/**
 * The most used Voice Translate settings, shown in Plasmo Voice's Addons tab
 * through its own addon config API, so they look exactly like every other
 * Plasmo Voice setting.
 *
 * <p>Voice Translate's own config stays the source of truth: Plasmo Voice
 * keeps a copy of these values in its config file, which is overwritten
 * from ours at start, and changes made in either window reach the other.</p>
 */
final class PlasmoSettings {
    private static final String AUTO = "auto";

    private final List<String> languageCodes = new ArrayList<>();
    private BooleanConfigEntry enabled;
    private IntConfigEntry targetLanguage;
    private BooleanConfigEntry bubbles;
    private boolean isSyncing;

    void register(AddonConfig addonConfig) {
        TranslateConfig config = VoiceTranslateClient.INSTANCE.config();
        List<String> languageNames = new ArrayList<>();
        this.languageCodes.add(AUTO);
        // Plasmo Voice translates each entry as a key; a native name is no
        // key, so it is shown as it is.
        languageNames.add("voice-translate.settings.language.game");
        for (Language language : Language.ALL) {
            this.languageCodes.add(language.code());
            languageNames.add(language.nativeName());
        }

        this.enabled = addonConfig.addToggle("enabled",
                McTextComponent.translatable("voice-translate.settings.enabled"),
                McTextComponent.translatable("voice-translate.settings.enabled.desc"),
                config.enabled);
        this.targetLanguage = addonConfig.addDropDown("target-language",
                McTextComponent.translatable("voice-translate.settings.target_language"),
                McTextComponent.translatable("voice-translate.settings.target_language.desc"),
                languageNames, false, indexOf(config.translation.targetLanguage));
        this.bubbles = addonConfig.addToggle("bubbles",
                McTextComponent.translatable("voice-translate.settings.bubbles"),
                McTextComponent.translatable("voice-translate.settings.bubbles.desc"),
                config.display.bubbles);

        pullFromConfig();
        this.enabled.addChangeListener(value -> push(() -> VoiceTranslateClient.INSTANCE.setEnabled(value)));
        this.targetLanguage.addChangeListener(index -> push(() -> {
            VoiceTranslateClient.INSTANCE.config().translation.targetLanguage =
                    this.languageCodes.get(Math.clamp(index, 0, this.languageCodes.size() - 1));
            VoiceTranslateClient.INSTANCE.applySettings();
        }));
        this.bubbles.addChangeListener(value -> push(() -> {
            VoiceTranslateClient.INSTANCE.config().display.bubbles = value;
            VoiceTranslateClient.INSTANCE.applySettings();
        }));
        VoiceTranslateClient.INSTANCE.addSettingsListener(this::pullFromConfig);
    }

    /** Shows what Voice Translate's config says, without echoing the change back to it. */
    private void pullFromConfig() {
        if (this.isSyncing) {
            return;
        }
        TranslateConfig config = VoiceTranslateClient.INSTANCE.config();
        this.isSyncing = true;
        try {
            setIfChanged(this.enabled, config.enabled);
            setIfChanged(this.bubbles, config.display.bubbles);
            int index = indexOf(config.translation.targetLanguage);
            if (this.targetLanguage.value() != index) {
                this.targetLanguage.set(index);
            }
        } finally {
            this.isSyncing = false;
        }
    }

    private void push(Runnable change) {
        if (this.isSyncing) {
            return;
        }
        this.isSyncing = true;
        try {
            change.run();
        } finally {
            this.isSyncing = false;
        }
    }

    private int indexOf(String code) {
        int index = this.languageCodes.indexOf(code);
        return Math.max(0, index);
    }

    private static void setIfChanged(BooleanConfigEntry entry, boolean value) {
        if (entry.value() != value) {
            entry.set(value);
        }
    }
}
