package org.sawiq.voicetranslate.client.ui;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import org.sawiq.voicetranslate.client.FailureText;
import org.sawiq.voicetranslate.client.VoiceTranslateClient;
import org.sawiq.voicetranslate.config.TranslateConfig;
import org.sawiq.voicetranslate.pipeline.PipelineHealth;
import org.sawiq.voicetranslate.voice.VoiceBridge;

/** On and off, the two languages, and how things are going. */
final class GeneralTab implements SettingsTab {
    @Override
    public void build(SettingsList list, Context context) {
        TranslateConfig config = context.config();
        list.category(Component.translatable("voice-translate.category.translation"));
        list.option(Component.translatable("voice-translate.settings.enabled"),
                Component.translatable("voice-translate.settings.enabled.desc"),
                Widgets.toggle(Component.translatable("voice-translate.settings.enabled"), config.enabled,
                        VoiceTranslateClient.INSTANCE::setEnabled));
        list.option(Component.translatable("voice-translate.settings.target_language"),
                Component.translatable("voice-translate.settings.target_language.desc"),
                Widgets.cycle(Component.translatable("voice-translate.settings.target_language"),
                        LanguageNames.codesWithAuto(), config.translation.targetLanguage,
                        code -> LanguageNames.name(code, "voice-translate.settings.language.game"),
                        code -> {
                            config.translation.targetLanguage = code;
                            context.apply().run();
                        }));
        list.option(Component.translatable("voice-translate.settings.spoken_language"),
                Component.translatable("voice-translate.settings.spoken_language.desc"),
                Widgets.cycle(Component.translatable("voice-translate.settings.spoken_language"),
                        LanguageNames.codesWithAuto(), config.recognition.spokenLanguage,
                        code -> LanguageNames.name(code, "voice-translate.settings.language.detect"),
                        code -> {
                            config.recognition.spokenLanguage = code;
                            context.apply().run();
                        }));
        list.option(Component.translatable("voice-translate.settings.show_untranslated"),
                Component.translatable("voice-translate.settings.show_untranslated.desc"),
                Widgets.toggle(Component.translatable("voice-translate.settings.show_untranslated"),
                        config.translation.showUntranslated, value -> {
                            config.translation.showUntranslated = value;
                            context.apply().run();
                        }));

        list.category(Component.translatable("voice-translate.category.status"));
        list.text(GeneralTab::voiceModStatus, () -> VoiceBridge.isAnyVoiceModHooked() ? UiPaint.GREEN : UiPaint.RED, 1);
        list.text(GeneralTab::pipelineStatus, GeneralTab::pipelineColor, 2);
        list.text(Component.translatable("voice-translate.status.where_to_find"), UiPaint.DIM, 3);
    }

    private static Component voiceModStatus() {
        if (VoiceBridge.isSimpleVoiceChatHooked() && VoiceBridge.isPlasmoVoiceHooked()) {
            return Component.translatable("voice-translate.status.voice.both");
        }
        if (VoiceBridge.isSimpleVoiceChatHooked()) {
            return Component.translatable("voice-translate.status.voice.svc");
        }
        if (VoiceBridge.isPlasmoVoiceHooked()) {
            return Component.translatable("voice-translate.status.voice.plasmo");
        }
        return Component.translatable("voice-translate.status.voice.none");
    }

    private static Component pipelineStatus() {
        PipelineHealth health = VoiceTranslateClient.INSTANCE.pipeline().health();
        if (!VoiceTranslateClient.INSTANCE.config().enabled) {
            return Component.translatable("voice-translate.status.off");
        }
        if (health.lastFailure() != null) {
            return Component.translatable("voice-translate.status.failing",
                    Component.translatable(FailureText.keyFor(health.lastFailure().kind())));
        }
        if (health.translatedCount() == 0) {
            return Component.translatable("voice-translate.status.waiting");
        }
        return Component.translatable("voice-translate.status.working", health.translatedCount(),
                String.format(Locale.ROOT, "%.1f", health.averageLatencyMillis() / 1000.0));
    }

    private static int pipelineColor() {
        PipelineHealth health = VoiceTranslateClient.INSTANCE.pipeline().health();
        if (!VoiceTranslateClient.INSTANCE.config().enabled) {
            return UiPaint.GREY;
        }
        return health.lastFailure() != null ? UiPaint.RED : UiPaint.TEXT;
    }
}
