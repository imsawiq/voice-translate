package org.sawiq.voicetranslate.client.ui;

import java.util.Locale;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.sawiq.voicetranslate.client.VoiceTranslateClient;
import org.sawiq.voicetranslate.client.compat.ClientAccess;
import org.sawiq.voicetranslate.client.compat.CompatScreen;
import org.sawiq.voicetranslate.client.compat.GuiCanvas;
import org.sawiq.voicetranslate.config.DubbingSettings;
import org.sawiq.voicetranslate.config.TranslateConfig;
import org.sawiq.voicetranslate.engine.LocalEngine;

/**
 * The walk-through shown the first time the settings are opened: what the
 * mod does, then the three things to do, in the plainest words, with the
 * buttons for each step right there. It can be opened again from the
 * General tab.
 */
public final class GuideScreen extends CompatScreen {
    private enum Step {
        WELCOME, ENGINE, LANGUAGE, CHECK, VOICE;

        private String key() {
            return "voice-translate.guide." + name().toLowerCase(Locale.ROOT);
        }
    }

    private static final int STEP_Y = 41;
    private static final int FOOTER_BUTTON_WIDTH = 98;
    private static final int FOOTER_GAP = 6;

    private final Screen parent;
    private final EnginePanel enginePanel;
    private Step step = Step.WELCOME;
    private SettingsList list;

    public GuideScreen(Screen parent) {
        super(Component.translatable("voice-translate.guide.title"));
        this.parent = parent;
        LocalEngine engine = VoiceTranslateClient.INSTANCE.engine();
        this.enginePanel = new EnginePanel(new EnginePanel.Engine(engine.installer(), engine::status, engine::stopForUninstall), false);
    }

    @Override
    protected void init() {
        this.list = new SettingsList(this.width, this.font, this::addRenderableWidget);
        buildStep(VoiceTranslateClient.INSTANCE.config());
        this.list.finish(this.height, 0);

        boolean isFirst = this.step.ordinal() == 0;
        boolean isLast = this.step.ordinal() == Step.values().length - 1;
        int total = FOOTER_BUTTON_WIDTH * 3 + FOOTER_GAP * 2;
        int x = (this.width - total) / 2;
        int y = ScreenFrame.footerY(this.height);
        Button back = Button.builder(Component.translatable("voice-translate.guide.back"), pressed -> move(-1))
                .bounds(x, y, FOOTER_BUTTON_WIDTH, ScreenFrame.WIDGET_HEIGHT).build();
        back.active = !isFirst;
        addRenderableWidget(back);
        addRenderableWidget(Button.builder(Component.translatable("voice-translate.guide.skip"), pressed -> finish())
                .bounds(x + FOOTER_BUTTON_WIDTH + FOOTER_GAP, y, FOOTER_BUTTON_WIDTH, ScreenFrame.WIDGET_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable(isLast ? "voice-translate.guide.done" : "voice-translate.guide.next"),
                        pressed -> {
                            if (isLast) {
                                finish();
                            } else {
                                move(1);
                            }
                        })
                .bounds(x + 2 * (FOOTER_BUTTON_WIDTH + FOOTER_GAP), y, FOOTER_BUTTON_WIDTH, ScreenFrame.WIDGET_HEIGHT).build());
    }

    private void buildStep(TranslateConfig config) {
        String key = this.step.key();
        this.list.category(Component.translatable(key + ".heading"));
        switch (this.step) {
            case WELCOME -> {
                paragraph(key + ".what");
                paragraph(key + ".nobody_else");
                paragraph(key + ".once");
            }
            case ENGINE -> {
                // The button right under the first sentence, so it is on screen in a small window too.
                paragraph(key + ".why");
                this.enginePanel.build(this.list);
                this.list.gap(4);
                paragraph(key + ".press");
                hint(key + ".any_computer");
            }
            case LANGUAGE -> {
                paragraph(key + ".usually");
                this.list.option(Component.translatable("voice-translate.settings.target_language"),
                        Widgets.cycle(Component.translatable("voice-translate.settings.target_language"),
                                LanguageNames.codesWithAuto(), config.translation.targetLanguage,
                                code -> LanguageNames.name(code, "voice-translate.settings.language.game"),
                                code -> {
                                    config.translation.targetLanguage = code;
                                    apply();
                                }));
                paragraph(key + ".theirs");
            }
            case CHECK -> {
                paragraph(key + ".join");
                paragraph(key + ".status");
                GeneralTab.addStatus(this.list);
                hint(key + ".later");
            }
            case VOICE -> buildVoiceStep(config.dubbing, key);
        }
    }

    private void buildVoiceStep(DubbingSettings dubbing, String key) {
        paragraph(key + ".optional");
        this.list.option(Component.translatable("voice-translate.settings.dub_others"),
                Widgets.toggle(Component.translatable("voice-translate.settings.dub_others"), dubbing.dubOthers, value -> {
                    dubbing.dubOthers = value;
                    apply();
                }));
        hint(key + ".dub_others");
        this.list.option(Component.translatable("voice-translate.settings.my_speech"),
                Widgets.toggle(Component.translatable("voice-translate.settings.my_speech"), dubbing.translateMySpeech, value -> {
                    dubbing.translateMySpeech = value;
                    apply();
                }));
        hint(key + ".my_speech");
        hint(key + ".more");
    }

    private void paragraph(String key) {
        this.list.text(Component.translatable(key), UiPaint.TEXT);
        this.list.gap(4);
    }

    private void hint(String key) {
        this.list.text(Component.translatable(key), UiPaint.DIM);
        this.list.gap(4);
    }

    private static void apply() {
        VoiceTranslateClient.INSTANCE.applySettings();
    }

    private void move(int by) {
        this.step = Step.values()[Math.clamp(this.step.ordinal() + by, 0, Step.values().length - 1)];
        rebuildWidgets();
    }

    /** Remembers the guide was seen, so it does not come back on its own, and goes on to the settings. */
    private void finish() {
        TranslateConfig config = VoiceTranslateClient.INSTANCE.config();
        config.hasSeenGuide = true;
        apply();
        ClientAccess.openScreen(new SettingsScreen(this.parent));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.list != null) {
            this.list.tick();
        }
    }

    @Override
    protected void paintBehindWidgets(GuiCanvas canvas) {
        ScreenFrame.paintBackground(new UiPaint(canvas), this.width, this.height);
    }

    @Override
    protected void paintAboveWidgets(GuiCanvas canvas, int mouseX, int mouseY) {
        UiPaint paint = new UiPaint(canvas);
        ScreenFrame.paintChrome(paint, this.width, this.height, this.title);
        paint.text(Component.translatable("voice-translate.guide.step", this.step.ordinal() + 1, Step.values().length),
                ScreenFrame.MARGIN, STEP_Y, UiPaint.GREY);
        this.list.paint(paint);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (this.list.scrollBy(-(int) Math.round(vertical * SettingsList.ROW_HEIGHT * 2))) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    /** Escape closes the guide like Skip does. */
    @Override
    public void onClose() {
        finish();
    }
}
