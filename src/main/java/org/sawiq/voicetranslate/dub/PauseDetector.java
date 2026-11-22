package org.sawiq.voicetranslate.dub;

/**
 * Hears where the player's phrase ends while the voice mod is still sending.
 *
 * <p>Under voice activation both voice mods keep transmitting for a while
 * after the player stops, and a player who talks on without a break never
 * stops at all. Waiting for the stream to end makes every phrase late by
 * that much; a pause as long as the one that ends a sentence for other
 * players is enough to send what was said so far.</p>
 *
 * <p>A frame is quiet when it is far below the loudest part of the phrase,
 * or near silence outright: mic levels differ too much between players for
 * one fixed threshold.</p>
 */
final class PauseDetector {
    private static final double QUIET_BELOW_PEAK_DB = 20.0;
    private static final double SILENCE_DBFS = -55.0;
    private static final double NOTHING_HEARD = -120.0;

    private double peakDb = NOTHING_HEARD;
    private int quietFrames;

    /** Takes the next frame; true when it is quiet. */
    boolean isQuiet(short[] frame) {
        double level = levelDb(frame);
        boolean isQuiet = level < SILENCE_DBFS || level < this.peakDb - QUIET_BELOW_PEAK_DB;
        if (isQuiet) {
            this.quietFrames++;
        } else {
            this.quietFrames = 0;
            this.peakDb = Math.max(this.peakDb, level);
        }
        return isQuiet;
    }

    /** How many quiet frames in a row ended the stream so far. */
    int quietFrames() {
        return this.quietFrames;
    }

    /** The voice mod stopped sending: the next phrase is measured on its own. */
    void reset() {
        this.peakDb = NOTHING_HEARD;
        this.quietFrames = 0;
    }

    private static double levelDb(short[] frame) {
        double sum = 0;
        for (short sample : frame) {
            sum += (double) sample * sample;
        }
        double rms = Math.sqrt(sum / frame.length) / Short.MAX_VALUE;
        return rms <= 0 ? NOTHING_HEARD : 20.0 * Math.log10(rms);
    }
}
