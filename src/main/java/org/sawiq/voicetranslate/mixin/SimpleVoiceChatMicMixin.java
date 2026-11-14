package org.sawiq.voicetranslate.mixin;

import de.maxhenkel.voicechat.voice.client.MicThread;
import org.sawiq.voicetranslate.voice.VoiceBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Simple Voice Chat's microphone thread ends every 20 ms tick in
 * {@code sendAudio}, with the microphone frame while the player talks and
 * null while not. That one argument is all translating the player's own
 * speech needs: what it hands back is what gets sent, which includes the
 * translated phrase after the key is let go. Unchanged while the feature is off.
 */
@Mixin(MicThread.class)
public abstract class SimpleVoiceChatMicMixin {
    @ModifyVariable(method = "sendAudio", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private short[] voicetranslate$translateOwnSpeech(short[] rawAudio) {
        try {
            return VoiceBridge.processOwnMicTick(rawAudio);
        } catch (RuntimeException exception) {
            // The microphone must keep working whatever happens here.
            return rawAudio;
        }
    }
}
