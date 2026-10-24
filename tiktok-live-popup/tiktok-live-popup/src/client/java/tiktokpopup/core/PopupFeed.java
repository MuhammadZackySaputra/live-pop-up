package tiktokpopup.core;

import java.util.ArrayList;
import java.util.List;

/** Antrian kartu popup. Ditulis dari thread jaringan, dibaca dari thread render. */
public final class PopupFeed {
    /** Durasi fade-out setelah masa tampil habis. */
    public static final long FADE_MS = 600L;
    private static final int HARD_LIMIT = 60;

    private static final List<PopupEntry> ENTRIES = new ArrayList<>();

    private PopupFeed() {}

    public static void add(PopupEntry entry) {
        synchronized (ENTRIES) {
            ENTRIES.add(entry);
            while (ENTRIES.size() > HARD_LIMIT) {
                ENTRIES.remove(0);
            }
        }
    }

    public static void clear() {
        synchronized (ENTRIES) {
            ENTRIES.clear();
        }
    }

    /**
     * Kartu yang masih terlihat, urut lama → baru, maksimal {@code max} kartu terbaru.
     * Kartu yang sudah lewat masa tampil + fade dibuang.
     */
    public static List<PopupEntry> snapshot(long nowMs, long lifeMs, int max) {
        synchronized (ENTRIES) {
            ENTRIES.removeIf(e -> nowMs - e.createdMs > lifeMs + FADE_MS);
            int from = Math.max(0, ENTRIES.size() - Math.max(1, max));
            return new ArrayList<>(ENTRIES.subList(from, ENTRIES.size()));
        }
    }
}
