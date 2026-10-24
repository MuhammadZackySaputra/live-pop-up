package tiktokpopup.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import tiktokpopup.config.EventSetting;
import tiktokpopup.config.Paths;
import tiktokpopup.config.PopupConfig;
import tiktokpopup.config.StatsCorner;
import tiktokpopup.core.Kind;
import tiktokpopup.core.LiveSession;
import tiktokpopup.core.LiveStats;
import tiktokpopup.sound.SoundLibrary;
import tiktokpopup.sound.SoundPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Layar pengaturan. Tab: Umum (koneksi, popup, statistik) dan satu tab per jenis
 * notifikasi (Chat / Follow / Gift / Fans) dengan sound yang bisa dipilih sendiri.
 */
public final class SettingsScreen extends Screen {
    private static final int CONTENT_W = 300;
    private static final int COL_W = 148;
    private static final int ROW_H = 22;
    private static final int BTN_H = 20;

    private static final int[] MAX_VISIBLE_STEPS = {3, 4, 5, 6, 8, 10, 12, 15};
    private static final int[] SECONDS_STEPS = {4, 6, 8, 10, 15, 20, 30, 60};
    private static final int[] WIDTH_STEPS = {160, 200, 220, 260, 300, 360, 420};
    private static final float[] SCALE_STEPS = {0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    private static final int[] LEFT_STEPS = {0, 6, 12, 24, 48, 96};
    private static final int[] BOTTOM_STEPS = {8, 24, 40, 48, 60, 80, 100, 140, 180, 240};

    /** Tab 0 = umum; 1..4 = jenis notifikasi. */
    private static final Kind[] TAB_KINDS = {null, Kind.CHAT, Kind.FOLLOW, Kind.GIFT, Kind.FANS};

    private final Screen parent;
    private int tab = 0;

    private EditBox usernameBox;
    private String pendingUsername;

    private final List<Button> tabButtons = new ArrayList<>();
    private final List<Button> generalButtons = new ArrayList<>();
    private final List<Button> eventButtons = new ArrayList<>();

    private Button connectButton;
    private Button popupToggle;
    private Button soundToggle;
    private Button soundFileButton;
    private Button volumeButton;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("tiktokpopup.settings.title"));
        this.parent = parent;
        this.pendingUsername = PopupConfig.get().username;
    }

    @Override
    protected void init() {
        tabButtons.clear();
        generalButtons.clear();
        eventButtons.clear();
        PopupConfig cfg = PopupConfig.get();

        int left = this.width / 2 - CONTENT_W / 2;
        int right = left + CONTENT_W - COL_W;

        // --- tab bar ---
        int tabW = Math.min(64, (this.width - 16) / TAB_KINDS.length);
        int tabsLeft = this.width / 2 - (tabW * TAB_KINDS.length) / 2;
        for (int i = 0; i < TAB_KINDS.length; i++) {
            final int index = i;
            Component label = i == 0
                ? Component.translatable("tiktokpopup.tab.general")
                : Component.translatable("tiktokpopup.tab." + TAB_KINDS[i].id());
            Button b = Button.builder(label, btn -> selectTab(index))
                .bounds(tabsLeft + i * tabW, 18, tabW, BTN_H).build();
            tabButtons.add(b);
            this.addRenderableWidget(b);
        }

        // --- tab Umum ---
        int y = 44;
        usernameBox = new EditBox(this.font, left + 98, y, CONTENT_W - 98, BTN_H,
            Component.translatable("tiktokpopup.settings.username"));
        usernameBox.setMaxLength(32);
        usernameBox.setValue(pendingUsername);
        usernameBox.setResponder(value -> pendingUsername = value);
        this.addRenderableWidget(usernameBox);

        y += ROW_H + 2;
        connectButton = general(left, y, COL_W, connectLabel(), btn -> {
            if (LiveSession.get().isActive()) {
                LiveSession.get().disconnect();
            } else {
                applyUsername();
                LiveSession.get().connect(cfg.username);
            }
            btn.setMessage(connectLabel());
        });
        general(right, y, COL_W, autoConnectLabel(cfg), btn -> {
            cfg.autoConnect = !cfg.autoConnect;
            btn.setMessage(autoConnectLabel(cfg));
        });

        y += ROW_H;
        general(left, y, COL_W, popupLabel(cfg), btn -> {
            cfg.popupEnabled = !cfg.popupEnabled;
            btn.setMessage(popupLabel(cfg));
        });
        general(right, y, COL_W, backgroundLabel(cfg), btn -> {
            cfg.showBackground = !cfg.showBackground;
            btn.setMessage(backgroundLabel(cfg));
        });

        y += ROW_H;
        general(left, y, COL_W, maxLabel(cfg), btn -> {
            cfg.maxVisible = nextInt(MAX_VISIBLE_STEPS, cfg.maxVisible);
            btn.setMessage(maxLabel(cfg));
        });
        general(right, y, COL_W, secondsLabel(cfg), btn -> {
            cfg.displaySeconds = nextInt(SECONDS_STEPS, cfg.displaySeconds);
            btn.setMessage(secondsLabel(cfg));
        });

        y += ROW_H;
        general(left, y, COL_W, widthLabel(cfg), btn -> {
            cfg.popupWidth = nextInt(WIDTH_STEPS, cfg.popupWidth);
            btn.setMessage(widthLabel(cfg));
        });
        general(right, y, COL_W, scaleLabel(cfg), btn -> {
            cfg.scale = nextFloat(SCALE_STEPS, cfg.scale);
            btn.setMessage(scaleLabel(cfg));
        });

        y += ROW_H;
        general(left, y, COL_W, leftLabel(cfg), btn -> {
            cfg.marginLeft = nextInt(LEFT_STEPS, cfg.marginLeft);
            btn.setMessage(leftLabel(cfg));
        });
        general(right, y, COL_W, bottomLabel(cfg), btn -> {
            cfg.marginBottom = nextInt(BOTTOM_STEPS, cfg.marginBottom);
            btn.setMessage(bottomLabel(cfg));
        });

        y += ROW_H;
        general(left, y, COL_W, statsLabel(cfg), btn -> {
            cfg.statsEnabled = !cfg.statsEnabled;
            btn.setMessage(statsLabel(cfg));
        });
        general(right, y, COL_W, cornerLabel(cfg), btn -> {
            cfg.statsCorner = cfg.statsCorner.next();
            btn.setMessage(cornerLabel(cfg));
        });

        // --- tab notifikasi (satu set widget, mengikuti jenis yang dipilih) ---
        int ey = 50;
        popupToggle = event(left, ey, COL_W, Component.empty(), btn -> {
            EventSetting s = currentSetting();
            if (s != null) {
                s.popup = !s.popup;
                refreshEventLabels();
            }
        });
        soundToggle = event(right, ey, COL_W, Component.empty(), btn -> {
            EventSetting s = currentSetting();
            if (s != null) {
                s.sound = !s.sound;
                refreshEventLabels();
            }
        });

        ey += ROW_H;
        soundFileButton = event(left, ey, CONTENT_W, Component.empty(), btn -> {
            EventSetting s = currentSetting();
            if (s != null) {
                List<String> options = SoundLibrary.options();
                int idx = indexOfIgnoreCase(options, s.soundFile);
                s.soundFile = options.get((idx + 1) % options.size());
                refreshEventLabels();
            }
        });

        ey += ROW_H;
        volumeButton = event(left, ey, COL_W, Component.empty(), btn -> {
            EventSetting s = currentSetting();
            if (s != null) {
                s.volume = s.volume >= 100 ? 0 : (s.volume / 10) * 10 + 10;
                refreshEventLabels();
            }
        });
        event(right, ey, COL_W, Component.translatable("tiktokpopup.btn.test_sound"), btn -> {
            Kind kind = TAB_KINDS[tab];
            EventSetting s = currentSetting();
            if (kind != null && s != null) {
                SoundPlayer.playNow(kind, s);
            }
        });

        ey += ROW_H;
        event(left, ey, CONTENT_W, Component.translatable("tiktokpopup.btn.test_notification"), btn -> {
            Kind kind = TAB_KINDS[tab];
            if (kind != null) {
                LiveSession.get().simulate(kind);
            }
        });

        // --- tombol Selesai ---
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> this.onClose())
            .bounds(this.width / 2 - 100, this.height - 28, 200, BTN_H).build());

        applyTabVisibility();
        refreshEventLabels();
    }

    // ---- pembuat widget ----

    private Button general(int x, int y, int w, Component label, Button.OnPress onPress) {
        Button b = Button.builder(label, onPress).bounds(x, y, w, BTN_H).build();
        generalButtons.add(b);
        this.addRenderableWidget(b);
        return b;
    }

    private Button event(int x, int y, int w, Component label, Button.OnPress onPress) {
        Button b = Button.builder(label, onPress).bounds(x, y, w, BTN_H).build();
        eventButtons.add(b);
        this.addRenderableWidget(b);
        return b;
    }

    // ---- tab ----

    private void selectTab(int index) {
        applyUsername();
        tab = index;
        applyTabVisibility();
        refreshEventLabels();
    }

    private void applyTabVisibility() {
        boolean general = tab == 0;
        usernameBox.visible = general;
        usernameBox.setEditable(general);
        for (Button b : generalButtons) {
            b.visible = general;
        }
        for (Button b : eventButtons) {
            b.visible = !general;
        }
        for (int i = 0; i < tabButtons.size(); i++) {
            tabButtons.get(i).active = i != tab;
        }
    }

    private EventSetting currentSetting() {
        Kind kind = TAB_KINDS[tab];
        return kind == null ? null : kind.setting(PopupConfig.get());
    }

    private void refreshEventLabels() {
        EventSetting s = currentSetting();
        if (s == null || popupToggle == null) {
            return;
        }
        popupToggle.setMessage(onOff("tiktokpopup.opt.popup", s.popup));
        soundToggle.setMessage(onOff("tiktokpopup.opt.sound", s.sound));
        soundFileButton.setMessage(Component.translatable("tiktokpopup.opt.sound_file", s.soundFile));
        volumeButton.setMessage(Component.translatable("tiktokpopup.opt.volume", s.volume));
    }

    // ---- label ----

    private Component connectLabel() {
        return LiveSession.get().isActive()
            ? Component.translatable("tiktokpopup.btn.disconnect")
            : Component.translatable("tiktokpopup.btn.connect");
    }

    private static Component onOff(String key, boolean value) {
        return Component.translatable(key, Component.translatable(value ? "options.on" : "options.off"));
    }

    private static Component autoConnectLabel(PopupConfig c) {
        return onOff("tiktokpopup.opt.autoconnect", c.autoConnect);
    }

    private static Component popupLabel(PopupConfig c) {
        return onOff("tiktokpopup.opt.popup_enabled", c.popupEnabled);
    }

    private static Component backgroundLabel(PopupConfig c) {
        return onOff("tiktokpopup.opt.background", c.showBackground);
    }

    private static Component maxLabel(PopupConfig c) {
        return Component.translatable("tiktokpopup.opt.max_visible", c.maxVisible);
    }

    private static Component secondsLabel(PopupConfig c) {
        return Component.translatable("tiktokpopup.opt.seconds", c.displaySeconds);
    }

    private static Component widthLabel(PopupConfig c) {
        return Component.translatable("tiktokpopup.opt.width", c.popupWidth);
    }

    private static Component scaleLabel(PopupConfig c) {
        return Component.translatable("tiktokpopup.opt.scale", String.format("%.2f", c.scale));
    }

    private static Component leftLabel(PopupConfig c) {
        return Component.translatable("tiktokpopup.opt.margin_left", c.marginLeft);
    }

    private static Component bottomLabel(PopupConfig c) {
        return Component.translatable("tiktokpopup.opt.margin_bottom", c.marginBottom);
    }

    private static Component statsLabel(PopupConfig c) {
        return onOff("tiktokpopup.opt.stats", c.statsEnabled);
    }

    private static Component cornerLabel(PopupConfig c) {
        return Component.translatable("tiktokpopup.opt.corner", Component.translatable(cornerKey(c.statsCorner)));
    }

    private static String cornerKey(StatsCorner corner) {
        return "tiktokpopup.corner." + corner.name().toLowerCase(java.util.Locale.ROOT);
    }

    // ---- util ----

    private static int nextInt(int[] steps, int current) {
        for (int step : steps) {
            if (step > current) {
                return step;
            }
        }
        return steps[0];
    }

    private static float nextFloat(float[] steps, float current) {
        for (float step : steps) {
            if (step > current + 0.001f) {
                return step;
            }
        }
        return steps[0];
    }

    private static int indexOfIgnoreCase(List<String> list, String value) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).equalsIgnoreCase(value)) {
                return i;
            }
        }
        return 0;
    }

    private void applyUsername() {
        PopupConfig cfg = PopupConfig.get();
        cfg.username = pendingUsername == null ? "" : pendingUsername.trim().replace("@", "");
    }

    // ---- render ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        Font f = this.font;

        g.text(f, this.title, this.width / 2 - f.width(this.title) / 2, 6, 0xFFFFFFFF);

        int left = this.width / 2 - CONTENT_W / 2;
        if (tab == 0) {
            g.text(f, Component.translatable("tiktokpopup.settings.username"), left, 50, 0xFFDDDDDD);
            Component status = Component.translatable(
                "tiktokpopup.settings.status",
                Component.translatable(stateKey(LiveStats.state()))
            );
            g.text(f, status, this.width / 2 - f.width(status) / 2, this.height - 40, 0xFFAAAAAA);
        } else {
            int y = 50 + ROW_H * 4 + 4;
            Component hint = Component.translatable(
                "tiktokpopup.settings.sounds_hint", Paths.soundsDir().toString()
            );
            for (FormattedCharSequence line : f.split(hint, CONTENT_W)) {
                g.text(f, line, left, y, 0xFFAAAAAA);
                y += f.lineHeight;
            }
        }
    }

    private static String stateKey(tiktokpopup.tiktok.TikTokListener.State state) {
        return "tiktokpopup.state." + state.name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        applyUsername();
        PopupConfig.get().save();
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(parent);
        }
    }
}
