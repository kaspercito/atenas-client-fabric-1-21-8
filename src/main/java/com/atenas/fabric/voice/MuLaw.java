package com.atenas.fabric.voice;

/** Compresion mu-law (G.711): 16 bit -> 8 bit. */
final class MuLaw {
    private MuLaw() {}
    private static final int BIAS = 0x84, CLIP = 32635;

    static byte encode(short pcm) {
        int sign = (pcm >> 8) & 0x80;
        int s = pcm;
        if (sign != 0) s = -s;
        if (s > CLIP) s = CLIP;
        s += BIAS;
        int exp = 7;
        for (int mask = 0x4000; (s & mask) == 0 && exp > 0; exp--, mask >>= 1) { }
        int mant = (s >> (exp + 3)) & 0x0F;
        return (byte) ~(sign | (exp << 4) | mant);
    }

    static short decode(byte u) {
        int v = ~u & 0xFF;
        int sign = v & 0x80, exp = (v >> 4) & 7, mant = v & 0x0F;
        int s = ((mant << 3) + BIAS) << exp;
        s -= BIAS;
        return (short) (sign != 0 ? -s : s);
    }
}
