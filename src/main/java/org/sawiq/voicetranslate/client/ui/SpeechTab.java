package org.sawiq.voicetranslate.client.ui;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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

/** Which service recognises speech, the local engine, and how speech is cut into sentences. */
final class SpeechTab implements SettingsTab {
    /** Removing deletes gigabytes of downloads, so it takes a second click within this time. */
    private static final long CONFIRM_WINDOW_MILLIS = 4_000L;
    private static final int BAR_HEIGHT = 4;

    private long removeArmedUntil;

    @Override
    public void build(SettingsList list, Context context) {
        RecognitionSettings recognition = context.config().recognition;
        list.category(Component.translatable("voice-translate.category.recognition"));
        list.option(Component.translatable("voice-translate.settings.recognition"),
                Component.translatable("voice-translate.settings.recognition.desc"),
                Widgets.cycle(Component.translatable("voice-translate.settings.recognition"),
                        List.of(RecognitionService.values()), recognition.service, SpeechTab::serviceName,
                        service -> {
                            recognition.service = service;
                            context.apply().run();
                            context.rebuild().run();
                        }));

        switch (recognition.service) {
            case LOCAL -> buildLocal(list, context);
            case GROQ, OPENAI -> buildCloud(list, context);
            case CUSTOM -> buildCustom(list, context);
        }

        list.category(Component.translatable("voice-translate.category.sentences"));
        list.option(Component.translatable("voice-translate.settings.pause"),
                Component.translatable("voice-translate.settings.pause.desc"),
                Widgets.slider(300, 2_000, 50, recognition.pauseMillis,
                        value -> Component.translatable("voice-translate.unit.ms", value),
                        value -> recognition.pauseMillis = value));
        list.option(Component.translatable("voice-translate.settings.sensitivity"),
                Component.translatable("voice-translate.settings.sensitivity.desc"),
                Widgets.slider(3, 24, 1, recognition.sensitivityDb,
                        value -> Component.translatable("voice-translate.unit.db", value),
                        value -> recognition.sensitivityDb = value));
    }

    static Component serviceName(RecognitionService service) {
        return Component.translatable("voice-translate.recognition." + service.name().toLowerCase(Locale.ROOT));
    }

    private void buildLocal(SettingsList list, Context context) {
        RecognitionSettings recognition = context.config().recognition;
        LocalEngine engine = VoiceTranslateClient.INSTANCE.engine();

        list.category(Component.translatable("voice-translate.category.engine"));
        list.text(() -> EngineStatusText.describe(engine), () -> EngineStatusText.color(engine), 1);
        list.custom(BAR_HEIGHT + 6, (paint, x, y, width) -> paintProgress(paint, engine, x, y + 1, width));
        Button action = Button.builder(Component.empty(), pressed -> onEngineButton(engine))
                .bounds(0, 0, SettingsList.CONTROL_WIDTH, SettingsList.WIDGET_HEIGHT).build();
        list.row(action);
        list.everyTick(() -> updateEngineButton(action, engine));

        list.option(Component.translatable("voice-translate.settings.model"),
                Component.translatable("voice-translate.settings.model.desc"),
                Widgets.cycle(Component.translatable("voice-translate.settings.model"),
                        Arrays.asList(RecognitionSettings.LOCAL_MODELS), recognition.localModel,
                        model -> "auto".equals(model)
                                ? Component.translatable("voice-translate.settings.model.auto", engine.resolveModel("auto"))
                                : Component.literal(model),
                        model -> {
                            recognition.localModel = model;
                            context.apply().run();
                        }));
        list.option(Component.translatable("voice-translate.settings.device"),
                Component.translatable("voice-translate.settings.device.desc"),
                Widgets.cycle(Component.translatable("voice-translate.settings.device"),
                        Arrays.asList(RecognitionSettings.LOCAL_DEVICES), recognition.localDevice,
                        device -> Component.translatable("voice-translate.settings.device." + device),
                        device -> {
                            recognition.localDevice = device;
                            context.apply().run();
                        }));
        list.text(Component.translatable("voice-translate.engine.explain", downloadSize(engine)), UiPaint.DIM, 3);
    }

    private void buildCloud(SettingsList list, Context context) {
        TranslateConfig config = context.config();
        RecognitionService service = config.recognition.service;
        list.category(serviceName(service));
        list.option(Component.translatable("voice-translate.settings.api_key"),
                Widgets.secret(context.font(), Component.translatable("voice-translate.settings.api_key"),
                        config.keys.forRecognition(service), key -> config.keys.setForRecognition(service, key.strip())));
        list.option(Component.translatable("voice-translate.settings.model"),
                Widgets.text(context.font(), Component.translatable("voice-translate.settings.model"),
                        config.recognition.modelFor(service), model -> {
                            if (service == RecognitionService.GROQ) {
                                config.recognition.groqModel = model.strip();
                            } else {
                                config.recognition.openAiModel = model.strip();
                            }
                        }));
        list.text(Component.translatable("voice-translate.privacy.cloud_speech", serviceName(service)), UiPaint.YELLOW, 2);
        if (service == RecognitionService.GROQ) {
            list.text(Component.translatable("voice-translate.hint.groq_key"), UiPaint.DIM, 2);
        }
    }

    private void buildCustom(SettingsList list, Context context) {
        TranslateConfig config = context.config();
        list.category(serviceName(RecognitionService.CUSTOM));
        list.option(Component.translatable("voice-translate.settings.address"),
                Widgets.text(context.font(), Component.translatable("voice-translate.settings.address"),
                        config.recognition.customUrl, url -> config.recognition.customUrl = url.strip()));
        list.option(Component.translatable("voice-translate.settings.model"),
                Widgets.text(context.font(), Component.translatable("voice-translate.settings.model"),
                        config.recognition.customModel, model -> config.recognition.customModel = model.strip()));
        list.option(Component.translatable("voice-translate.settings.api_key_optional"),
                Widgets.secret(context.font(), Component.translatable("voice-translate.settings.api_key"),
                        config.keys.custom, key -> config.keys.custom = key.strip()));
        list.text(Component.translatable("voice-translate.hint.custom_server"), UiPaint.DIM, 2);
    }

    /** A real bar while a size is known, a sweep while something runs for an unknown time, nothing at rest. */
    private static void paintProgress(UiPaint paint, LocalEngine engine, int x, int y, int width) {
        LocalEngine.Status status = engine.status();
        switch (status.phase()) {
            case INSTALLING, DOWNLOADING_MODEL -> {
                if (status.progress() >= 0) {
                    paint.bar(x, y, width, BAR_HEIGHT, status.progress() / 100.0, UiPaint.BLUE);
                } else {
                    paint.sweep(x, y, width, BAR_HEIGHT, System.currentTimeMillis(), UiPaint.BLUE);
                }
            }
            case STARTING, LOADING_MODEL -> paint.sweep(x, y, width, BAR_HEIGHT, System.currentTimeMillis(), UiPaint.YELLOW);
            case READY -> paint.bar(x, y, width, BAR_HEIGHT, 1.0, UiPaint.GREEN);
            default -> paint.bar(x, y, width, BAR_HEIGHT, 0.0, UiPaint.GREY);
        }
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
        if (button.visible) {
            button.active = state != EngineInstaller.State.UNSUPPORTED;
        }
    }

    private static int downloadSize(LocalEngine engine) {
        PackageSet packages = engine.installer().packageSet();
        return (packages == null ? PackageSet.CUDA : packages).downloadMegabytes()
                + ("large-v3-turbo".equals(engine.resolveModel("auto")) ? 1_600 : 480);
    }
}
