package tiktokpopup.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import tiktokpopup.Log;
import tiktokpopup.config.PopupConfig;
import tiktokpopup.core.Kind;
import tiktokpopup.core.PopupEntry;
import tiktokpopup.core.PopupFeed;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Popup chat & notifikasi di kiri bawah layar. Kartu terbaru di paling bawah,
 * yang lama naik ke atas lalu memudar.
 */
public final class PopupHud {
    private static final int PAD_X = 5;
    private static final int PAD_Y = 3;
    private static final int BAR_W = 2;
    private static final int GAP = 3;
    private static final long SLIDE_MS = 260L;
    private static final long FADE_IN_MS = 160L;

    /** Layout per kartu, di-cache supaya tidak memecah teks tiap frame. */
    private static final class Cached {
        int wrapWidth = -1;
        int width;
        int height;
        Component header;
        List<FormattedCharSequence> lines;
        float animY = Float.NaN;
    }

    private static final Map<PopupEntry, Cached> CACHE = new WeakHashMap<>();
    private static long lastFrameNanos = System.nanoTime();

    private PopupHud() {}

    public static void register() {
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.BOSS_BAR,
            Identifier.fromNamespaceAndPath(Log.MOD_ID, "popup"),
            (graphics, deltaTracker) -> render(graphics)
        );
    }

    private static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        PopupConfig cfg = PopupConfig.get();
        if (!cfg.popupEnabled || !HudVisibility.shouldRender(mc)) {
            return;
        }
        long now = System.currentTimeMillis();
        long lifeMs = cfg.displaySeconds * 1000L;
        List<PopupEntry> entries = PopupFeed.snapshot(now, lifeMs, cfg.maxVisible);
        if (entries.isEmpty()) {
            return;
        }

        long nanos = System.nanoTime();
        float dt = Math.min(0.25f, (nanos - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = nanos;
        float follow = 1f - (float) Math.exp(-dt * 14f);

        Font font = mc.font;
        float scale = cfg.scale;
        float screenH = mc.getWindow().getGuiScaledHeight() / scale;
        int maxWidth = cfg.popupWidth;
        int lineH = font.lineHeight;

        var pose = g.pose();
        pose.pushMatrix();
        pose.scale(scale, scale);

        float cursorY = screenH - cfg.marginBottom / scale;
        for (int i = entries.size() - 1; i >= 0; i--) {
            PopupEntry entry = entries.get(i);
            Cached c = CACHE.computeIfAbsent(entry, k -> new Cached());
            layout(font, entry, c, maxWidth, lineH);

            float targetY = cursorY - c.height;
            c.animY = Float.isNaN(c.animY) ? targetY : c.animY + (targetY - c.animY) * follow;
            cursorY = targetY - GAP;

            long age = now - entry.createdMs;
            float alpha = Math.min(1f, age / (float) FADE_IN_MS);
            if (age > lifeMs) {
                alpha = Math.min(alpha, 1f - (age - lifeMs) / (float) PopupFeed.FADE_MS);
            }
            if (!HudVisibility.visible(alpha)) {
                continue;
            }
            float slide = Math.min(1f, age / (float) SLIDE_MS);
            float eased = 1f - (1f - slide) * (1f - slide) * (1f - slide);
            float x = cfg.marginLeft / scale - (c.width + cfg.marginLeft) * (1f - eased);

            drawCard(g, font, cfg, entry, c, Math.round(x), Math.round(c.animY), alpha, lineH);
        }
        pose.popMatrix();
    }

    private static void layout(Font font, PopupEntry entry, Cached c, int maxWidth, int lineH) {
        if (c.wrapWidth == maxWidth) {
            return;
        }
        c.wrapWidth = maxWidth;
        int wrap = Math.max(40, maxWidth - BAR_W - PAD_X * 2);
        c.header = Component.literal(entry.kind.icon() + entry.name);
        Component body = entry.bodyKey != null
            ? Component.translatable(entry.bodyKey, entry.bodyArgs)
            : Component.literal(entry.bodyLiteral);
        c.lines = font.split(body, wrap);
        int contentW = font.width(c.header);
        for (FormattedCharSequence line : c.lines) {
            contentW = Math.max(contentW, font.width(line));
        }
        c.width = Math.min(maxWidth, Math.max(70, contentW + BAR_W + PAD_X * 2));
        c.height = PAD_Y * 2 + lineH + 1 + c.lines.size() * lineH;
    }

    private static void drawCard(GuiGraphicsExtractor g, Font font, PopupConfig cfg, PopupEntry entry,
        Cached c, int x, int y, float alpha, int lineH) {
        if (cfg.showBackground) {
            g.fill(x, y, x + c.width, y + c.height, HudVisibility.argb(alpha * 0.68f, 0x101018));
        }
        g.fill(x, y, x + BAR_W, y + c.height, HudVisibility.argb(alpha, entry.kind.accentRgb()));

        int textX = x + BAR_W + PAD_X;
        int textY = y + PAD_Y;
        g.text(font, c.header, textX, textY, HudVisibility.argb(alpha, entry.nameRgb));
        textY += lineH + 1;
        int bodyColor = HudVisibility.argb(alpha, entry.kind == Kind.CHAT ? 0xFFFFFF : 0xE6E6E6);
        for (FormattedCharSequence line : c.lines) {
            g.text(font, line, textX, textY, bodyColor);
            textY += lineH;
        }
    }
}
