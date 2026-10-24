package tiktokpopup.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import tiktokpopup.Log;
import tiktokpopup.config.EventSetting;
import tiktokpopup.core.Kind;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Memutar sound notifikasi.
 * <ul>
 *   <li>"default": bunyi bawaan Minecraft (note block) dengan nada berbeda tiap jenis.</li>
 *   <li>file .wav / .ogg: didecode sekali, di-cache, lalu diputar lewat Java Sound
 *       (tidak bergantung pada resource pack).</li>
 * </ul>
 * Aman dipanggil dari thread mana pun.
 */
public final class SoundPlayer {
    private static final int MAX_CONCURRENT = 8;
    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "TikTokPopup-Sound");
        t.setDaemon(true);
        return t;
    });
    private static final Map<String, PcmSound> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger ACTIVE = new AtomicInteger();

    private SoundPlayer() {}

    /** Mainkan jika sound untuk jenis ini aktif. */
    public static void play(Kind kind, EventSetting setting) {
        if (setting.sound) {
            playNow(kind, setting);
        }
    }

    /** Mainkan tanpa memeriksa toggle (tombol Test). */
    public static void playNow(Kind kind, EventSetting setting) {
        float volume = Math.max(0, Math.min(100, setting.volume)) / 100f;
        if (volume <= 0f) {
            return;
        }
        String file = setting.soundFile == null ? SoundLibrary.DEFAULT : setting.soundFile;
        if (SoundLibrary.DEFAULT.equalsIgnoreCase(file)) {
            playBuiltin(kind, volume);
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                playFile(file, volume);
            } catch (Throwable e) {
                if (REPORTED.add(file)) {
                    Log.LOGGER.warn("Gagal memutar sound '{}': {}", file, e.toString());
                }
                // jatuh ke bunyi bawaan agar notifikasi tidak senyap
                playBuiltin(kind, volume);
            }
        });
    }

    private static void playBuiltin(Kind kind, float volume) {
        float pitch = switch (kind) {
            case CHAT -> 1.25f;
            case FOLLOW -> 1.6f;
            case GIFT -> 2.0f;
            case FANS -> 1.0f;
        };
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), volume, pitch);
            }
        });
    }

    private static void playFile(String name, float volume) throws Exception {
        Path path = SoundLibrary.resolve(name);
        if (!Files.isRegularFile(path)) {
            throw new IOException("file tidak ditemukan: " + path);
        }
        if (ACTIVE.get() >= MAX_CONCURRENT) {
            return;
        }
        String key = path + "|" + Files.getLastModifiedTime(path).toMillis() + "|" + Files.size(path);
        PcmSound sound = CACHE.get(key);
        if (sound == null) {
            sound = decode(path);
            if (CACHE.size() > 32) {
                CACHE.clear();
            }
            CACHE.put(key, sound);
        }

        byte[] data = sound.scaled(volume);
        AudioFormat format = new AudioFormat(sound.sampleRate(), 16, sound.channels(), true, false);
        Clip clip = AudioSystem.getClip();
        clip.open(format, data, 0, data.length);
        ACTIVE.incrementAndGet();
        clip.addLineListener(event -> {
            if (event.getType() == LineEvent.Type.STOP) {
                clip.close();
                ACTIVE.decrementAndGet();
            }
        });
        clip.start();
    }

    private static PcmSound decode(Path path) throws Exception {
        if (Files.size(path) > MAX_FILE_BYTES) {
            throw new IOException("file terlalu besar (maks 20 MB)");
        }
        String lower = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".ogg")) {
            return OggDecoder.decode(Files.readAllBytes(path));
        }
        if (!lower.endsWith(".wav")) {
            throw new IOException("format tidak didukung (pakai .wav atau .ogg)");
        }
        try (AudioInputStream raw = AudioSystem.getAudioInputStream(path.toFile())) {
            AudioFormat src = raw.getFormat();
            int channels = Math.min(2, Math.max(1, src.getChannels()));
            AudioFormat target = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED, src.getSampleRate(), 16, channels, channels * 2,
                src.getSampleRate(), false
            );
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(target, raw)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = pcm.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
                return new PcmSound(src.getSampleRate(), channels, out.toByteArray());
            }
        }
    }
}
