package org.sawiq.voicetranslate.config;

/** How subtitles are shown. Mutable for Gson and the settings screen. */
public final class DisplaySettings {
    public enum PanelMode {
        /** Everyone, including players whose bubble is on screen. */
        EVERYONE,
        /** Only players without a visible bubble: behind you, far away, in a group call. */
        OFF_SCREEN,
        HIDDEN
    }

    public enum PanelPosition {
        BOTTOM,
        TOP
    }

    /** Subtitles floating above the speaker's head. */
    public boolean bubbles = true;
    public PanelMode panelMode = PanelMode.OFF_SCREEN;
    public PanelPosition panelPosition = PanelPosition.BOTTOM;
    /** The recognised original under the translation, smaller and dimmer. */
    public boolean showOriginal = true;
    /** Sentences still being spoken, shown as they are recognised. */
    public boolean showDrafts = true;
    /** Also write finished translations into the chat log. */
    public boolean chatLog = false;
    /** Percent, 50..200. */
    public int textScale = 100;
    /** Percent, 0..100. */
    public int backgroundOpacity = 55;
    /** Beyond this, a speaker's subtitle goes to the panel instead of a bubble. */
    public int bubbleDistance = 32;

    void sanitize() {
        if (this.panelMode == null) {
            this.panelMode = PanelMode.OFF_SCREEN;
        }
        if (this.panelPosition == null) {
            this.panelPosition = PanelPosition.BOTTOM;
        }
        this.textScale = Math.clamp(this.textScale, 50, 200);
        this.backgroundOpacity = Math.clamp(this.backgroundOpacity, 0, 100);
        this.bubbleDistance = Math.clamp(this.bubbleDistance, 4, 128);
    }
}
