package org.sawiq.voicetranslate.client.ui;

import net.minecraft.network.chat.Component;
import org.sawiq.voicetranslate.engine.EngineComponent;
import org.sawiq.voicetranslate.engine.EngineInstaller;
import org.sawiq.voicetranslate.engine.LocalEngine;

/** An engine's state in words and in a colour. */
public final class EngineStatusText {
    private EngineStatusText() {
    }

    /**
     * The translation key for one of an engine's texts. Each engine has its own
     * set, so the voice tab never offers to install "the speech engine".
     */
    public static String key(EngineComponent component, String name) {
        return switch (component) {
            case SPEECH -> "voice-translate.engine." + name;
            case VOICE -> "voice-translate.voice_engine." + name;
        };
    }

    static Component describe(LocalEngine engine) {
        return describe(engine.status(), engine.installer());
    }

    static Component describe(LocalEngine.Status status, EngineInstaller installer) {
        EngineComponent component = installer.component();
        return switch (status.phase()) {
            case UNSUPPORTED -> Component.translatable(key(component, "unsupported"));
            case NOT_INSTALLED -> Component.translatable(key(component, "not_installed"));
            case INSTALLING -> installing(component, installer.progress());
            case STOPPED -> Component.translatable(key(component, "stopped"));
            case STARTING -> Component.translatable(key(component, "starting"));
            case DOWNLOADING_MODEL -> Component.translatable(key(component, "downloading"), status.model(),
                    status.progress() >= 0 ? status.progress() + "%" : "...");
            case LOADING_MODEL -> Component.translatable(key(component, "loading"), status.model());
            case READY -> Component.translatable(key(component, "ready"), status.model(),
                    Component.translatable("cuda".equals(status.device())
                            ? "voice-translate.engine.device.gpu" : "voice-translate.engine.device.cpu"));
            case FAILED -> Component.translatable(key(component, "failed"), status.detail());
        };
    }

    static int color(LocalEngine engine) {
        return color(engine.status());
    }

    static int color(LocalEngine.Status status) {
        return switch (status.phase()) {
            case READY -> UiPaint.GREEN;
            case FAILED, UNSUPPORTED -> UiPaint.RED;
            case INSTALLING, STARTING, DOWNLOADING_MODEL, LOADING_MODEL -> UiPaint.YELLOW;
            case NOT_INSTALLED, STOPPED -> UiPaint.TEXT;
        };
    }

    private static Component installing(EngineComponent component, EngineInstaller.Progress progress) {
        Component step = Component.translatable(key(component,
                "step." + (progress.step().isEmpty() ? "download_uv" : progress.step())));
        String amount = progress.percent() >= 0 ? progress.percent() + "%" : progress.detail();
        return Component.translatable("voice-translate.engine.installing",
                Math.max(1, progress.stepNumber()), EngineInstaller.STEPS.size(), step, amount);
    }
}
