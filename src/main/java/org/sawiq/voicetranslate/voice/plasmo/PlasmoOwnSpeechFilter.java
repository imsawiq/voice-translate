package org.sawiq.voicetranslate.voice.plasmo;

import su.plo.voice.api.client.audio.filter.AudioFilter;
import su.plo.voice.api.client.audio.filter.AudioFilterContext;

/**
 * The microphone filter that swaps a translated frame in for what the player
 * said, on the ticks {@link PlasmoOwnSpeechCapture} decided to send.
 */
final class PlasmoOwnSpeechFilter implements AudioFilter {
    private final PlasmoOwnSpeechCapture capture;

    PlasmoOwnSpeechFilter(PlasmoOwnSpeechCapture capture) {
        this.capture = capture;
    }

    @Override
    public String getName() {
        return "voice-translate";
    }

    @Override
    public short[] process(AudioFilterContext context, short[] samples) {
        this.capture.takeOutput(samples, context.getChannels());
        return samples;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
