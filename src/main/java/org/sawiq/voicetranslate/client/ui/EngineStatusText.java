package org.sawiq.voicetranslate.client.ui;

import net.minecraft.network.chat.Component;
import org.sawiq.voicetranslate.engine.EngineInstaller;
import org.sawiq.voicetranslate.engine.LocalEngine;

/** The local engine's state in words and in a colour. */
final class EngineStatusText {
    private EngineStatusText() {
    }

    static Component describe(LocalEngine engine) {
        LocalEngine.Status status = engine.status();
        return switch (status.phase()) {
            case UNSUPPORTED -> Component.translatable("voice-translate.engine.unsupported");
            case NOT_INSTALLED -> Component.translatable("voice-translate.engine.not_installed");
            case INSTALLING -> installing(engine.installer().progress());
            case STOPPED -> Component.translatable("voice-translate.engine.stopped");
            case STARTING -> Component.translatable("voice-translate.engine.starting");
            case DOWNLOADING_MODEL -> Component.translatable("voice-translate.engine.downloading", status.model(),
                    status.progress() >= 0 ? status.progress() + "%" : "...");
            case LOADING_MODEL -> Component.translatable("voice-translate.engine.loading", status.model());
            case READY -> Component.translatable("voice-translate.engine.ready", status.model(),
                    Component.translatable("cuda".equals(status.device())
                            ? "voice-translate.engine.device.gpu" : "voice-translate.engine.device.cpu"));
            case FAILED -> Component.translatable("voice-translate.engine.failed", status.detail());
        };
    }

    static int color(LocalEngine engine) {
        return switch (engine.status().phase()) {
            case READY -> UiPaint.GREEN;
            case FAILED, UNSUPPORTED -> UiPaint.RED;
            case INSTALLING, STARTING, DOWNLOADING_MODEL, LOADING_MODEL -> UiPaint.YELLOW;
            case NOT_INSTALLED, STOPPED -> UiPaint.TEXT;
        };
    }

    private static Component installing(EngineInstaller.Progress progress) {
        Component step = Component.translatable("voice-translate.engine.step."
                + (progress.step().isEmpty() ? "download_uv" : progress.step()));
        String amount = progress.percent() >= 0 ? progress.percent() + "%" : progress.detail();
        return Component.translatable("voice-translate.engine.installing",
                Math.max(1, progress.stepNumber()), EngineInstaller.STEPS.size(), step, amount);
    }
}
