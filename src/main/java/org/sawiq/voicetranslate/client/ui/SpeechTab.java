package org.sawiq.voicetranslate.client.ui;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.sawiq.voicetranslate.VoiceTranslate;
import org.sawiq.voicetranslate.client.VoiceTranslateClient;
import org.sawiq.voicetranslate.config.RecognitionService;
import org.sawiq.voicetranslate.config.RecognitionSettings;
import org.sawiq.voicetranslate.config.TranslateConfig;
import org.sawiq.voicetranslate.engine.LocalEngine;
import org.sawiq.voicetranslate.engine.ModelCache;
import org.sawiq.voicetranslate.engine.PackageSet;

/** Which service recognises speech, the local engine, and how speech is cut into sentences. */
final class SpeechTab implements SettingsTab {
    /** Deleting models takes a second click within this time. */
    private static final long CONFIRM_WINDOW_MILLIS = 4_000L;
    private static final int DOWNLOADED_COLOUR = 0x9CF29C;

    private final EnginePanel enginePanel;
    private long cleanUpArmedUntil;

    SpeechTab() {
        LocalEngine engine = VoiceTranslateClient.INSTANCE.engine();
        this.enginePanel = new EnginePanel(new EnginePanel.Engine(engine.installer(), engine::status, engine::stopForUninstall));
    }

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
        this.enginePanel.build(list);

        list.option(Component.translatable("voice-translate.settings.model"),
                Component.translatable("voice-translate.settings.model.desc"),
                Widgets.cycle(Component.translatable("voice-translate.settings.model"),
                        Arrays.asList(RecognitionSettings.LOCAL_MODELS), recognition.localModel,
                        model -> modelName(engine, model),
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
        if (engine.installer().canAddGpuLibraries()) {
            list.text(Component.translatable("voice-translate.engine.gpu_libraries"), UiPaint.DIM, 3);
            list.row(Button.builder(Component.translatable("voice-translate.engine.gpu_libraries.add"),
                            pressed -> {
                                engine.addGpuLibraries();
                                context.rebuild().run();
                            })
                    .bounds(0, 0, SettingsList.CONTROL_WIDTH, SettingsList.WIDGET_HEIGHT).build());
        }
        list.text(Component.translatable("voice-translate.engine.explain", downloadSize(engine)), UiPaint.DIM, 3);
        buildStorage(list, engine);
    }

    /**
     * A downloaded model in green; one that is not yet, with what it costs to
     * download, so nobody starts three gigabytes by cycling the button.
     */
    private static Component modelName(LocalEngine engine, String model) {
        if ("auto".equals(model)) {
            return Component.translatable("voice-translate.settings.model.auto", engine.resolveModel("auto"));
        }
        if (ModelCache.isDownloaded(model)) {
            return Component.literal(model).withColor(DOWNLOADED_COLOUR);
        }
        return Component.literal(model + " · ").append(Component.translatable("voice-translate.unit.gb",
                String.format(Locale.ROOT, "%.1f", ModelCache.downloadMegabytes(model) / 1024.0)));
    }

    /** Which models take space, and a way to remove the ones not in use. */
    private void buildStorage(SettingsList list, LocalEngine engine) {
        list.category(Component.translatable("voice-translate.category.models"));
        list.text(SpeechTab::storedModels, () -> UiPaint.TEXT, 2);
        Button cleanUp = Button.builder(Component.empty(), pressed -> onCleanUp(engine))
                .bounds(0, 0, SettingsList.CONTROL_WIDTH, SettingsList.WIDGET_HEIGHT).build();
        list.row(cleanUp);
        list.everyTick(() -> updateCleanUpButton(cleanUp, engine));
    }

    private static Component storedModels() {
        List<ModelCache.Stored> stored = ModelCache.stored();
        if (stored.isEmpty()) {
            return Component.translatable("voice-translate.models.none");
        }
        StringBuilder names = new StringBuilder();
        for (ModelCache.Stored model : stored) {
            if (!names.isEmpty()) {
                names.append(", ");
            }
            names.append(model.model()).append(" (")
                    .append(String.format(Locale.ROOT, "%.1f", model.megabytes() / 1024.0)).append(')');
        }
        return Component.translatable("voice-translate.models.stored", names.toString());
    }

    private void onCleanUp(LocalEngine engine) {
        long now = System.currentTimeMillis();
        if (now >= this.cleanUpArmedUntil) {
            this.cleanUpArmedUntil = now + CONFIRM_WINDOW_MILLIS;
            return;
        }
        this.cleanUpArmedUntil = 0L;
        Set<String> kept = keptModels(engine);
        Thread cleaner = new Thread(() -> {
            long freed = ModelCache.deleteAllExcept(kept);
            VoiceTranslate.LOGGER.info("Deleted unused speech models, {} MB freed", freed);
        }, "Voice Translate model clean-up");
        cleaner.setDaemon(true);
        cleaner.start();
    }

    private void updateCleanUpButton(Button button, LocalEngine engine) {
        long megabytes = ModelCache.megabytesExcept(keptModels(engine));
        boolean isArmed = System.currentTimeMillis() < this.cleanUpArmedUntil;
        String size = String.format(Locale.ROOT, "%.1f", megabytes / 1024.0);
        button.setMessage(megabytes == 0
                ? Component.translatable("voice-translate.models.clean_none")
                : Component.translatable(isArmed ? "voice-translate.models.clean_confirm" : "voice-translate.models.clean", size));
        if (button.visible) {
            button.active = megabytes > 0;
        }
    }

    /** The model wanted and the one the engine has loaded; both may be in use right now. */
    private static Set<String> keptModels(LocalEngine engine) {
        Set<String> kept = new HashSet<>();
        kept.add(engine.wantedModel());
        kept.add(engine.status().model());
        return kept;
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

    private static int downloadSize(LocalEngine engine) {
        PackageSet packages = engine.installer().packageSet();
        return (packages == null ? PackageSet.CPU : packages).downloadMegabytes()
                + ModelCache.downloadMegabytes(engine.wantedModel());
    }
}
