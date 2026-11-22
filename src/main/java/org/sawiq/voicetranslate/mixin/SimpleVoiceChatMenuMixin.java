package org.sawiq.voicetranslate.mixin;

import de.maxhenkel.voicechat.gui.VoiceChatScreen;
import de.maxhenkel.voicechat.gui.VoiceChatScreenBase;
import de.maxhenkel.voicechat.gui.widgets.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.sawiq.voicetranslate.VoiceTranslate;
import org.sawiq.voicetranslate.client.compat.ClientAccess;
import org.sawiq.voicetranslate.client.ui.SettingsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a Voice Translate button to Simple Voice Chat's menu, next to its own
 * mute, deafen and settings icons in the bottom row. The voice changer's
 * button takes the gap between Settings and Group, so the two never meet.
 *
 * <p>The position is read off Simple Voice Chat's own buttons rather than
 * hard-coded, so a later release that moves them carries this one along.</p>
 */
@Mixin(VoiceChatScreen.class)
public abstract class SimpleVoiceChatMenuMixin extends Screen {
    @Unique
    private static final Identifier VOICETRANSLATE$ICON = Identifier.fromNamespaceAndPath(VoiceTranslate.MOD_ID, "icons/translate");
    @Unique
    private static final int VOICETRANSLATE$SPACING = 22;

    protected SimpleVoiceChatMenuMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void voicetranslate$addButton(CallbackInfo callbackInfo) {
        try {
            ImageButton anchor = voicetranslate$lastLeftIcon();
            if (anchor == null) {
                return;
            }
            ImageButton button = new ImageButton(anchor.getX() + VOICETRANSLATE$SPACING, anchor.getY(), VOICETRANSLATE$ICON,
                    pressed -> ClientAccess.openScreen(SettingsScreen.open((Screen) (Object) this)));
            button.setTooltip(Tooltip.create(Component.translatable("voice-translate.menu.open")));
            addRenderableWidget(button);
        } catch (RuntimeException exception) {
            // The voice chat menu must keep working whatever happens here.
            VoiceTranslate.LOGGER.error("Could not add the Voice Translate button to Simple Voice Chat's menu", exception);
        }
    }

    /** The rightmost icon of the group in the bottom left corner: mute, deafen, settings. */
    @Unique
    private ImageButton voicetranslate$lastLeftIcon() {
        int middle = ((VoiceChatScreenBase) (Object) this).getGuiLeft() + 97;
        ImageButton anchor = null;
        for (GuiEventListener child : children()) {
            if (!(child instanceof ImageButton icon) || icon.getX() >= middle) {
                continue;
            }
            boolean isLower = anchor == null || icon.getY() > anchor.getY();
            boolean isFurtherRight = anchor != null && icon.getY() == anchor.getY() && icon.getX() > anchor.getX();
            if (isLower || isFurtherRight) {
                anchor = icon;
            }
        }
        return anchor;
    }
}
