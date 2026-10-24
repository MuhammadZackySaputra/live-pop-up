package tiktokpopup.sound;

import org.lwjgl.stb.STBVorbis;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/** Decode .ogg (Vorbis) memakai STB Vorbis dari LWJGL yang sudah dibawa Minecraft. */
final class OggDecoder {
    private OggDecoder() {}

    static PcmSound decode(byte[] oggBytes) throws IOException {
        ByteBuffer input = ByteBuffer.allocateDirect(oggBytes.length);
        input.put(oggBytes).flip();
        IntBuffer channels = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asIntBuffer();
        IntBuffer sampleRate = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asIntBuffer();

        ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(input, channels, sampleRate);
        if (pcm == null) {
            throw new IOException("File .ogg tidak valid atau tidak didukung");
        }
        try {
            int channelCount = channels.get(0);
            int rate = sampleRate.get(0);
            if (channelCount < 1 || channelCount > 2 || rate <= 0) {
                throw new IOException("Format ogg tidak didukung (channels=" + channelCount + ")");
            }
            byte[] out = new byte[pcm.remaining() * 2];
            int i = 0;
            while (pcm.hasRemaining()) {
                short s = pcm.get();
                out[i++] = (byte) (s & 0xFF);
                out[i++] = (byte) ((s >> 8) & 0xFF);
            }
            return new PcmSound(rate, channelCount, out);
        } finally {
            freeNative(pcm);
        }
    }

    /** Buffer hasil stb_vorbis_decode_memory dialokasikan dengan malloc; bebaskan lewat LibCStdlib. */
    private static void freeNative(ShortBuffer buffer) {
        try {
            Class.forName("org.lwjgl.system.libc.LibCStdlib")
                .getMethod("free", java.nio.Buffer.class)
                .invoke(null, buffer);
        } catch (Throwable ignored) {
            // kebocoran kecil sekali-per-file lebih baik daripada crash
        }
    }
}
