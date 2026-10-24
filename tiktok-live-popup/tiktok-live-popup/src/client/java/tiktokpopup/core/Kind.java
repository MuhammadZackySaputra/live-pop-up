package tiktokpopup.core;

import tiktokpopup.config.EventSetting;
import tiktokpopup.config.PopupConfig;

/** Jenis kartu di popup / jenis sound. */
public enum Kind {
    CHAT("chat", 0x25F4EE, ""),
    FOLLOW("follow", 0xFE2C55, "♥ "),
    GIFT("gift", 0xFFC93C, "★ "),
    FANS("fans", 0xB57BFF, "♥ ");

    private final String id;
    private final int accentRgb;
    private final String icon;

    Kind(String id, int accentRgb, String icon) {
        this.id = id;
        this.accentRgb = accentRgb;
        this.icon = icon;
    }

    public String id() {
        return id;
    }

    /** Warna aksen 0xRRGGBB. */
    public int accentRgb() {
        return accentRgb;
    }

    /** Simbol kecil sebelum nama (kosong untuk chat). */
    public String icon() {
        return icon;
    }

    public EventSetting setting(PopupConfig cfg) {
        return switch (this) {
            case CHAT -> cfg.chat;
            case FOLLOW -> cfg.follow;
            case GIFT -> cfg.gift;
            case FANS -> cfg.fans;
        };
    }
}
