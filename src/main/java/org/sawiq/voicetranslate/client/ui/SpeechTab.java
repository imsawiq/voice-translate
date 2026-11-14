package org.sawiq.voicetranslate.client.ui;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.sawiq.voicetranslate.VoiceTranslate;
import org.sawiq.voicetranslate.client.VoiceTranslateClient;
import org.sawiq.voicetranslate.config.RecognitionService;
import org.sawiq.voicetranslate.config.RecognitionSettings;
import org.sawiq.voicetranslate.config.TranslateConfig;
import org.sawiq.voicetranslate.engine.EngineInstaller;
import org.sawiq.voicetranslate.engine.LocalEngine;
import org.sawiq.voicetranslate.engine.PackageSet;

/** Which service recognises speech, and the local engine's controls. */
final class SpeechTab implements SettingsTab {
    /** Removing deletes gigabytes of downloads, so it takes a second click within this time. */
    private static final long CONFIRM_WINDOW_MILLIS = 4_000L;

    private long removeArmedUntil;

    @Override
    public String titleKey() {
        return "voice-translate.tab.speech";
    }

    @Override
    public void build(FormLayout form, Context context) {
        TranslateConfig config = context.config();
        RecognitionSettings recognition = config.recognition;
        form.full(Widgets.cycle(Component.translatable("voice-translate.settings.recognition"),
                List.of(RecognitionService.values()), recognition.service,
                service -> Component.translatable("voice-translate.recognition." + service.name().toLowerCase(Locale.ROOT)),
                service -> {
                    recognition.service = service;
                    context.apply().run();
                    context.rebuild().run();
                }));

        switch (recognition.service) {
            case LOCAL -> buildLocal(form, context);
            case GROQ, OPENAI -> buildCloud(form, context);
            case CUSTOM -> buildCustom(form, context);
        }

        form.half(Widgets.slider(300, 2_000, 50, recognition.pauseMillis,
                value -> Component.translatable("voice-translate.settings.pause", value),
                value -> recognition.pauseMillis = value));
        form.half(Widgets.slider(3, 24, 1, recognition.sensitivityDb,
                value -> Component.translatable("voice-translate.settings.sensitivity", value),
                value -> recognition.sensitivityDb = value));
    }

    private void buildLocal(FormLayout form, Context context) {
        RecognitionSettings recognition = context.config().recognition;
        LocalEngine engine = VoiceTranslateClient.INSTANCE.engine();
        form.half(Widgets.cycle(Component.translatable("voice-translate.settings.model"),
                Arrays.asList(RecognitionSettings.LOCAL_MODELS), recognition.localModel,
                model -> "auto".equals(model)
                        ? Component.translatable("voice-translate.settings.model.auto", engine.resolveModel("auto"))
                        : Component.literal(model),
                model -> {
                    recognition.localModel = model;
                    context.apply().run();
                }));
        form.half(Widgets.cycle(Component.translatable("voice-translate.settings.device"),
                Arrays.asList(RecognitionSettings.LOCAL_DEVICES), recognition.localDevice,
                device -> Component.translatable("voice-translate.settings.device." + device),
                device -> {
                    recognition.localDevice = device;
                    context.apply().run();
                }));

        form.liveText(() -> EngineStatusText.describe(engine), 0xFFE0E0E0);
        form.full((x, y, width, height) -> {
            Button button = Button.builder(Component.empty(), pressed -> onEngineButton(engine)).bounds(x, y, width, height).build();
            form.everyTick(() -> updateEngineButton(button, engine));
            return button;
        });
        form.text(Component.translatable("voice-translate.engine.explain", downloadSize(engine)), 0xFF909090);
    }

    private void buildCloud(FormLayout form, Context context) {
        TranslateConfig config = context.config();
        RecognitionService service = config.recognition.service;
        form.labelled(Component.translatable("voice-translate.settings.api_key"),
                Widgets.secret(context.font(), Component.translatable("voice-translate.settings.api_key"),
                        config.keys.forRecognition(service), key -> config.keys.setForRecognition(service, key.strip())));
        form.labelled(Component.translatable("voice-translate.settings.model"),
                Widgets.text(context.font(), Component.translatable("voice-translate.settings.model"),
                        config.recognition.modelFor(service), model -> {
                            if (service == RecognitionService.GROQ) {
                                config.recognition.groqModel = model.strip();
                            } else {
                                config.recognition.openAiModel = model.strip();
                            }
                        }));
        form.text(Component.translatable("voice-translate.privacy.cloud_speech",
                Component.translatable("voice-translate.recognition." + service.name().toLowerCase(Locale.ROOT)))
                .withStyle(ChatFormatting.GOLD), 0xFFFFFFFF);
        if (service == RecognitionService.GROQ) {
            form.text(Component.translatable("voice-translate.hint.groq_key"), 0xFF909090);
        }
    }

    private void buildCustom(FormLayout form, Context context) {
        TranslateConfig config = context.config();
        form.labelled(Component.translatable("voice-translate.settings.address"),
                Widgets.text(context.font(), Component.translatable("voice-translate.settings.address"),
                        config.recognition.customUrl, url -> config.recognition.customUrl = url.strip()));
        form.labelled(Component.translatable("voice-translate.settings.model"),
                Widgets.text(context.font(), Component.translatable("voice-translate.settings.model"),
                        config.recognition.customModel, model -> config.recognition.customModel = model.strip()));
        form.labelled(Component.translatable("voice-translate.settings.api_key_optional"),
                Widgets.secret(context.font(), Component.translatable("voice-translate.settings.api_key"),
                        config.keys.custom, key -> config.keys.custom = key.strip()));
    }

    private void onEngineButton(LocalEngine engine) {
        EngineInstaller installer = engine.installer();
        switch (installer.progress().state()) {
            case NOT_INSTALLED, FAILED -> installer.install();
            case INSTALLING -> installer.cancel();
            case INSTALLED -> {
                long now = System.currentTimeMillis();
                if (now < this.removeArmedUntil) {
                    this.removeArmedUntil = 0L;
                    uninstall(engine);
                } else {
                    this.removeArmedUntil = now + CONFIRM_WINDOW_MILLIS;
                }
            }
            case UNSUPPORTED -> {
            }
        }
    }

    private static void uninstall(LocalEngine engine) {
        engine.stopForUninstall();
        // The engine needs a moment to let go of its files on Windows.
        Thread remover = new Thread(() -> {
            try {
                Thread.sleep(3_500);
                engine.installer().uninstall();
            } catch (IOException exception) {
                VoiceTranslate.LOGGER.warn("Could not remove the speech engine", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }, "Voice Translate engine removal");
        remover.setDaemon(true);
        remover.start();
    }

    private void updateEngineButton(Button button, LocalEngine engine) {
        EngineInstaller.State state = engine.installer().progress().state();
        boolean isRemoveArmed = System.currentTimeMillis() < this.removeArmedUntil;
        String key = switch (state) {
            case NOT_INSTALLED -> "voice-translate.engine.install";
            case FAILED -> "voice-translate.engine.retry";
            case INSTALLING -> "voice-translate.engine.cancel";
            case INSTALLED -> isRemoveArmed ? "voice-translate.engine.remove_confirm" : "voice-translate.engine.remove";
            case UNSUPPORTED -> "voice-translate.engine.unavailable";
        };
        button.setMessage(Component.translatable(key));
        button.active = state != EngineInstaller.State.UNSUPPORTED;
    }

    private static int downloadSize(LocalEngine engine) {
        PackageSet packages = engine.installer().packageSet();
        return (packages == null ? PackageSet.CUDA : packages).downloadMegabytes()
                + ("large-v3-turbo".equals(engine.resolveModel("auto")) ? 1_600 : 480);
    }
}
