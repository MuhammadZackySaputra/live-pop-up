package tiktokpopup.tiktok;

/** Peran penulis chat di TikTok. */
public record TikTokRoles(boolean follower, boolean subscriber, boolean moderator, boolean broadcaster) {
    public static final TikTokRoles NONE = new TikTokRoles(false, false, false, false);
}
