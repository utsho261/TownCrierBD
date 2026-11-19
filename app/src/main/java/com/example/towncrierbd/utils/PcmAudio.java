package com.example.towncrierbd.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Shared, normalized PCM conversion for recordings and imported audio. */
public final class PcmAudio {
    private PcmAudio() {}
    public static void validateFormat(int rate, int channels) {
        if (rate < 8000 || rate > 192000 || channels < 1 || channels > 8)
            throw new IllegalArgumentException("Unsupported sample rate or channel count");
    }
    public static float[] toMono(ByteBuffer source, int channels, boolean floatingPoint) {
        if (channels < 1 || channels > 8) throw new IllegalArgumentException("Unsupported channel count");
        ByteBuffer data = source.slice().order(ByteOrder.LITTLE_ENDIAN);
        int frameBytes = channels * (floatingPoint ? 4 : 2);
        if (data.remaining() % frameBytes != 0) throw new IllegalArgumentException("Incomplete PCM frame");
        float[] mono = new float[data.remaining() / frameBytes];
        for (int frame = 0; frame < mono.length; frame++) {
            double total = 0;
            for (int ch = 0; ch < channels; ch++) {
                float value = floatingPoint ? data.getFloat() : data.getShort() / 32768f;
                if (Float.isNaN(value) || Float.isInfinite(value)) value = 0;
                total += Math.max(-1, Math.min(1, value));
            }
            mono[frame] = (float)(total / channels);
        }
        return mono;
    }
}
