package org.sawiq.voicetranslate.client.ui;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.sawiq.voicetranslate.client.VoiceTranslateClient;
import org.sawiq.voicetranslate.client.compat.ClientAccess;

/**
 * The mod's settings, on four tabs. Choices take effect at once; text
 * fields such as keys are applied when the screen closes, so a half-typed
 * key is not sent to a service with every letter.
 */
public final class SettingsScreen extends Screen {
    private static final int TAB_WIDTH = 76;
    private static final int TAB_GAP = 4;
    private static final int TABS_TOP = 26;
    private static final int CONTENT_TOP = 54;
    private static final int FOOTER_BUTTON_WIDTH = 100;

    /** Reopening the screen returns to the tab last used. */
    private static int lastTab;

    private final Screen parent;
    private final List<SettingsTab> tabs = List.of(new GeneralTab(), new SpeechTab(), new TranslationTab(), new DisplayTab());
    private FormLayout form;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("voice-translate.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int tabsLeft = this.width / 2 - (this.tabs.size() * TAB_WIDTH + (this.tabs.size() - 1) * TAB_GAP) / 2;
        for (int i = 0; i < this.tabs.size(); i++) {
            int index = i;
            Button tab = Button.builder(Component.translatable(this.tabs.get(i).titleKey()), pressed -> selectTab(index))
                    .bounds(tabsLeft + i * (TAB_WIDTH + TAB_GAP), TABS_TOP, TAB_WIDTH, 20)
                    .build();
            tab.active = i != lastTab;
            addRenderableWidget(tab);
        }

        this.form = new FormLayout(this::addRenderableWidget, this.font, this.width / 2, CONTENT_TOP);
        SettingsTab.Context context = new SettingsTab.Context(VoiceTranslateClient.INSTANCE.config(), this.font,
                this::rebuildWidgets, VoiceTranslateClient.INSTANCE::applySettings);
        this.tabs.get(lastTab).build(this.form, context);

        int footerY = this.height - 28;
        int footerLeft = this.width / 2 - (FOOTER_BUTTON_WIDTH * 3 + TAB_GAP * 2) / 2;
        addRenderableWidget(Button.builder(Component.translatable("voice-translate.history.open"),
                        pressed -> ClientAccess.openScreen(new HistoryScreen(this)))
                .bounds(footerLeft, footerY, FOOTER_BUTTON_WIDTH, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("voice-translate.players.open"),
                        pressed -> ClientAccess.openScreen(new PlayersScreen(this)))
                .bounds(footerLeft + FOOTER_BUTTON_WIDTH + TAB_GAP, footerY, FOOTER_BUTTON_WIDTH, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), pressed -> onClose())
                .bounds(footerLeft + (FOOTER_BUTTON_WIDTH + TAB_GAP) * 2, footerY, FOOTER_BUTTON_WIDTH, 20).build());
    }

    private void selectTab(int index) {
        VoiceTranslateClient.INSTANCE.applySettings();
        lastTab = index;
        rebuildWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.form != null) {
            this.form.tick();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);
        if (this.form != null) {
            this.form.draw(graphics);
        }
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
