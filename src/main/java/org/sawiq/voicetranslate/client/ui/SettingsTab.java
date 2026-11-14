package org.sawiq.voicetranslate.client.ui;

import net.minecraft.client.gui.Font;
import org.sawiq.voicetranslate.config.TranslateConfig;

/** One page of the settings screen. */
interface SettingsTab {
    void build(SettingsList list, Context context);

    /**
     * What a page may ask of the screen.
     *
     * @param rebuild lays the page out again, for a choice that shows or hides other settings
     * @param apply   puts the settings into effect now, for choices whose result should show at once
     */
    record Context(TranslateConfig config, Font font, Runnable rebuild, Runnable apply) {
    }
}
