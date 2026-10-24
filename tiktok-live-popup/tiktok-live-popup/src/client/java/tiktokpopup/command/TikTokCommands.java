package tiktokpopup.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import tiktokpopup.TikTokPopupClient;
import tiktokpopup.config.Paths;
import tiktokpopup.core.Kind;
import tiktokpopup.core.LiveSession;
import tiktokpopup.core.LiveStats;
import tiktokpopup.core.PlayerChat;

/** Perintah {@code /tiktok ...} (client-side). */
public final class TikTokCommands {
    private TikTokCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(root("tiktok")));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> root(String name) {
        return ClientCommands.literal(name)
            .then(ClientCommands.literal("connect")
                .then(ClientCommands.argument("username", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        LiveSession.get().connect(StringArgumentType.getString(ctx, "username"));
                        return 1;
                    })))
            .then(ClientCommands.literal("disconnect")
                .executes(ctx -> {
                    LiveSession.get().disconnect();
                    PlayerChat.say(Component.translatable("tiktokpopup.msg.disconnected"));
                    return 1;
                }))
            .then(ClientCommands.literal("settings")
                .executes(ctx -> {
                    TikTokPopupClient.openSettingsNextTick();
                    return 1;
                }))
            .then(ClientCommands.literal("status")
                .executes(ctx -> {
                    PlayerChat.say(Component.translatable(
                        "tiktokpopup.msg.status",
                        Component.translatable("tiktokpopup.state." + LiveStats.state().name().toLowerCase(java.util.Locale.ROOT)),
                        LiveStats.username().isEmpty() ? "-" : "@" + LiveStats.username(),
                        LiveStats.viewers(),
                        LiveStats.likes()
                    ));
                    return 1;
                }))
            .then(ClientCommands.literal("sounds")
                .executes(ctx -> {
                    PlayerChat.say(Component.translatable("tiktokpopup.msg.sounds_folder", Paths.soundsDir().toString()));
                    return 1;
                }))
            .then(ClientCommands.literal("debug")
                .then(ClientCommands.literal("on").executes(ctx -> setDebug(true)))
                .then(ClientCommands.literal("off").executes(ctx -> setDebug(false))))
            .then(ClientCommands.literal("test")
                .then(ClientCommands.literal("chat").executes(ctx -> test(Kind.CHAT)))
                .then(ClientCommands.literal("follow").executes(ctx -> test(Kind.FOLLOW)))
                .then(ClientCommands.literal("gift").executes(ctx -> test(Kind.GIFT)))
                .then(ClientCommands.literal("fans").executes(ctx -> test(Kind.FANS)))
                .then(ClientCommands.literal("stats").executes(ctx -> {
                    LiveSession.get().simulateStats();
                    return 1;
                })));
    }

    private static int setDebug(boolean on) {
        LiveSession.get().setDebugEvents(on);
        PlayerChat.say(Component.translatable(on ? "tiktokpopup.msg.debug_on" : "tiktokpopup.msg.debug_off"));
        return 1;
    }

    private static int test(Kind kind) {
        LiveSession.get().simulate(kind);
        return 1;
    }
}
