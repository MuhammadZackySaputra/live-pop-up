package tiktokpopup.config;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Lokasi file mod. */
public final class Paths {
    private Paths() {}

    /** {@code <game>/config/tiktok-live-popup.json} */
    public static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("tiktok-live-popup.json");
    }

    /** {@code <game>/tiktok-live-popup/sounds} — taruh file .wav / .ogg di sini. */
    public static Path soundsDir() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("tiktok-live-popup").resolve("sounds");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
            // folder dibuat ulang saat dibutuhkan
        }
        return dir;
    }
}
