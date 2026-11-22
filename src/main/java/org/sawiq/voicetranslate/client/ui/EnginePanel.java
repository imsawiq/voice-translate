package org.sawiq.voicetranslate.client.ui;

import java.io.IOException;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.sawiq.voicetranslate.VoiceTranslate;
import org.sawiq.voicetranslate.engine.EngineInstaller;
import org.sawiq.voicetranslate.engine.LocalEngine;

/**
 * An engine's status line, progress bar and install / cancel / remove
 * button, the same for speech recognition and for the voice.
 */
final class EnginePanel {
    /** Removing deletes gigabytes of downloads, so it takes a second click within this time. */
    private static final long CONFIRM_WINDOW_MILLIS = 4_000L;
    private static final int BAR_HEIGHT = 4;

    /** What the panel controls. */
    record Engine(EngineInstaller installer, Supplier<LocalEngine.Status> status, Runnable stopForUninstall) {
    }

    private final Engine engine;
    /** False in the guide: someone following it step by step should not find a way to delete it there. */
    private final boolean canRemove;
    private long removeArmedUntil;

    EnginePanel(Engine engine) {
        this(engine, true);
    }

    EnginePanel(Engine engine, boolean canRemove) {
        this.engine = engine;
        this.canRemove = canRemove;
    }

    void build(SettingsList list) {
        list.text(() -> EngineStatusText.describe(this.engine.status().get(), this.engine.installer()),
                () -> EngineStatusText.color(this.engine.status().get()), 1);
        list.custom(BAR_HEIGHT + 6, (paint, x, y, width) -> paintProgress(paint, x, y + 1, width));
        Button action = Button.builder(Component.empty(), pressed -> onButton())
                .bounds(0, 0, SettingsList.CONTROL_WIDTH, SettingsList.WIDGET_HEIGHT).build();
        list.row(action);
        list.everyTick(() -> updateButton(action));
    }

    /** A real bar while a size is known, a sweep while something runs for an unknown time, nothing at rest. */
    private void paintProgress(UiPaint paint, int x, int y, int width) {
        LocalEngine.Status status = this.engine.status().get();
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

    private void onButton() {
        EngineInstaller installer = this.engine.installer();
        switch (installer.progress().state()) {
            case NOT_INSTALLED, FAILED -> installer.install();
            case INSTALLING -> installer.cancel();
            case INSTALLED -> {
                if (!this.canRemove) {
                    return;
                }
                long now = System.currentTimeMillis();
                if (now < this.removeArmedUntil) {
                    this.removeArmedUntil = 0L;
                    uninstall();
                } else {
                    this.removeArmedUntil = now + CONFIRM_WINDOW_MILLIS;
                }
            }
            case UNSUPPORTED -> {
            }
        }
    }

    private void uninstall() {
        this.engine.stopForUninstall().run();
        // The engine needs a moment to let go of its files on Windows.
        Thread remover = new Thread(() -> {
            try {
                Thread.sleep(3_500);
                this.engine.installer().uninstall();
            } catch (IOException exception) {
                VoiceTranslate.LOGGER.warn("Could not remove the engine", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }, "Voice Translate engine removal");
        remover.setDaemon(true);
        remover.start();
    }

    private void updateButton(Button button) {
        EngineInstaller.State state = this.engine.installer().progress().state();
        boolean isRemoveArmed = System.currentTimeMillis() < this.removeArmedUntil;
        String name = switch (state) {
            case NOT_INSTALLED -> "install";
            case FAILED -> "retry";
            case INSTALLING -> "cancel";
            case INSTALLED -> !this.canRemove ? "installed" : isRemoveArmed ? "remove_confirm" : "remove";
            case UNSUPPORTED -> "unavailable";
        };
        button.setMessage(Component.translatable(EngineStatusText.key(this.engine.installer().component(), name)));
        if (button.visible) {
            button.active = state != EngineInstaller.State.UNSUPPORTED
                    && (this.canRemove || state != EngineInstaller.State.INSTALLED);
        }
    }
}
