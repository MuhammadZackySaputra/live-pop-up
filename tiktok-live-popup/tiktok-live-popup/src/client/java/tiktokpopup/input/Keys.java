package tiktokpopup.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import tiktokpopup.Log;
import tiktokpopup.TikTokPopupClient;

/** Tombol pintas: ] membuka pengaturan (bisa diubah di Controls). */
public final class Keys {
    private static final KeyMapping.Category CATEGORY =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Log.MOD_ID, "key_category"));

    private static KeyMapping openSettings;

    private Keys() {}

    public static void register() {
        openSettings = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.tiktokpopup.settings",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_RBRACKET,
            CATEGORY
        ));
    }

    /** Dipanggil tiap tick klien. */
    public static void tick() {
        while (openSettings != null && openSettings.consumeClick()) {
            TikTokPopupClient.openSettingsNextTick();
        }
    }
}
