package tiktokpopup.core;

import tiktokpopup.tiktok.TikTokListener;

/** Statistik live realtime (viewer & likes) dan status koneksi. */
public final class LiveStats {
    private static volatile long viewers;
    private static volatile long likes;
    private static volatile long lastLikeMs;
    private static volatile TikTokListener.State state = TikTokListener.State.DISCONNECTED;
    private static volatile String username = "";
    private static volatile String detail = "";

    private LiveStats() {}

    public static long viewers() {
        return viewers;
    }

    public static long likes() {
        return likes;
    }

    /** Waktu (ms epoch) like terakhir masuk; dipakai untuk efek denyut. */
    public static long lastLikeMs() {
        return lastLikeMs;
    }

    public static TikTokListener.State state() {
        return state;
    }

    public static String username() {
        return username;
    }

    public static String detail() {
        return detail;
    }

    public static void setViewers(long value) {
        viewers = Math.max(0L, value);
    }

    /** {@code total} > 0 → pakai total room; jika tidak, jumlahkan {@code added}. */
    public static synchronized void applyLikes(long added, long total) {
        if (total > 0L) {
            likes = Math.max(likes, total);
        } else if (added > 0L) {
            likes += added;
        }
        if (added > 0L || total > 0L) {
            lastLikeMs = System.currentTimeMillis();
        }
    }

    public static void setState(TikTokListener.State newState, String user, String newDetail) {
        state = newState;
        username = user == null ? "" : user;
        detail = newDetail == null ? "" : newDetail;
    }

    public static void reset() {
        viewers = 0L;
        likes = 0L;
        lastLikeMs = 0L;
    }
}
