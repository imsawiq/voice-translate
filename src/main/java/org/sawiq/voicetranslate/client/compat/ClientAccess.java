package org.sawiq.voicetranslate.client.compat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The client calls whose names moved between Minecraft releases. Everything
 * version-specific about screens and chat goes through here, so the rest of
 * the mod reads the same on every target.
 */
public final class ClientAccess {
    private ClientAccess() {
    }

    /** The screen on display, or null in game. */
    public static Screen currentScreen() {
        //? if >=26.2 {
        return Minecraft.getInstance().gui.screen();
        //?} else {
        /*return Minecraft.getInstance().screen;
        *///?}
    }

    public static void openScreen(Screen screen) {
        //? if >=26.2 {
        Minecraft.getInstance().gui.setScreen(screen);
        //?} else {
        /*Minecraft.getInstance().setScreen(screen);
        *///?}
    }

    /** The camera the world is drawn from. */
    public static Camera camera() {
        //? if >=26.2 {
        return Minecraft.getInstance().gameRenderer.mainCamera();
        //?} else {
        /*return Minecraft.getInstance().gameRenderer.getMainCamera();
        *///?}
    }

    /** A line in the chat log, seen only by this player. */
    public static void chat(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(message);
        }
    }

    /** A line above the hotbar. */
    public static void actionBar(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendOverlayMessage(message);
        }
    }
}
