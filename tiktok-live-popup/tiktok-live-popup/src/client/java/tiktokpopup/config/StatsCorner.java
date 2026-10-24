package tiktokpopup.config;

/** Pojok layar untuk panel viewer/likes. */
public enum StatsCorner {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

    public StatsCorner next() {
        StatsCorner[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
