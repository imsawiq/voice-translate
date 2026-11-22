package org.sawiq.voicetranslate.dub;

/**
 * A few seconds of one player's speech, which the voice engine copies the
 * voice from.
 *
 * @param key     names this sample; the engine prepares a voice once per key and keeps it
 * @param samples 16 kHz mono PCM
 */
public record VoiceSample(String key, short[] samples) {
}
