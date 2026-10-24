package tiktokpopup;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import tiktokpopup.command.TikTokCommands;
import tiktokpopup.config.PopupConfig;
import tiktokpopup.core.LiveSession;
import tiktokpopup.hud.PopupHud;
import tiktokpopup.hud.StatsHud;
import tiktokpopup.input.Keys;
import tiktokpopup.screen.SettingsScreen;

/** Entry point klien. */
public final class TikTokPopupClient implements ClientModInitializer {
    private static volatile boolean openSettingsRequested;
    private static boolean autoConnectTried;

    @Override
    public void onInitializeClient() {
        PopupConfig.get();
        PopupHud.register();
        StatsHud.register();
        Keys.register();
        TikTokCommands.register();
        ClientTickEvents.END_CLIENT_TICK.register(TikTokPopupClient::tick);
        Log.LOGGER.info("TikTok Live Popup initialized");
    }

    /**
     * Buka layar pengaturan di tick berikutnya. Wajib ditunda: perintah chat menutup
     * ChatScreen setelah dieksekusi, sehingga layar yang dibuka langsung akan ikut tertutup.
     */
    public static void openSettingsNextTick() {
        openSettingsRequested = true;
    }

    private static void tick(Minecraft client) {
        Keys.tick();

        if (openSettingsRequested) {
            openSettingsRequested = false;
            client.setScreenAndShow(new SettingsScreen(client.gui.screen()));
        }

        if (client.player == null) {
            return;
        }
        if (!autoConnectTried) {
            autoConnectTried = true;
            PopupConfig cfg = PopupConfig.get();
            if (cfg.autoConnect && !cfg.username.isBlank() && !LiveSession.get().isActive()) {
                LiveSession.get().connect(cfg.username);
            }
        }
    }
}
