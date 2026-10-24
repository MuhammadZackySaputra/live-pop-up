package tiktokpopup.core;

import net.minecraft.network.chat.Component;
import tiktokpopup.Log;
import tiktokpopup.config.EventSetting;
import tiktokpopup.config.PopupConfig;
import tiktokpopup.sound.SoundPlayer;
import tiktokpopup.tiktok.TikTokClient;
import tiktokpopup.tiktok.TikTokListener;
import tiktokpopup.tiktok.TikTokRoles;

/**
 * Menghubungkan koneksi TikTok dengan popup, statistik, dan sound.
 * Event dari thread jaringan masuk lewat {@link TikTokListener}.
 */
public final class LiveSession implements TikTokListener {
    private static final LiveSession INSTANCE = new LiveSession();

    private volatile TikTokClient client;

    private LiveSession() {}

    public static LiveSession get() {
        return INSTANCE;
    }

    public boolean isActive() {
        TikTokClient c = client;
        return c != null && c.isRunning();
    }

    public synchronized void connect(String username) {
        String user = username == null ? "" : username.trim().replace("@", "");
        if (user.isEmpty()) {
            PlayerChat.say(Component.translatable("tiktokpopup.msg.no_username"));
            return;
        }
        disconnect();
        PopupConfig cfg = PopupConfig.get();
        cfg.username = user;
        cfg.save();
        LiveStats.reset();
        PopupFeed.clear();
        LiveStats.setState(State.CONNECTING, user, "");
        TikTokClient created = new TikTokClient(user, this);
        created.setDebugEvents(cfg.debugEvents);
        client = created;
    }

    public synchronized void disconnect() {
        TikTokClient old = client;
        client = null;
        if (old != null) {
            old.disconnect();
        }
        LiveStats.setState(State.DISCONNECTED, PopupConfig.get().username, "");
    }

    public void setDebugEvents(boolean enabled) {
        PopupConfig cfg = PopupConfig.get();
        cfg.debugEvents = enabled;
        cfg.save();
        TikTokClient c = client;
        if (c != null) {
            c.setDebugEvents(enabled);
        }
    }

    // ---- TikTokListener (thread jaringan) ----

    @Override
    public void onChat(String uniqueId, String displayName, String text, TikTokRoles roles) {
        String clean = TextClean.chat(text);
        if (clean.isEmpty()) {
            return;
        }
        emit(Kind.CHAT, PopupEntry.chat(TextClean.name(displayName), clean, nameColor(roles)));
    }

    @Override
    public void onGift(String uniqueId, String displayName, String giftName, int count) {
        String gift = TextClean.clean(giftName, 40);
        if (gift.isEmpty() || "[emoji]".equals(gift)) {
            emit(Kind.GIFT, PopupEntry.event(Kind.GIFT, TextClean.name(displayName), "tiktokpopup.event.gift_unknown"));
        } else {
            emit(Kind.GIFT, PopupEntry.event(Kind.GIFT, TextClean.name(displayName), "tiktokpopup.event.gift", gift, count));
        }
    }

    @Override
    public void onFollow(String uniqueId, String displayName) {
        emit(Kind.FOLLOW, PopupEntry.event(Kind.FOLLOW, TextClean.name(displayName), "tiktokpopup.event.follow"));
    }

    @Override
    public void onFans(String uniqueId, String displayName) {
        emit(Kind.FANS, PopupEntry.event(Kind.FANS, TextClean.name(displayName), "tiktokpopup.event.fans"));
    }

    @Override
    public void onLikes(String uniqueId, String displayName, int added, long total) {
        LiveStats.applyLikes(added, total);
    }

    @Override
    public void onViewers(long viewers) {
        LiveStats.setViewers(viewers);
    }

    @Override
    public void onState(State state, String detail) {
        String user = client != null ? client.getUsername() : PopupConfig.get().username;
        LiveStats.setState(state, user, detail);
        switch (state) {
            case CONNECTED -> PlayerChat.say(Component.translatable("tiktokpopup.msg.connected", user));
            case WAITING_FOR_LIVE -> PlayerChat.say(Component.translatable("tiktokpopup.msg.waiting", user));
            case LIVE_ENDED -> PlayerChat.say(Component.translatable("tiktokpopup.msg.ended", user));
            case ERROR -> PlayerChat.say(Component.translatable("tiktokpopup.msg.error", detail));
            default -> { }
        }
        Log.LOGGER.info("TikTok state: {} {}", state, detail);
    }

    // ---- Simulasi untuk /tiktok test ----

    public void simulate(Kind kind) {
        switch (kind) {
            case CHAT -> onChat("test_user", "TestViewer", "Halo dari TikTok! Ini chat percobaan.", TikTokRoles.NONE);
            case FOLLOW -> onFollow("test_user", "TestViewer");
            case GIFT -> onGift("test_user", "TestViewer", "Rose", 3);
            case FANS -> onFans("test_user", "TestViewer");
        }
    }

    public void simulateStats() {
        LiveStats.setViewers(LiveStats.viewers() + 25 + (long) (Math.random() * 40));
        LiveStats.applyLikes(1 + (long) (Math.random() * 20), 0);
    }

    // ---- util ----

    private void emit(Kind kind, PopupEntry entry) {
        PopupConfig cfg = PopupConfig.get();
        EventSetting setting = kind.setting(cfg);
        if (cfg.popupEnabled && setting.popup) {
            PopupFeed.add(entry);
        }
        SoundPlayer.play(kind, setting);
    }

    private static int nameColor(TikTokRoles roles) {
        if (roles == null) {
            return Kind.CHAT.accentRgb();
        }
        if (roles.broadcaster()) return 0xFF5555;
        if (roles.moderator()) return 0x55FF55;
        if (roles.subscriber()) return 0xC58BFF;
        return Kind.CHAT.accentRgb();
    }
}
