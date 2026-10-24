package tiktokpopup.core;

/** Membersihkan teks TikTok agar aman ditampilkan dengan font Minecraft. */
public final class TextClean {
    public static final int MAX_CHAT_LENGTH = 200;
    public static final int MAX_NAME_LENGTH = 24;

    private TextClean() {}

    /**
     * Buang karakter kontrol, emoji gambar (font Minecraft tidak punya), selektor variasi,
     * lalu rapikan spasi dan batasi panjang. Jika hasil kosong tapi aslinya ada isi
     * (emoji saja) kembalikan {@code "[emoji]"}.
     */
    public static String clean(String raw, int maxLength) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        boolean sawEmoji = false;
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (cp >= 0x1F000 || cp == 0xFE0F || cp == 0xFE0E || cp == 0x200D || cp == 0x20E3) {
                sawEmoji = true;
                continue;
            }
            if (Character.isISOControl(cp) || cp == 0xFFFD) {
                sb.append(' ');
                continue;
            }
            sb.appendCodePoint(cp);
        }
        String out = sb.toString().replaceAll("\\s+", " ").trim();
        if (out.isEmpty()) {
            return sawEmoji ? "[emoji]" : "";
        }
        if (out.length() > maxLength) {
            out = out.substring(0, Math.max(1, maxLength - 1)) + "…";
        }
        return out;
    }

    public static String chat(String raw) {
        return clean(raw, MAX_CHAT_LENGTH);
    }

    public static String name(String raw) {
        String cleaned = clean(raw, MAX_NAME_LENGTH);
        return cleaned.isEmpty() || "[emoji]".equals(cleaned) ? "Viewer" : cleaned;
    }
}
