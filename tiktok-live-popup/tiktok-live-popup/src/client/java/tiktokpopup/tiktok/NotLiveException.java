package tiktokpopup.tiktok;

import java.io.IOException;

/** User TikTok tidak sedang live (atau room tidak ditemukan). */
public final class NotLiveException extends IOException {
    public NotLiveException(String message) {
        super(message);
    }
}
