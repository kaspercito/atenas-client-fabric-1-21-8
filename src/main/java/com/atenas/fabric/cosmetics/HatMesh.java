package com.atenas.fabric.cosmetics;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;

/** Malla .atm de un sombrero (mismos archivos que el cliente 1.8.9 y el mod 1.20.1). Formato: 'ATM1', grupos; por grupo nombre, flags, quads. */
public final class HatMesh {
    public static final int EMIT = 1, PULSE = 2, SPIN = 4, SOFT = 8;
    public static final double MAX_DIST_SQ = 24.0 * 24.0;
    private static final Map<String, HatMesh> CACHE = new HashMap<>();
    private static final HatMesh NONE = new HatMesh(new float[0][], new int[0]);

    private final float[][] quads;
    public final int[] flags;

    private HatMesh(float[][] quads, int[] flags) { this.quads = quads; this.flags = flags; }

    public int groups() { return quads.length; }

    public static synchronized HatMesh get(String id) {
        HatMesh m = CACHE.get(id);
        if (m == null) {
            try { m = load(id); } catch (Throwable t) { System.err.println("[AtenasClient] malla de sombrero " + id + ": " + t); m = NONE; }
            CACHE.put(id, m);
        }
        return m == NONE ? null : m;
    }

    private static HatMesh load(String id) throws Exception {
        try (InputStream raw = HatMesh.class.getResourceAsStream("/assets/" + Cosmetics.NS + "/models/hats/" + id + ".atm")) {
            if (raw == null) throw new java.io.FileNotFoundException(id + ".atm");
            DataInputStream in = new DataInputStream(new BufferedInputStream(raw, 1 << 16));
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (magic[0] != 'A' || magic[1] != 'T' || magic[2] != 'M' || magic[3] != '1') throw new Exception("formato");
            int groups = in.readInt();
            float[][] quads = new float[groups][];
            int[] flags = new int[groups];
            final float s = 0.0625f;
            for (int g = 0; g < groups; g++) {
                in.readUTF();
                flags[g] = in.readByte();
                int n = in.readInt();
                float[] f = new float[n * 32];
                for (int i = 0; i < f.length; i++) f[i] = in.readFloat();
                for (int v = 0; v < n * 4; v++) { f[v * 8] *= s; f[v * 8 + 1] *= s; f[v * 8 + 2] *= s; }
                quads[g] = f;
            }
            return new HatMesh(quads, flags);
        }
    }

    /** Emite los quads del grupo g (8 floats por vertice: x y z u v nx ny nz). */
    public void emit(int g, VertexConsumer vc, MatrixStack.Entry e, int light, float alpha) {
        float[] f = quads[g];
        int a = Math.max(0, Math.min(255, (int) (alpha * 255f)));
        for (int o = 0; o < f.length; o += 8) {
            vc.vertex(e, f[o], f[o + 1], f[o + 2]).color(255, 255, 255, a).texture(f[o + 3], f[o + 4]).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, f[o + 5], f[o + 6], f[o + 7]);
        }
    }
}
