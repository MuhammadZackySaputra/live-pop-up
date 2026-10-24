package tiktokpopup.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import tiktokpopup.Log;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Konfigurasi mod (JSON di folder config). Field public agar mudah di-serialize Gson. */
public final class PopupConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static PopupConfig instance;

    // --- Koneksi ---
    public String username = "";
    /** Hubungkan otomatis saat masuk dunia jika username sudah diisi. */
    public boolean autoConnect = false;
    /** Log event TikTok yang tidak dikenali ke latest.log. */
    public boolean debugEvents = false;

    // --- Popup chat kiri bawah ---
    public boolean popupEnabled = true;
    public int maxVisible = 6;
    public int displaySeconds = 10;
    public int popupWidth = 220;
    public float scale = 1.0f;
    public int marginLeft = 6;
    public int marginBottom = 48;
    public boolean showBackground = true;

    // --- Statistik realtime ---
    public boolean statsEnabled = true;
    public StatsCorner statsCorner = StatsCorner.TOP_LEFT;

    // --- Notifikasi & sound ---
    public EventSetting chat = new EventSetting(true, false);
    public EventSetting follow = new EventSetting(true, true);
    public EventSetting gift = new EventSetting(true, true);
    public EventSetting fans = new EventSetting(true, true);

    private PopupConfig() {}

    public static synchronized PopupConfig get() {
        if (instance == null) {
            instance = load(Paths.configFile());
        }
        return instance;
    }

    static PopupConfig load(Path file) {
        PopupConfig loaded = null;
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(reader, PopupConfig.class);
            } catch (Exception e) {
                Log.LOGGER.warn("Gagal membaca config, memakai default: {}", e.getMessage());
            }
        }
        if (loaded == null) {
            loaded = new PopupConfig();
        }
        loaded.sanitize();
        return loaded;
    }

    public void save() {
        save(Paths.configFile());
    }

    void save(Path file) {
        sanitize();
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            Log.LOGGER.warn("Gagal menyimpan config: {}", e.getMessage());
        }
    }

    /** Perbaiki nilai yang hilang / di luar batas (mis. config diedit manual). */
    void sanitize() {
        if (username == null) username = "";
        username = username.trim().replace("@", "");
        if (statsCorner == null) statsCorner = StatsCorner.TOP_LEFT;
        if (chat == null) chat = new EventSetting(true, false);
        if (follow == null) follow = new EventSetting(true, true);
        if (gift == null) gift = new EventSetting(true, true);
        if (fans == null) fans = new EventSetting(true, true);
        for (EventSetting s : new EventSetting[] {chat, follow, gift, fans}) {
            if (s.soundFile == null || s.soundFile.isBlank()) s.soundFile = "default";
            s.volume = Math.max(0, Math.min(100, s.volume));
        }
        maxVisible = Math.max(1, Math.min(15, maxVisible));
        displaySeconds = Math.max(2, Math.min(120, displaySeconds));
        popupWidth = Math.max(120, Math.min(500, popupWidth));
        if (!(scale >= 0.5f && scale <= 3.0f)) scale = 1.0f;
        marginLeft = Math.max(0, Math.min(400, marginLeft));
        marginBottom = Math.max(0, Math.min(600, marginBottom));
    }
}
