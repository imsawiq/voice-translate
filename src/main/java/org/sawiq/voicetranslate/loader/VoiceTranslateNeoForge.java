package org.sawiq.voicetranslate.loader;

//? if neoforge {
/*import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import org.sawiq.voicetranslate.VoiceTranslate;
import org.sawiq.voicetranslate.client.KeyBindings;
import org.sawiq.voicetranslate.client.VoiceTranslateClient;
import org.sawiq.voicetranslate.client.hud.CaptionHud;
import org.sawiq.voicetranslate.client.ui.SettingsScreen;
import org.sawiq.voicetranslate.voice.plasmo.PlasmoVoiceAddon;

// NeoForge entrypoint, client only. Simple Voice Chat finds its plugin by the
// @ForgeVoicechatPlugin annotation; Plasmo Voice is handed its addon here,
// and only when it is installed.
@Mod(value = VoiceTranslate.NEOFORGE_MOD_ID, dist = Dist.CLIENT)
public final class VoiceTranslateNeoForge {
    private final KeyMapping.Category category = new KeyMapping.Category(KeyBindings.CATEGORY_ID);
    private final KeyBindings keys = new KeyBindings(this.category);

    public VoiceTranslateNeoForge(IEventBus modEventBus, ModContainer container) {
        VoiceTranslateClient.INSTANCE.initialize(FMLPaths.CONFIGDIR.get(), this.keys);
        if (ModList.get().isLoaded("plasmovoice")) {
            PlasmoVoiceAddon.register();
        }
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent));

        modEventBus.addListener(this::registerKeys);
        modEventBus.addListener(this::registerLayers);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onShutdown);
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(this.category);
        this.keys.all().forEach(event::register);
    }

    private void registerLayers(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.CHAT,
                Identifier.fromNamespaceAndPath(VoiceTranslate.MOD_ID, "captions"), CaptionHud::render);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        VoiceTranslateClient.INSTANCE.tick(Minecraft.getInstance());
    }

    private void onShutdown(GameShuttingDownEvent event) {
        VoiceTranslateClient.INSTANCE.shutdown();
    }
}
*///?}
