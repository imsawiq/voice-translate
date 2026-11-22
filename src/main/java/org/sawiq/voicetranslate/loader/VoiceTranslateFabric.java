package org.sawiq.voicetranslate.loader;

//? if fabric {
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.sawiq.voicetranslate.VoiceTranslate;
import org.sawiq.voicetranslate.client.KeyBindings;
import org.sawiq.voicetranslate.client.VoiceTranslateClient;
import org.sawiq.voicetranslate.client.compat.HudLayer;
import org.sawiq.voicetranslate.voice.plasmo.PlasmoVoiceAddon;

/**
 * Fabric entrypoint. Simple Voice Chat finds its plugin through the
 * "voicechat" entrypoint in fabric.mod.json; Plasmo Voice is handed its addon
 * here, and only when it is installed, so its classes are never touched
 * without it.
 */
public final class VoiceTranslateFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeyBindings keys = new KeyBindings(KeyMapping.Category.register(KeyBindings.CATEGORY_ID));
        keys.all().forEach(KeyMappingHelper::registerKeyMapping);

        VoiceTranslateClient client = VoiceTranslateClient.INSTANCE;
        client.initialize(FabricLoader.getInstance().getConfigDir(), keys);
        if (FabricLoader.getInstance().isModLoaded("plasmovoice")) {
            PlasmoVoiceAddon.register();
        }

        // Before the chat, so an open chat window is drawn over the subtitles.
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(VoiceTranslate.MOD_ID, "captions"), HudLayer::render);
        ClientTickEvents.END_CLIENT_TICK.register(client::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> client.shutdown());
    }
}
//?}
