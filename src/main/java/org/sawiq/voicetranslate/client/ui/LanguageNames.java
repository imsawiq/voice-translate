package org.sawiq.voicetranslate.client.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.sawiq.voicetranslate.language.Language;

/** Language choices for the cycle buttons, with "auto" first. */
final class LanguageNames {
    static final String AUTO = "auto";

    private LanguageNames() {
    }

    static List<String> codesWithAuto() {
        List<String> codes = new ArrayList<>();
        codes.add(AUTO);
        Language.ALL.forEach(language -> codes.add(language.code()));
        return codes;
    }

    /** @param autoKey what "auto" means in this setting */
    static Component name(String code, String autoKey) {
        if (AUTO.equals(code)) {
            return Component.translatable(autoKey);
        }
        return Language.byCode(code).<Component>map(language -> Component.literal(language.nativeName()))
                .orElse(Component.literal(code));
    }
}
