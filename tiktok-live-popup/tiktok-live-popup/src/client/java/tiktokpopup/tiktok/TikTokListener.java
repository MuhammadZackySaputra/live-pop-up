package tiktokpopup.tiktok;

/** Event dari live TikTok. Semua method dipanggil dari thread jaringan, bukan thread render. */
public interface TikTokListener {
    enum State { CONNECTING, WAITING_FOR_LIVE, CONNECTED, LIVE_ENDED, ERROR, DISCONNECTED }

    void onChat(String uniqueId, String displayName, String text, TikTokRoles roles);

    void onGift(String uniqueId, String displayName, String giftName, int count);

    void onFollow(String uniqueId, String displayName);

    /** Fans club / subscribe baru. */
    void onFans(String uniqueId, String displayName);

    /** {@code added} like pada tap ini; {@code total} total like room (0 jika tidak diketahui). */
    void onLikes(String uniqueId, String displayName, int added, long total);

    void onViewers(long viewers);

    void onState(State state, String detail);
}
