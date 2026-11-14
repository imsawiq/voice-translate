package org.sawiq.voicetranslate.audio;

import java.util.Arrays;

/** A growable run of 16-bit samples with a hard ceiling, so a stuck stream cannot eat memory. */
final class SampleBuffer {
    private short[] samples;
    private int length;
    private final int capacityLimit;

    SampleBuffer(int initialCapacity, int capacityLimit) {
        this.samples = new short[initialCapacity];
        this.capacityLimit = capacityLimit;
    }

    /** Appends what fits under the ceiling and drops the rest. */
    void append(short[] chunk) {
        int room = this.capacityLimit - this.length;
        int count = Math.min(room, chunk.length);
        if (count <= 0) {
            return;
        }
        ensureCapacity(this.length + count);
        System.arraycopy(chunk, 0, this.samples, this.length, count);
        this.length += count;
    }

    int length() {
        return this.length;
    }

    short[] copy() {
        return Arrays.copyOf(this.samples, this.length);
    }

    short[] copyRange(int from, int to) {
        return Arrays.copyOfRange(this.samples, from, to);
    }

    /** Keeps only the samples from {@code from} on. */
    void dropBefore(int from) {
        int kept = this.length - from;
        System.arraycopy(this.samples, from, this.samples, 0, kept);
        this.length = kept;
    }

    void truncate(int newLength) {
        this.length = Math.max(0, Math.min(this.length, newLength));
    }

    void clear() {
        this.length = 0;
    }

    /**
     * Start of the quietest {@code windowLength}-sample stretch between
     * {@code from} and the end, the least harmful place to split speech.
     */
    int quietestWindowStart(int from, int windowLength) {
        int best = from;
        double bestEnergy = Double.MAX_VALUE;
        int step = Math.max(1, windowLength / 4);
        for (int start = from; start + windowLength <= this.length; start += step) {
            double energy = 0.0;
            for (int i = start; i < start + windowLength; i++) {
                energy += (double) this.samples[i] * this.samples[i];
            }
            if (energy < bestEnergy) {
                bestEnergy = energy;
                best = start;
            }
        }
        return best;
    }

    private void ensureCapacity(int needed) {
        if (needed > this.samples.length) {
            int grown = Math.min(this.capacityLimit, Math.max(needed, this.samples.length * 2));
            this.samples = Arrays.copyOf(this.samples, grown);
        }
    }
}
