package tiktokpopup.sound;

/** Audio PCM 16-bit little-endian signed yang sudah didecode. */
public record PcmSound(float sampleRate, int channels, byte[] data) {

    /** Salinan dengan amplitudo dikali {@code gain} (0..1+), dipotong agar tidak clipping. */
    public byte[] scaled(float gain) {
        byte[] out = new byte[data.length];
        for (int i = 0; i + 1 < data.length; i += 2) {
            int sample = (short) ((data[i] & 0xFF) | (data[i + 1] << 8));
            int scaled = Math.round(sample * gain);
            if (scaled > Short.MAX_VALUE) scaled = Short.MAX_VALUE;
            if (scaled < Short.MIN_VALUE) scaled = Short.MIN_VALUE;
            out[i] = (byte) (scaled & 0xFF);
            out[i + 1] = (byte) ((scaled >> 8) & 0xFF);
        }
        return out;
    }
}
