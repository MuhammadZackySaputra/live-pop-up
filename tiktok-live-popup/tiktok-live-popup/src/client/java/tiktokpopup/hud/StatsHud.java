package tiktokpopup.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import tiktokpopup.Log;
import tiktokpopup.config.PopupConfig;
import tiktokpopup.core.LiveStats;
import tiktokpopup.tiktok.TikTokListener;

import java.text.NumberFormat;

/** Panel kecil: status live, jumlah viewer, dan likes secara realtime. */
public final class StatsHud {
    private static final int PAD_X = 5;
    private static final int PAD_Y = 3;
    private static final int MARGIN = 6;
    private static final int SEP = 8;
    private static final long PULSE_MS = 380L;

    private static final NumberFormat NUMBERS = NumberFormat.getIntegerInstance();

    private static double shownViewers;
    private static double shownLikes;
    private static long lastFrameNanos = System.nanoTime();

    private StatsHud() {}

    public static void register() {
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.BOSS_BAR,
            Identifier.fromNamespaceAndPath(Log.MOD_ID, "stats"),
            (graphics, deltaTracker) -> render(graphics)
        );
    }

    private static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        PopupConfig cfg = PopupConfig.get();
        if (!cfg.statsEnabled || !HudVisibility.shouldRender(mc)) {
            return;
        }

        TikTokListener.State state = LiveStats.state();
        long viewers = LiveStats.viewers();
        long likes = LiveStats.likes();
        boolean connected = state == TikTokListener.State.CONNECTED;
        if (!connected && viewers == 0L && likes == 0L && state == TikTokListener.State.DISCONNECTED) {
            return;
        }

        long nanos = System.nanoTime();
        float dt = Math.min(0.25f, (nanos - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = nanos;
        shownViewers = approach(shownViewers, viewers, dt);
        shownLikes = approach(shownLikes, likes, dt);

        Font font = mc.font;
        int lineH = font.lineHeight;
        int height = lineH + PAD_Y * 2;

        Component status = statusText(state);
        boolean showNumbers = connected || viewers > 0L || likes > 0L;

        Component viewersLabel = Component.translatable("tiktokpopup.stats.viewers");
        Component viewersValue = Component.literal(NUMBERS.format(Math.round(shownViewers)));
        Component heart = Component.literal("♥");
        Component likesValue = Component.literal(NUMBERS.format(Math.round(shownLikes)));

        int dotW = 5;
        int width = PAD_X + dotW + 4 + font.width(status);
        if (showNumbers) {
            width += SEP + font.width(viewersLabel) + 4 + font.width(viewersValue)
                + SEP + font.width(heart) + 3 + font.width(likesValue);
        }
        width += PAD_X;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int x = switch (cfg.statsCorner) {
            case TOP_LEFT, BOTTOM_LEFT -> MARGIN;
            case TOP_RIGHT, BOTTOM_RIGHT -> sw - width - MARGIN;
        };
        int y = switch (cfg.statsCorner) {
            case TOP_LEFT, TOP_RIGHT -> MARGIN;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> sh - height - MARGIN;
        };

        g.fill(x, y, x + width, y + height, HudVisibility.argb(0.68f, 0x101018));

        int cx = x + PAD_X;
        int ty = y + PAD_Y;
        g.fill(cx, y + (height - dotW) / 2, cx + dotW, y + (height - dotW) / 2 + dotW, HudVisibility.argb(1f, dotColor(state)));
        cx += dotW + 4;
        g.text(font, status, cx, ty, HudVisibility.argb(1f, connected ? 0xFFFFFF : 0xC8C8C8));
        cx += font.width(status);

        if (showNumbers) {
            cx += SEP;
            g.text(font, viewersLabel, cx, ty, HudVisibility.argb(1f, 0x25F4EE));
            cx += font.width(viewersLabel) + 4;
            g.text(font, viewersValue, cx, ty, HudVisibility.argb(1f, 0xFFFFFF));
            cx += font.width(viewersValue) + SEP;

            long sinceLike = System.currentTimeMillis() - LiveStats.lastLikeMs();
            float pulse = sinceLike >= 0 && sinceLike < PULSE_MS ? 1f - sinceLike / (float) PULSE_MS : 0f;
            g.text(font, heart, cx, ty, HudVisibility.argb(1f, mix(0xFE2C55, 0xFFFFFF, pulse)));
            cx += font.width(heart) + 3;
            g.text(font, likesValue, cx, ty, HudVisibility.argb(1f, 0xFFFFFF));
        }
    }

    private static Component statusText(TikTokListener.State state) {
        return switch (state) {
            case CONNECTED -> Component.translatable("tiktokpopup.stats.live");
            case CONNECTING -> Component.translatable("tiktokpopup.stats.connecting");
            case WAITING_FOR_LIVE -> Component.translatable("tiktokpopup.stats.waiting");
            case LIVE_ENDED -> Component.translatable("tiktokpopup.stats.ended");
            case ERROR -> Component.translatable("tiktokpopup.stats.error");
            case DISCONNECTED -> Component.translatable("tiktokpopup.stats.offline");
        };
    }

    private static int dotColor(TikTokListener.State state) {
        return switch (state) {
            case CONNECTED -> 0xFF3B5C;
            case CONNECTING, WAITING_FOR_LIVE -> 0xFFC93C;
            case ERROR -> 0xFF5555;
            case LIVE_ENDED, DISCONNECTED -> 0x888888;
        };
    }

    /** Angka bergerak halus menuju target agar terasa realtime. */
    private static double approach(double shown, long target, float dt) {
        double diff = target - shown;
        if (Math.abs(diff) < 0.5) {
            return target;
        }
        // mundur (reset/koneksi baru) langsung loncat
        if (diff < 0) {
            return target;
        }
        return shown + diff * (1.0 - Math.exp(-dt * 7.0));
    }

    private static int mix(int from, int to, float t) {
        int r = Math.round(((from >> 16) & 0xFF) * (1 - t) + ((to >> 16) & 0xFF) * t);
        int gr = Math.round(((from >> 8) & 0xFF) * (1 - t) + ((to >> 8) & 0xFF) * t);
        int b = Math.round((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
        return (r << 16) | (gr << 8) | b;
    }
}
