package org.sawiq.voicetranslate.mixin;

import org.sawiq.voicetranslate.voice.plasmo.PlasmoVoiceAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import su.plo.voice.api.client.audio.capture.ClientActivation;
import su.plo.voice.client.audio.capture.VoiceClientActivation;

/**
 * Lets translating the player's own speech decide when Plasmo Voice sends,
 * see {@code PlasmoOwnSpeechCapture}. Untouched while that is off.
 */
@Mixin(VoiceClientActivation.class)
public abstract class PlasmoActivationMixin {
    /** The parent activation is asked first in every tick, and it alone without a parent result. */
    @Inject(method = "process", at = @At("HEAD"), require = 0)
    private void voicetranslate$beginTick(short[] samples, ClientActivation.Result parentResult,
                                          CallbackInfoReturnable<ClientActivation.Result> callback) {
        if (parentResult == null && samples != null) {
            PlasmoVoiceAddon.beginCaptureTick(samples);
        }
    }

    @Inject(method = "process", at = @At("RETURN"), cancellable = true, require = 0)
    private void voicetranslate$decide(short[] samples, ClientActivation.Result parentResult,
                                       CallbackInfoReturnable<ClientActivation.Result> callback) {
        ClientActivation activation = (ClientActivation) (Object) this;
        callback.setReturnValue(PlasmoVoiceAddon.decide(activation, callback.getReturnValue()));
    }
}
