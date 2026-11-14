package org.sawiq.voicetranslate.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.sawiq.voicetranslate.client.VoiceTranslateClient;
import org.sawiq.voicetranslate.client.compat.ClientAccess;

/**
 * Voice Translate's window: six pages under one set of tabs, in the same
 * frame as the voice changer studio and Plasmo Voice's settings.
 *
 * <p>Choices take effect at once. Text fields such as keys are applied when
 * the page changes or the window closes, so a half-typed key is not sent to a
 * service with every letter.</p>
 */
public final class SettingsScreen extends Screen {
    /** The pages, in tab order. */
    public enum Page {
        GENERAL(GeneralTab::new),
        SPEECH(SpeechTab::new),
        TRANSLATION(TranslationTab::new),
        DISPLAY(DisplayTab::new),
        VOICE(VoiceTab::new),
        HISTORY(HistoryTab::new),
        PLAYERS(PlayersTab::new);

        private final Supplier<SettingsTab> factory;

        Page(Supplier<SettingsTab> factory) {
            this.factory = factory;
        }

        private String key() {
            return "voice-translate.tab." + name().toLowerCase(Locale.ROOT);
        }
    }

    private static final int KEY_UP = 265;
    private static final int KEY_DOWN = 264;
    private static final int KEY_PAGE_UP = 266;
    private static final int KEY_PAGE_DOWN = 267;
    private static final int FOOTER_BUTTON_WIDTH = 150;

    /** Reopening the window returns to the page last used. */
    private static Page lastPage = Page.GENERAL;

    private final Screen parent;
    private Page page;
    private SettingsTab tab;
    private SettingsList list;
    private int keptScroll;

    public SettingsScreen(Screen parent) {
        this(parent, lastPage);
    }

    public SettingsScreen(Screen parent, Page page) {
        super(Component.translatable("voice-translate.settings.title"));
        this.parent = parent;
        this.page = page;
    }

    @Override
    protected void init() {
        lastPage = this.page;
        List<ScreenFrame.Tab> tabs = new ArrayList<>();
        for (Page each : Page.values()) {
            tabs.add(new ScreenFrame.Tab(Component.translatable(each.key()), Component.translatable(each.key() + ".desc"),
                    each == this.page, () -> open(each)));
        }
        ScreenFrame.addTabs(tabs, this.width, this.font::width, this::addRenderableWidget);

        if (this.tab == null) {
            this.tab = this.page.factory.get();
        }
        this.list = new SettingsList(this.width, this::addRenderableWidget);
        SettingsTab.Context context = new SettingsTab.Context(VoiceTranslateClient.INSTANCE.config(), this.font,
                this::rebuildKeepingScroll, VoiceTranslateClient.INSTANCE::applySettings);
        this.tab.build(this.list, context);
        this.list.finish(this.height, this.keptScroll);
        this.keptScroll = 0;

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), pressed -> onClose())
                .bounds(this.width / 2 - FOOTER_BUTTON_WIDTH / 2, ScreenFrame.footerY(this.height), FOOTER_BUTTON_WIDTH,
                        ScreenFrame.WIDGET_HEIGHT)
                .build());
    }

    private void open(Page next) {
        VoiceTranslateClient.INSTANCE.applySettings();
        this.page = next;
        this.tab = null;
        rebuildWidgets();
    }

    /** A choice that shows or hides rows lays the page out again without jumping back to its top. */
    private void rebuildKeepingScroll() {
        this.keptScroll = this.list.scrollOffset();
        rebuildWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.list != null) {
            this.list.tick();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        UiPaint paint = new UiPaint(graphics, this.font);
        ScreenFrame.paintBackground(paint, this.width, this.height);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        ScreenFrame.paintChrome(paint, this.width, this.height, this.title);
        this.list.paint(paint);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (this.list.scrollBy(-(int) Math.round(vertical * SettingsList.ROW_HEIGHT * 2))) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (getFocused() instanceof EditBox) {
            return super.keyPressed(event);
        }
        int step = switch (event.key()) {
            case KEY_UP -> -SettingsList.ROW_HEIGHT;
            case KEY_DOWN -> SettingsList.ROW_HEIGHT;
            case KEY_PAGE_UP -> -this.list.viewportHeight();
            case KEY_PAGE_DOWN -> this.list.viewportHeight();
            default -> 0;
        };
        return (step != 0 && this.list.scrollBy(step)) || super.keyPressed(event);
    }

    @Override
    public void removed() {
        VoiceTranslateClient.INSTANCE.applySettings();
        super.removed();
    }

    @Override
    public void onClose() {
        ClientAccess.openScreen(this.parent);
    }
}
