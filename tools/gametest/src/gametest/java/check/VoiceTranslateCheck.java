package check;

import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import org.sawiq.voicetranslate.caption.Caption;
import org.sawiq.voicetranslate.client.VoiceTranslateClient;
import org.sawiq.voicetranslate.client.ui.HistoryScreen;
import org.sawiq.voicetranslate.client.ui.SettingsScreen;

public class VoiceTranslateCheck implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> {
                client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                long now = System.currentTimeMillis();
                UUID self = client.player.getUUID();
                var board = VoiceTranslateClient.INSTANCE.captions();
                board.publish(new Caption(1, self, "Steve", "Hey, follow me to the nether. I have ender pearls and a diamond pickaxe.",
                        "Эй, следуй за мной в Незер. У меня есть эндер-жемчуг и алмазная кирка.", "en", true, true, now - 3000, now + 60000));
                board.publish(new Caption(2, UUID.randomUUID(), "Alex", "Wo seid ihr? Ich bin am Spawn.",
                        "Где вы? Я на спавне.", "de", true, true, now - 2000, now + 60000));
                board.publish(new Caption(3, UUID.randomUUID(), "Notch", "Watch out, two creepers behind the",
                        "Осторожно, два крипера за", "en", true, false, now - 1000, now + 60000));
            });
            context.waitTicks(5);
            context.takeScreenshot("vt-hud");

            context.setScreen(() -> new SettingsScreen(null));
            context.waitTicks(3);
            context.takeScreenshot("vt-settings-general");
            for (String tab : new String[] {"speech", "translation", "display"}) {
                context.clickScreenButton("voice-translate.tab." + tab);
                context.waitTicks(3);
                context.takeScreenshot("vt-settings-" + tab);
            }
            context.setScreen(() -> new HistoryScreen(null));
            context.waitTicks(3);
            context.takeScreenshot("vt-history");
            context.setScreen(() -> null);
        }
    }
}
