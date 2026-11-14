package org.sawiq.voicetranslate.client.ui;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import org.sawiq.voicetranslate.audio.VoiceIntake;
import org.sawiq.voicetranslate.client.FailureText;
import org.sawiq.voicetranslate.client.VoiceTranslateClient;
import org.sawiq.voicetranslate.config.RecognitionService;
import org.sawiq.voicetranslate.config.TranslateConfig;
import org.sawiq.voicetranslate.engine.LocalEngine;
import org.sawiq.voicetranslate.pipeline.PipelineHealth;
import org.sawiq.voicetranslate.voice.VoiceBridge;

/** On and off, the two languages, and an honest account of how things are going. */
final class GeneralTab implements SettingsTab {
    /** A failure older than this is history, not the current state. */
    private static final long RECENT_FAILURE_MILLIS = 60_000L;

    /** One line of the status block: what to say and in which colour. */
    private record Status(Component text, int color) {
    }

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
        list.option(Component.translatable("voice-translate.settings.own_voice"),
                Component.translatable("voice-translate.settings.own_voice.desc"),
                Widgets.toggle(Component.translatable("voice-translate.settings.own_voice"),
                        config.translateOwnVoice, value -> {
                            config.translateOwnVoice = value;
                            context.apply().run();
                        }));

        list.category(Component.translatable("voice-translate.category.status"));
        list.text(() -> voiceModStatus().text(), () -> voiceModStatus().color(), 1);
        list.text(() -> heardStatus().text(), () -> heardStatus().color(), 1);
        list.text(() -> pipelineStatus().text(), () -> pipelineStatus().color(), 2);
        list.text(Component.translatable("voice-translate.status.where_to_find"), UiPaint.DIM, 3);
    }

    private static Status voiceModStatus() {
        String key;
        if (VoiceBridge.isSimpleVoiceChatHooked() && VoiceBridge.isPlasmoVoiceHooked()) {
            key = "voice-translate.status.voice.both";
        } else if (VoiceBridge.isSimpleVoiceChatHooked()) {
            key = "voice-translate.status.voice.svc";
        } else if (VoiceBridge.isPlasmoVoiceHooked()) {
            key = "voice-translate.status.voice.plasmo";
        } else {
            return new Status(Component.translatable("voice-translate.status.voice.none"), UiPaint.RED);
        }
        return new Status(Component.translatable(key), UiPaint.GREEN);
    }

    /** Proof that voices arrive at all: who was heard last, and how long ago. */
    private static Status heardStatus() {
        VoiceTranslateClient translator = VoiceTranslateClient.INSTANCE;
        VoiceIntake.Heard heard = translator.lastHeard();
        if (heard == null) {
            return new Status(Component.translatable("voice-translate.status.heard.nobody"), UiPaint.GREY);
        }
        long seconds = Math.max(0L, (System.currentTimeMillis() - heard.at()) / 1000L);
        String name = translator.nameOf(heard.speakerId(), heard.speakerName());
        return new Status(Component.translatable("voice-translate.status.heard", name, seconds),
                seconds < 3 ? UiPaint.GREEN : UiPaint.TEXT);
    }

    /**
     * What stands between a voice and a subtitle right now. The local engine
     * comes first: while it starts or loads a model nothing can work, and an
     * old "not ready" from before it finished must not linger once it has.
     */
    private static Status pipelineStatus() {
        VoiceTranslateClient translator = VoiceTranslateClient.INSTANCE;
        TranslateConfig config = translator.config();
        if (!config.enabled) {
            return new Status(Component.translatable("voice-translate.status.off"), UiPaint.GREY);
        }
        LocalEngine engine = translator.engine();
        if (config.recognition.service == RecognitionService.LOCAL && engine.status().phase() != LocalEngine.Phase.READY) {
            return new Status(Component.translatable("voice-translate.status.engine", EngineStatusText.describe(engine)),
                    EngineStatusText.color(engine));
        }
        PipelineHealth health = translator.pipeline().health();
        boolean isRecentFailure = health.lastFailure() != null
                && System.currentTimeMillis() - health.lastFailureAt() < RECENT_FAILURE_MILLIS;
        if (isRecentFailure) {
            return new Status(Component.translatable("voice-translate.status.failing",
                    Component.translatable(FailureText.keyFor(health.lastFailure().kind()))), UiPaint.RED);
        }
        if (health.translatedCount() == 0) {
            return new Status(Component.translatable("voice-translate.status.waiting"), UiPaint.TEXT);
        }
        return new Status(Component.translatable("voice-translate.status.working", health.translatedCount(),
                String.format(Locale.ROOT, "%.1f", health.averageLatencyMillis() / 1000.0)), UiPaint.GREEN);
    }
}
