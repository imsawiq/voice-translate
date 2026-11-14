package org.sawiq.voicetranslate.client.ui;

import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import org.sawiq.voicetranslate.client.VoiceTranslateClient;
import org.sawiq.voicetranslate.config.LlmProvider;
import org.sawiq.voicetranslate.config.TranslateConfig;
import org.sawiq.voicetranslate.config.TranslationService;
import org.sawiq.voicetranslate.config.TranslationSettings;

/** Which service translates, its key or model, and the game glossary. */
final class TranslationTab implements SettingsTab {
    @Override
    public void build(SettingsList list, Context context) {
        TranslateConfig config = context.config();
        TranslationSettings translation = config.translation;
        list.category(Component.translatable("voice-translate.category.translator"));
        list.option(Component.translatable("voice-translate.settings.translator"),
                Component.translatable("voice-translate.settings.translator.desc"),
                Widgets.cycle(Component.translatable("voice-translate.settings.translator"),
                        List.of(TranslationService.values()), translation.service,
                        service -> Component.translatable("voice-translate.translator." + service.name().toLowerCase(Locale.ROOT)),
                        service -> {
                            translation.service = service;
                            context.apply().run();
                            context.rebuild().run();
                        }));

        switch (translation.service) {
            case GOOGLE -> list.text(Component.translatable("voice-translate.hint.google"), UiPaint.DIM, 2);
            case DEEPL -> {
                list.option(Component.translatable("voice-translate.settings.deepl_key"),
                        Widgets.secret(context.font(), Component.translatable("voice-translate.settings.deepl_key"),
                                config.keys.deepL, key -> config.keys.deepL = key.strip()));
                list.text(Component.translatable("voice-translate.hint.deepl"), UiPaint.DIM, 2);
            }
            case LLM -> buildLlm(list, context);
        }

        list.category(Component.translatable("voice-translate.category.glossary"));
        list.text(() -> {
            int names = VoiceTranslateClient.INSTANCE.glossarySize();
            return names > 0
                    ? Component.translatable("voice-translate.glossary.ready", names)
                    : Component.translatable("voice-translate.glossary.none");
        }, () -> UiPaint.TEXT, 3);
    }

    private void buildLlm(SettingsList list, Context context) {
        TranslateConfig config = context.config();
        TranslationSettings translation = config.translation;
        list.option(Component.translatable("voice-translate.settings.llm_provider"),
                Widgets.cycle(Component.translatable("voice-translate.settings.llm_provider"),
                        List.of(LlmProvider.values()), translation.llmProvider,
                        provider -> Component.translatable("voice-translate.llm." + provider.name().toLowerCase(Locale.ROOT)),
                        provider -> {
                            translation.selectLlmProvider(provider);
                            context.apply().run();
                            context.rebuild().run();
                        }));
        LlmProvider provider = translation.llmProvider;
        if (provider == LlmProvider.OLLAMA || provider == LlmProvider.CUSTOM) {
            list.option(Component.translatable("voice-translate.settings.address"),
                    Widgets.text(context.font(), Component.translatable("voice-translate.settings.address"),
                            translation.llmUrl, url -> translation.llmUrl = url.strip()));
        }
        list.option(Component.translatable(provider.needsKey()
                        ? "voice-translate.settings.api_key" : "voice-translate.settings.api_key_optional"),
                Widgets.secret(context.font(), Component.translatable("voice-translate.settings.api_key"),
                        config.keys.forLlm(provider), key -> config.keys.setForLlm(provider, key.strip())));
        list.option(Component.translatable("voice-translate.settings.model"),
                Widgets.text(context.font(), Component.translatable("voice-translate.settings.model"),
                        translation.llmModel, model -> translation.llmModel = model.strip()));
        list.text(Component.translatable("voice-translate.hint.llm"), UiPaint.DIM, 3);
    }
}
