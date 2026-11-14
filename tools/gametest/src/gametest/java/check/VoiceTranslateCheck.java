package check;

import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.sawiq.voicetranslate.caption.Caption;
import org.sawiq.voicetranslate.client.VoiceTranslateClient;
import org.sawiq.voicetranslate.client.ui.SettingsScreen;

public class VoiceTranslateCheck implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            client.options.languageCode = "ru_ru";
            client.getLanguageManager().setSelected("ru_ru");
            client.reloadResourcePacks();
        });
        context.waitTicks(100);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.waitTicks(40);
            context.runOnClient(client -> {
                long now = System.currentTimeMillis();
                var board = VoiceTranslateClient.INSTANCE.captions();
                board.publish(new Caption(1, UUID.randomUUID(), "Steve", "Hey, follow me to the nether. I have ender pearls and a diamond pickaxe.",
                        "Эй, следуй за мной в Незер. У меня есть эндер-жемчуг и алмазная кирка.", "en", true, true, now - 30000, now - 29000));
                board.publish(new Caption(2, UUID.randomUUID(), "Alex", "Wo seid ihr? Ich bin am Spawn.",
                        "Где вы? Я на спавне.", "de", true, true, now - 20000, now - 19000));
            });
            for (SettingsScreen.Page page : SettingsScreen.Page.values()) {
                context.setScreen(() -> new SettingsScreen(null, page));
                context.waitTicks(4);
                context.takeScreenshot("vt-" + page.name().toLowerCase());
            }
            context.setScreen(() -> new de.maxhenkel.voicechat.gui.VoiceChatScreen());
            context.waitTicks(4);
            context.takeScreenshot("svc-menu");
            context.setScreen(() -> null);
        }
    }
}
