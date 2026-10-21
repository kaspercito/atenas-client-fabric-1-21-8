package com.atenas.fabric.hud;

import net.minecraft.client.MinecraftClient;

/** Contadores de los paneles: CPS (clics por transicion de la tecla) y velocidad horizontal suavizada (igual que 1.8.9). */
public final class HudExtras {
    private HudExtras() {}

    private static final long[] LEFT = new long[64], RIGHT = new long[64];
    private static int li, ri;
    private static boolean wasL, wasR, hasPos;
    private static double lastX, lastZ, bps;

    public static void tick(MinecraftClient mc) {
        if (mc.player == null) { hasPos = false; return; }
        boolean l = mc.options.attackKey.isPressed(), r = mc.options.useKey.isPressed();
        long now = System.currentTimeMillis();
        if (l && !wasL) LEFT[li++ & 63] = now;
        if (r && !wasR) RIGHT[ri++ & 63] = now;
        wasL = l; wasR = r;
        double x = mc.player.getX(), z = mc.player.getZ();
        if (hasPos) {
            double inst = Math.sqrt((x - lastX) * (x - lastX) + (z - lastZ) * (z - lastZ)) * 20.0;
            bps = bps * 0.6 + inst * 0.4;
        }
        lastX = x; lastZ = z; hasPos = true;
    }

    private static int cps(long[] a) {
        long now = System.currentTimeMillis();
        int n = 0;
        for (long t : a) if (t != 0 && now - t < 1000) n++;
        return n;
    }

    public static int leftCps() { return cps(LEFT); }
    public static int rightCps() { return cps(RIGHT); }
    public static double speed() { return bps; }
}
