package org.sawiq.voicetranslate.client.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.sawiq.voicetranslate.engine.EngineInstaller;
import org.sawiq.voicetranslate.engine.LocalEngine;

/** The local engine's state in words. */
final class EngineStatusText {
    private EngineStatusText() {
    }

    static Component describe(LocalEngine engine) {
        LocalEngine.Status status = engine.status();
        return switch (status.phase()) {
            case UNSUPPORTED -> Component.translatable("voice-translate.engine.unsupported").withStyle(ChatFormatting.RED);
            case NOT_INSTALLED -> Component.translatable("voice-translate.engine.not_installed");
            case INSTALLING -> installing(engine.installer().progress());
            case STOPPED -> Component.translatable("voice-translate.engine.stopped");
            case STARTING -> Component.translatable("voice-translate.engine.starting");
            case DOWNLOADING_MODEL -> Component.translatable("voice-translate.engine.downloading", status.model(),
                    status.progress() >= 0 ? status.progress() + "%" : "...");
            case LOADING_MODEL -> Component.translatable("voice-translate.engine.loading", status.model());
            case READY -> Component.translatable("voice-translate.engine.ready", status.model(),
                    Component.translatable("cuda".equals(status.device())
                            ? "voice-translate.engine.device.gpu" : "voice-translate.engine.device.cpu"))
                    .withStyle(ChatFormatting.GREEN);
            case FAILED -> Component.translatable("voice-translate.engine.failed", status.detail()).withStyle(ChatFormatting.RED);
        };
    }

    private static Component installing(EngineInstaller.Progress progress) {
        Component step = Component.translatable("voice-translate.engine.step." + (progress.step().isEmpty() ? "download_uv" : progress.step()));
        String amount = progress.percent() >= 0 ? progress.percent() + "%" : progress.detail();
        return Component.translatable("voice-translate.engine.installing",
                Math.max(1, progress.stepNumber()), EngineInstaller.STEPS.size(), step, amount);
    }
}
