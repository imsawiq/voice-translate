package org.sawiq.voicetranslate.client.ui;

import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import org.sawiq.voicetranslate.config.LlmProvider;
import org.sawiq.voicetranslate.config.TranslateConfig;
import org.sawiq.voicetranslate.config.TranslationService;
import org.sawiq.voicetranslate.config.TranslationSettings;

/** Which service translates, and its key or model. */
final class TranslationTab implements SettingsTab {
    @Override
    public String titleKey() {
        return "voice-translate.tab.translation";
    }

    @Override
    public void build(FormLayout form, Context context) {
        TranslateConfig config = context.config();
        TranslationSettings translation = config.translation;
        form.full(Widgets.cycle(Component.translatable("voice-translate.settings.translator"),
                List.of(TranslationService.values()), translation.service,
                service -> Component.translatable("voice-translate.translator." + service.name().toLowerCase(Locale.ROOT)),
                service -> {
                    translation.service = service;
                    context.apply().run();
                    context.rebuild().run();
                }));

        switch (translation.service) {
            case GOOGLE -> form.text(Component.translatable("voice-translate.hint.google"), 0xFF909090);
            case DEEPL -> {
                form.labelled(Component.translatable("voice-translate.settings.deepl_key"),
                        Widgets.secret(context.font(), Component.translatable("voice-translate.settings.deepl_key"),
                                config.keys.deepL, key -> config.keys.deepL = key.strip()));
                form.text(Component.translatable("voice-translate.hint.deepl"), 0xFF909090);
            }
            case LLM -> buildLlm(form, context);
        }
    }

    private void buildLlm(FormLayout form, Context context) {
        TranslateConfig config = context.config();
        TranslationSettings translation = config.translation;
        form.full(Widgets.cycle(Component.translatable("voice-translate.settings.llm_provider"),
                List.of(LlmProvider.values()), translation.llmProvider,
                provider -> Component.translatable("voice-translate.llm." + provider.name().toLowerCase(Locale.ROOT)),
                provider -> {
                    translation.selectLlmProvider(provider);
                    context.apply().run();
                    context.rebuild().run();
                }));
        LlmProvider provider = translation.llmProvider;
        if (provider == LlmProvider.OLLAMA || provider == LlmProvider.CUSTOM) {
            form.labelled(Component.translatable("voice-translate.settings.address"),
                    Widgets.text(context.font(), Component.translatable("voice-translate.settings.address"),
                            translation.llmUrl, url -> translation.llmUrl = url.strip()));
        }
        form.labelled(Component.translatable(provider.needsKey()
                        ? "voice-translate.settings.api_key" : "voice-translate.settings.api_key_optional"),
                Widgets.secret(context.font(), Component.translatable("voice-translate.settings.api_key"),
                        config.keys.forLlm(provider), key -> config.keys.setForLlm(provider, key.strip())));
        form.labelled(Component.translatable("voice-translate.settings.model"),
                Widgets.text(context.font(), Component.translatable("voice-translate.settings.model"),
                        translation.llmModel, model -> translation.llmModel = model.strip()));
        form.text(Component.translatable("voice-translate.hint.llm"), 0xFF909090);
    }
}
