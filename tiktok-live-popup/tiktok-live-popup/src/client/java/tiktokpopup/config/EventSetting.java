package tiktokpopup.config;

/** Pengaturan satu jenis notifikasi (chat / follow / gift / fans). */
public final class EventSetting {
    /** Tampilkan di popup kiri bawah. */
    public boolean popup = true;
    /** Mainkan sound. */
    public boolean sound = true;
    /**
     * "default" = bunyi bawaan Minecraft, atau nama file di folder sounds
     * (mis. "follow.wav", "gift.ogg"), atau path absolut.
     */
    public String soundFile = "default";
    /** 0..100 */
    public int volume = 80;

    public EventSetting() {}

    public EventSetting(boolean popup, boolean sound) {
        this.popup = popup;
        this.sound = sound;
    }
}
