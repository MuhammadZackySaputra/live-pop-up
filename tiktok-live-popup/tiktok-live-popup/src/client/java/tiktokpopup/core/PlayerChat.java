package tiktokpopup.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Kirim pesan sistem ke chat Minecraft pemain (hanya terlihat oleh pemain sendiri). */
public final class PlayerChat {
    private PlayerChat() {}

    public static void say(Component message) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendSystemMessage(message);
            }
        });
    }

    public static void say(String literal) {
        say(Component.literal(literal));
    }
}
