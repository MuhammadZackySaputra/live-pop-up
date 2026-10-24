package tiktokpopup.hud;

import net.minecraft.client.Minecraft;

/** Kapan HUD mod boleh digambar: hanya saat bermain di dunia tanpa layar terbuka. */
final class HudVisibility {
    private HudVisibility() {}

    static boolean shouldRender(Minecraft client) {
        return client != null
            && client.player != null
            && client.level != null
            && client.gui.screen() == null;
    }

    static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /** Teks dengan alpha sangat kecil dianggap opaque oleh Minecraft, jadi lewati saja. */
    static boolean visible(float alpha) {
        return alpha > 0.04f;
    }
}
