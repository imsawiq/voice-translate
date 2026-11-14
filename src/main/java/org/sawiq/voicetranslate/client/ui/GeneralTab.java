package org.sawiq.voicetranslate.client.ui;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.sawiq.voicetranslate.client.FailureText;
import org.sawiq.voicetranslate.client.VoiceTranslateClient;
import org.sawiq.voicetranslate.config.TranslateConfig;
import org.sawiq.voicetranslate.pipeline.PipelineHealth;
import org.sawiq.voicetranslate.voice.VoiceBridge;

/** On/off, the languages, and how things are going. */
final class GeneralTab implements SettingsTab {
    @Override
    public String titleKey() {
        return "voice-translate.tab.general";
    }

    @Override
    public void build(FormLayout form, Context context) {
        TranslateConfig config = context.config();
        form.full(Widgets.toggle(Component.translatable("voice-translate.settings.enabled"), config.enabled, value -> {
            VoiceTranslateClient.INSTANCE.setEnabled(value);
        }));
        form.full(Widgets.cycle(Component.translatable("voice-translate.settings.target_language"),
                LanguageNames.codesWithAuto(), config.translation.targetLanguage,
                code -> LanguageNames.name(code, "voice-translate.settings.language.game"),
                code -> {
                    config.translation.targetLanguage = code;
                    context.apply().run();
                }));
        form.full(Widgets.cycle(Component.translatable("voice-translate.settings.spoken_language"),
                LanguageNames.codesWithAuto(), config.recognition.spokenLanguage,
                code -> LanguageNames.name(code, "voice-translate.settings.language.detect"),
                code -> {
                    config.recognition.spokenLanguage = code;
                    context.apply().run();
                }));
        form.full(Widgets.toggle(Component.translatable("voice-translate.settings.show_untranslated"),
                config.translation.showUntranslated, value -> {
                    config.translation.showUntranslated = value;
                    context.apply().run();
                }));

        form.text(voiceModStatus(), 0xFFB0B0B0);
        form.liveText(GeneralTab::pipelineStatus, 0xFFB0B0B0);
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
        return Component.translatable("voice-translate.status.voice.none").withStyle(ChatFormatting.RED);
    }

    private static Component pipelineStatus() {
        PipelineHealth health = VoiceTranslateClient.INSTANCE.pipeline().health();
        if (health.lastFailure() != null) {
            return Component.translatable("voice-translate.status.failing",
                    Component.translatable(FailureText.keyFor(health.lastFailure().kind()))).withStyle(ChatFormatting.RED);
        }
        if (health.translatedCount() == 0) {
            return Component.translatable("voice-translate.status.waiting");
        }
        return Component.translatable("voice-translate.status.working",
                health.translatedCount(), String.format(Locale.ROOT, "%.1f", health.averageLatencyMillis() / 1000.0));
    }
}
