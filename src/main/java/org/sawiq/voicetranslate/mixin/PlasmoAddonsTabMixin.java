package org.sawiq.voicetranslate.mixin;

import org.sawiq.voicetranslate.VoiceTranslate;
import org.sawiq.voicetranslate.client.compat.ClientAccess;
import org.sawiq.voicetranslate.client.ui.SettingsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.plo.lib.mod.client.gui.components.Button;
import su.plo.slib.api.chat.component.McTextComponent;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.client.config.VoiceClientConfig;
import su.plo.voice.client.config.addon.VoiceAddonConfig;
import su.plo.voice.client.gui.settings.VoiceSettingsScreen;
import su.plo.voice.client.gui.settings.tab.AddonsTabWidget;
import su.plo.voice.client.gui.settings.tab.TabWidget;

/**
 * Puts a button that opens Voice Translate's own window under its settings
 * in Plasmo Voice's Addons tab. The toggles above it come through Plasmo
 * Voice's addon config API; only the button needs a mixin, because that API
 * has no buttons.
 */
@Mixin(AddonsTabWidget.class)
public abstract class PlasmoAddonsTabMixin extends TabWidget {
    protected PlasmoAddonsTabMixin(VoiceSettingsScreen parent, PlasmoVoiceClient voiceClient, VoiceClientConfig config) {
        super(parent, voiceClient, config);
    }

    @Inject(method = "createAddonEntry", at = @At("TAIL"), require = 0)
    private void voicetranslate$addOpenButton(VoiceAddonConfig addonConfig, CallbackInfo callbackInfo) {
        try {
            if (!VoiceTranslate.MOD_ID.equals(addonConfig.getAddon().getId())) {
                return;
            }
            addEntry(new FullWidthEntry<>(new Button(0, 0, 220, 20,
                    McTextComponent.translatable("voice-translate.menu.open_full"),
                    pressed -> ClientAccess.openScreen(new SettingsScreen(ClientAccess.currentScreen())),
                    Button.NO_TOOLTIP)));
        } catch (RuntimeException exception) {
            VoiceTranslate.LOGGER.error("Could not add the Voice Translate button to Plasmo Voice's settings", exception);
        }
    }
}
