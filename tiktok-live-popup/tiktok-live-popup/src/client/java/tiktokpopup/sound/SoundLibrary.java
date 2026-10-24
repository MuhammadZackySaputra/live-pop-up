package tiktokpopup.sound;

import tiktokpopup.config.Paths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Daftar file sound yang bisa dipilih di pengaturan. */
public final class SoundLibrary {
    public static final String DEFAULT = "default";

    private SoundLibrary() {}

    /** "default" + semua .wav/.ogg di folder sounds, urut abjad. */
    public static List<String> options() {
        List<String> names = new ArrayList<>();
        try (Stream<Path> stream = Files.list(Paths.soundsDir())) {
            stream.filter(Files::isRegularFile)
                .map(p -> p.getFileName().toString())
                .filter(SoundLibrary::isSupported)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(names::add);
        } catch (IOException ignored) {
            // folder belum ada / tidak terbaca → hanya default
        }
        names.add(0, DEFAULT);
        return names;
    }

    public static boolean isSupported(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".wav") || lower.endsWith(".ogg");
    }

    /** Nama file relatif → path di folder sounds; path absolut dipakai apa adanya. */
    public static Path resolve(String name) {
        Path given = java.nio.file.Paths.get(name);
        return given.isAbsolute() ? given : Paths.soundsDir().resolve(name).normalize();
    }
}
