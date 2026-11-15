package org.sawiq.voicetranslate.client.ui;

import java.util.Arrays;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.sawiq.voicetranslate.client.VoiceTranslateClient;
import org.sawiq.voicetranslate.config.DubbingSettings;
import org.sawiq.voicetranslate.config.RecognitionSettings;
import org.sawiq.voicetranslate.dub.PiperLanguages;
import org.sawiq.voicetranslate.engine.EngineComponent;
import org.sawiq.voicetranslate.engine.PackageSet;
import org.sawiq.voicetranslate.engine.VoiceEngine;
import org.sawiq.voicetranslate.language.Language;

/**
 * Spoken translations: other players dubbed, and the player's own speech
 * sent translated. Both speak with the speech engine's light Piper voices;
 * copying players' own voices is an optional heavy engine below them.
 */
final class VoiceTab implements SettingsTab {
    private final EnginePanel clonePanel;

    VoiceTab() {
        VoiceEngine engine = VoiceTranslateClient.INSTANCE.voiceEngine();
        this.clonePanel = new EnginePanel(new EnginePanel.Engine(engine.installer(), engine::status, engine::stopForUninstall));
    }

    @Override
    public void build(SettingsList list, Context context) {
        DubbingSettings dubbing = context.config().dubbing;

        list.category(Component.translatable("voice-translate.category.dub_others"));
        list.option(Component.translatable("voice-translate.settings.dub_others"),
                Component.translatable("voice-translate.settings.dub_others.desc"),
                Widgets.toggle(Component.translatable("voice-translate.settings.dub_others"), dubbing.dubOthers, value -> {
                    dubbing.dubOthers = value;
                    context.apply().run();
                }));
        list.option(Component.translatable("voice-translate.settings.original_volume"),
                Component.translatable("voice-translate.settings.original_volume.desc"),
                Widgets.slider(0, 100, 5, dubbing.originalVolume, value -> Component.literal(value + "%"),
                        value -> dubbing.originalVolume = value));
        list.option(Component.translatable("voice-translate.settings.dub_volume"),
                Widgets.slider(10, 200, 10, dubbing.dubVolume, value -> Component.literal(value + "%"),
                        value -> dubbing.dubVolume = value));

        list.category(Component.translatable("voice-translate.category.my_speech"));
        list.option(Component.translatable("voice-translate.settings.my_speech"),
                Component.translatable("voice-translate.settings.my_speech.desc"),
                Widgets.toggle(Component.translatable("voice-translate.settings.my_speech"), dubbing.translateMySpeech, value -> {
                    dubbing.translateMySpeech = value;
                    context.apply().run();
                }));
        list.option(Component.translatable("voice-translate.settings.my_speech_language"),
                Widgets.cycle(Component.translatable("voice-translate.settings.my_speech_language"),
                        spokenLanguages(), dubbing.mySpeechLanguage,
                        code -> LanguageNames.name(code, "voice-translate.settings.language.game"),
                        code -> {
                            dubbing.mySpeechLanguage = code;
                            context.apply().run();
                        }));
        list.text(Component.translatable("voice-translate.hint.my_speech"), UiPaint.DIM, 3);
        list.text(VoiceTab::voicesLine, VoiceTab::voicesColour, 3);

        list.category(Component.translatable("voice-translate.category.voice_engine"));
        list.option(Component.translatable("voice-translate.settings.clone_voices"),
                Component.translatable("voice-translate.settings.clone_voices.desc"),
                Widgets.toggle(Component.translatable("voice-translate.settings.clone_voices"), dubbing.cloneVoices, value -> {
                    dubbing.cloneVoices = value;
                    context.apply().run();
                }));
        this.clonePanel.build(list);
        list.option(Component.translatable("voice-translate.settings.device"),
                Component.translatable("voice-translate.settings.device.desc"),
                Widgets.cycle(Component.translatable("voice-translate.settings.device"),
                        Arrays.asList(RecognitionSettings.LOCAL_DEVICES), dubbing.device,
                        device -> Component.translatable("voice-translate.settings.device." + device),
                        device -> {
                            dubbing.device = device;
                            context.apply().run();
                        }));
        list.text(Component.translatable("voice-translate.voice.explain", cloneDownloadSize()), UiPaint.DIM, 4);
    }

    /** Where the voices come from, or what is missing for them. */
    private static Component voicesLine() {
        boolean hasSpeechEngine = VoiceTranslateClient.INSTANCE.engine().installer().isInstalled();
        return Component.translatable(hasSpeechEngine ? "voice-translate.voice.piper" : "voice-translate.voice.needs_engine");
    }

    private static int voicesColour() {
        return VoiceTranslateClient.INSTANCE.engine().installer().isInstalled() ? UiPaint.DIM : UiPaint.YELLOW;
    }

    /** The languages Piper speaks, in the order the language list has them. */
    private static List<String> spokenLanguages() {
        return Language.ALL.stream().map(Language::code).filter(PiperLanguages.SUPPORTED::contains).toList();
    }

    private static int cloneDownloadSize() {
        PackageSet packages = VoiceTranslateClient.INSTANCE.voiceEngine().installer().packageSet();
        return EngineComponent.VOICE.downloadMegabytes(packages == null ? PackageSet.CUDA : packages);
    }
}
