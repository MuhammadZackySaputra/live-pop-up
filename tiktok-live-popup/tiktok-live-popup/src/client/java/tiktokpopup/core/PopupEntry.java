package tiktokpopup.core;

/** Satu kartu di popup kiri bawah. Immutable, aman dibagi antar thread. */
public final class PopupEntry {
    public final Kind kind;
    public final String name;
    /** Jika {@code bodyKey} null, {@code bodyLiteral} yang ditampilkan. */
    public final String bodyKey;
    public final Object[] bodyArgs;
    public final String bodyLiteral;
    public final long createdMs;
    /** Warna nama 0xRRGGBB. */
    public final int nameRgb;

    private PopupEntry(Kind kind, String name, String bodyKey, Object[] bodyArgs, String bodyLiteral,
        long createdMs, int nameRgb) {
        this.kind = kind;
        this.name = name;
        this.bodyKey = bodyKey;
        this.bodyArgs = bodyArgs;
        this.bodyLiteral = bodyLiteral;
        this.createdMs = createdMs;
        this.nameRgb = nameRgb;
    }

    public static PopupEntry chat(String name, String text, int nameRgb) {
        return new PopupEntry(Kind.CHAT, name, null, new Object[0], text, System.currentTimeMillis(), nameRgb);
    }

    public static PopupEntry event(Kind kind, String name, String translationKey, Object... args) {
        return new PopupEntry(kind, name, translationKey, args, "", System.currentTimeMillis(), kind.accentRgb());
    }
}
