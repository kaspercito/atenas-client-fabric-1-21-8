package com.atenas.fabric;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Random;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.Overlay;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.ResourceReload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Pantalla de carga de Atenas (la misma del mod 1.20.1: fondo violeta, rejilla en perspectiva, logo con halo y anillos, barra segmentada)
 * en lugar de la de Mojang. Envuelve el overlay de arranque: el original SIGUE corriendo (termina la recarga y se quita solo) y lo tapamos.
 * Todo va en try/catch: si algo falla se deja el original.
 */
public final class AtenasLoading extends Overlay {
    private static boolean handled;
    private static int misses;
    private static final Identifier LOGO = Identifier.of("atenasclientfabric", "dyn/loading_logo");

    private final MinecraftClient mc;
    private final Overlay orig;
    private final ResourceReload reload;
    private final long t0 = System.currentTimeMillis();
    private long doneAt = -1;
    private int lastSec = -1;
    private float shown;
    private boolean failed, logoTried, logoOk;
    private final float[] sx = new float[70], sy = new float[70], ss = new float[70], sp = new float[70];

    private AtenasLoading(MinecraftClient mc, Overlay orig, ResourceReload reload) {
        this.mc = mc; this.orig = orig; this.reload = reload;
        Random r = new Random(7);
        for (int i = 0; i < sx.length; i++) { sx[i] = r.nextFloat(); sy[i] = r.nextFloat() * 0.8f; ss[i] = 1f + r.nextInt(2); sp[i] = r.nextFloat() * 6.28f; }
    }

    /** Llamar cada tick: solo actua una vez, con el overlay de carga del arranque. */
    public static void tryInstall(MinecraftClient mc) {
        if (handled) return;
        if (!com.atenas.fabric.shared.Config.get("atenasloading", true)) { handled = true; System.out.println("[Atenas] pantalla de carga propia desactivada por config (atenasloading=false)"); return; }
        try {
            Overlay o = mc.getOverlay();
            if (o == null) { if (++misses > 1200) handled = true; return; }
            handled = true;
            if (!(o instanceof SplashOverlay) || o instanceof AtenasLoading) { System.out.println("[Atenas] pantalla de carga: overlay no compatible: " + o.getClass().getName()); return; }
            ResourceReload rr = null;
            for (Class<?> c = o.getClass(); c != null && c != Object.class && rr == null; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (ResourceReload.class.isAssignableFrom(f.getType())) { f.setAccessible(true); Object v = f.get(o); if (v instanceof ResourceReload) { rr = (ResourceReload) v; break; } }
                }
            }
            if (rr == null) { System.out.println("[Atenas] pantalla de carga: no se encontro el ResourceReload en " + o.getClass().getName()); return; }
            mc.setOverlay(new AtenasLoading(mc, o, rr));
            System.out.println("[Atenas] pantalla de carga propia instalada");
        } catch (Throwable t) { handled = true; System.out.println("[Atenas] pantalla de carga: fallo al instalar: " + t); t.printStackTrace(); }
    }

    private static long goneAt = -1;
    private static boolean guardDone;

    /**
     * Red de seguridad del arranque (se llama cada tick): si el overlay de carga ya se fue y el juego se quedo en nada o en una pantalla de mensaje
     * vacia (se ve negro, sin botones), abre el menu principal. Solo actua durante el arranque, antes de que haya menu, mundo o conexion.
     */
    public static void guard(MinecraftClient mc) {
        if (guardDone || mc.getOverlay() != null) return;
        if (mc.world != null || mc.isIntegratedServerRunning() || mc.getNetworkHandler() != null) { guardDone = true; return; }
        Screen s = mc.currentScreen;
        if (s != null && !(s instanceof MessageScreen)) { guardDone = true; return; } // ya hay menu u otra pantalla real
        long now = System.currentTimeMillis();
        if (goneAt < 0) { goneAt = now; return; }
        if (now - goneAt < 800L) return;
        guardDone = true;
        System.out.println("[Atenas] arranque: sin menu tras quitar la carga (pantalla=" + (s == null ? "null" : s.getClass().getName()) + "), se abre el menu principal");
        mc.setScreen(new TitleScreen());
    }

    /** Desde 1.21.9 el overlay original avanza en tick() (termina la recarga, abre el menu y se quita): hay que reenviarlo o se queda pegado. */
    @Override
    public void tick() { try { orig.tick(); } catch (Throwable t) { if (!failed) { failed = true; System.out.println("[Atenas] carga: tick del original fallo: " + t); } } }

    @Override
    public boolean pausesGame() { return orig.pausesGame(); }

    @Override
    public void render(DrawContext g, int mx, int my, float pt) {
        if (failed) { orig.render(g, mx, my, pt); return; }
        try {
            orig.render(g, mx, my, pt);
            long now = System.currentTimeMillis();
            if (doneAt < 0 && reload.isComplete()) { doneAt = now; System.out.println("[Atenas] recarga de recursos completa"); }
            float f = doneAt < 0 ? -1f : (now - doneAt) / 1000f;
            if (doneAt >= 0) {
                int sec = (int) f;
                if (sec > lastSec && sec <= 8) {
                    lastSec = sec;
                    System.out.println("[Atenas] carga: +" + sec + "s pantalla=" + (mc.currentScreen == null ? "null" : mc.currentScreen.getClass().getName()) + " overlay=" + (mc.getOverlay() == null ? "null" : mc.getOverlay().getClass().getSimpleName()));
                }
                if (f > 1.0f) { // nuestra animacion ya termino: se quita el overlay sin esperar al original (que a veces no se va solo)
                    System.out.println("[Atenas] carga: fin de la animacion, se quita el overlay (pantalla=" + (mc.currentScreen == null ? "null" : mc.currentScreen.getClass().getName()) + ")");
                    mc.setOverlay(null);
                    return;
                }
            }
            float target = doneAt < 0 ? MathHelper.clamp(reload.getProgress(), 0f, 0.97f) : 1f;
            shown += (target - shown) * 0.12f;
            if (shown > target) shown = target;
            float alpha = 1f;
            if (alpha > 0.003f) draw(g, alpha, (now - t0) / 1000f);
        } catch (Throwable t) {
            failed = true;
            System.out.println("[Atenas] pantalla de carga propia desactivada: " + t);
            try { mc.setOverlay(orig); } catch (Throwable ignored) { }
        }
    }

    private static int a(int rgb, float alpha) { return (MathHelper.clamp((int) (alpha * 255f), 0, 255) << 24) | (rgb & 0xFFFFFF); }

    private static int mix(int c0, int c1, float f) {
        int r = (int) (((c0 >> 16) & 255) + ((((c1 >> 16) & 255) - ((c0 >> 16) & 255)) * f));
        int gg = (int) (((c0 >> 8) & 255) + ((((c1 >> 8) & 255) - ((c0 >> 8) & 255)) * f));
        int b = (int) ((c0 & 255) + (((c1 & 255) - (c0 & 255)) * f));
        return (r << 16) | (gg << 8) | b;
    }

    private static final String[] TIPS = {"Sincronizando tu identidad", "Cargando cosm\u00e9ticos y mascotas", "Conectando con Atenas", "Preparando tu men\u00fa"};

    private static void px(DrawContext g, float x, float y, float sz, int rgb, float al) {
        if (al <= 0.003f) return;
        int ix = Math.round(x - sz / 2f), iy = Math.round(y - sz / 2f), is = Math.max(1, Math.round(sz));
        g.fill(ix, iy, ix + is, iy + is, a(rgb, al));
    }

    private static void bracket(DrawContext g, int x, int y, int len, int dx, int dy, int rgb, float al) {
        g.fill(Math.min(x, x + dx * len), y, Math.max(x, x + dx * len) + 1, y + 1, a(rgb, al));
        g.fill(x, Math.min(y, y + dy * len), x + 1, Math.max(y, y + dy * len) + 1, a(rgb, al));
    }

    private void draw(DrawContext g, float alpha, float t) {
        final int w = g.getScaledWindowWidth(), h = g.getScaledWindowHeight();
        final float k = Math.max(0.6f, Math.min(w / 427f, h / 240f));
        g.fillGradient(0, 0, w, h, a(0x04030B, alpha), a(0x120A2E, alpha));
        g.fillGradient(0, (int) (h * 0.55f), w, h, a(0x22D3EE, 0f), a(0x8B5CF6, 0.20f * alpha));
        final float horizon = h * 0.58f, flow = (t * 0.35f) % 1f;
        for (int i = 0; i < 14; i++) {
            float p = (i + flow) / 14f;
            int gy = Math.round(horizon + (h - horizon) * p * p);
            g.fill(0, gy, w, gy + 1, a(0x8B5CF6, (0.05f + 0.20f * p) * alpha));
        }
        for (int i = -10; i <= 10; i++) {
            float gx = w / 2f + i * (w / 16f);
            for (int yy = (int) horizon; yy < h; yy += 3) {
                float p = (yy - horizon) / (h - horizon);
                int xx = Math.round(w / 2f + (gx - w / 2f) * (0.15f + p * 1.1f));
                g.fill(xx, yy, xx + 1, yy + 1, a(0x22D3EE, (0.03f + 0.10f * p) * alpha));
            }
        }
        for (int i = 0; i < sx.length; i++) {
            float tw = 0.25f + 0.55f * (0.5f + 0.5f * (float) Math.sin(t * 1.4f + sp[i]));
            int ppx = (int) (sx[i] * w), ppy = (int) (sy[i] * h * 0.75f), sz = Math.max(1, Math.round(ss[i] * Math.max(1f, k * 0.6f)));
            g.fill(ppx, ppy, ppx + sz, ppy + sz, a(0xFFFFFF, tw * 0.7f * alpha));
        }
        final int m = Math.round(14 * k), bl = Math.round(22 * k);
        bracket(g, m, m, bl, 1, 1, 0x22D3EE, 0.8f * alpha);
        bracket(g, w - m - 1, m, bl, -1, 1, 0x22D3EE, 0.8f * alpha);
        bracket(g, m, h - m - 1, bl, 1, -1, 0x8B5CF6, 0.8f * alpha);
        bracket(g, w - m - 1, h - m - 1, bl, -1, -1, 0x8B5CF6, 0.8f * alpha);

        final int cx = w / 2;
        final int logoS = Math.round(60 * k), ly = Math.round(h / 2f - 98 * k);
        final float lcx = cx, lcy = ly + logoS / 2f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 2.6f);
        for (int n = 4; n >= 1; n--) {
            int gs = logoS + Math.round(n * 9 * k);
            g.fill(Math.round(lcx - gs / 2f), Math.round(lcy - gs / 2f), Math.round(lcx + gs / 2f), Math.round(lcy + gs / 2f), a(0x8B5CF6, (0.025f + 0.035f * pulse) / n * alpha));
        }
        if (!logoTried) { logoTried = true; logoOk = loadLogo(); }
        if (logoOk) g.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, cx - logoS / 2, ly, 0f, 0f, logoS, logoS, logoS, logoS, logoS, logoS, a(0xFFFFFF, alpha));

        for (int ring = 0; ring < 2; ring++) {
            final int dots = ring == 0 ? 56 : 40;
            final float r = logoS * (ring == 0 ? 0.88f : 1.08f);
            final float head = t * (ring == 0 ? 1.5f : -1.1f);
            for (int i = 0; i < dots; i++) {
                float ang = (float) (i * 6.2831853 / dots);
                float d = ((head - ang) % 6.2831853f + 6.2831853f) % 6.2831853f;
                float glow = (float) Math.exp(-d * 1.6f);
                float x = lcx + (float) Math.cos(ang) * r * 1.15f, y = lcy + (float) Math.sin(ang) * r * 0.62f;
                px(g, x, y, Math.max(1f, k * (1f + glow * 1.6f)), ring == 0 ? 0x22D3EE : 0xA78BFA, (0.18f + 0.82f * glow) * alpha);
            }
        }

        float ty = ly + logoS + 13.9f * k;
        ty += TextTex.draw(g, mc, "A T E N A S", 21.4f * k, 0xE9E3FF, cx, ty, true, alpha) + 1.1f * k;
        TextTex.draw(g, mc, "C  L  I  E  N  T   \u00b7   1 . 2 1 . 1 1", 7.5f * k, 0x22D3EE, cx, ty, true, alpha);

        final int bw = Math.round(260 * k), bh = Math.max(5, Math.round(7 * k));
        final int bx = cx - bw / 2, by = Math.round(h / 2f + 52 * k);
        final int seg = Math.max(3, Math.round(5 * k)), gap = Math.max(1, Math.round(1.5f * k));
        final int n = Math.max(10, bw / (seg + gap));
        final float prog = MathHelper.clamp(shown, 0f, 1f);
        final int filled = Math.round(n * prog);
        g.fill(bx - 3, by - 3, bx + bw + 3, by + bh + 3, a(0x0B0E1C, 0.85f * alpha));
        bracket(g, bx - 4, by - 4, 8, 1, 1, 0x22D3EE, alpha);
        bracket(g, bx + bw + 3, by - 4, 8, -1, 1, 0x22D3EE, alpha);
        bracket(g, bx - 4, by + bh + 3, 8, 1, -1, 0x8B5CF6, alpha);
        bracket(g, bx + bw + 3, by + bh + 3, 8, -1, -1, 0x8B5CF6, alpha);
        final float scan = (t * n * 0.9f) % (n + 8);
        for (int i = 0; i < n; i++) {
            int sxp = bx + Math.round(i * (bw / (float) n));
            int wseg = Math.max(2, Math.round(bw / (float) n) - gap);
            if (i < filled) {
                float pp = i / (float) n;
                int c = pp < 0.5f ? mix(0x22D3EE, 0x8B5CF6, pp / 0.5f) : mix(0x8B5CF6, 0xEC4899, (pp - 0.5f) / 0.5f);
                float sh = Math.max(0f, 1f - Math.abs(i - scan) / 3f);
                g.fill(sxp, by, sxp + wseg, by + bh, a(mix(c, 0xFFFFFF, sh * 0.7f), alpha));
                g.fill(sxp, by + bh, sxp + wseg, by + bh + Math.max(1, Math.round(2 * k)), a(c, 0.18f * alpha));
            } else {
                g.fill(sxp, by, sxp + wseg, by + bh, a(0x1B1A33, alpha));
            }
        }
        if (filled < n) {
            int sxp = bx + Math.round(filled * (bw / (float) n));
            g.fill(sxp, by, sxp + Math.max(2, Math.round(bw / (float) n) - gap), by + bh, a(0xFFFFFF, (0.35f + 0.5f * pulse) * alpha));
        }
        g.fillGradient(bx, by - Math.round(6 * k), bx + Math.round(bw * prog), by, a(0x8B5CF6, 0f), a(0x8B5CF6, 0.16f * alpha));

        String status = doneAt < 0 && prog < 0.97f ? TIPS[(int) (t / 2.2f) % TIPS.length] : "Abriendo el men\u00fa";
        int dots = 1 + (int) (t * 2.5f) % 3;
        TextTex.draw(g, mc, status + "...".substring(0, dots), 7.5f * k, 0x9B97B8, bx + 48f * k, by + bh + 8.6f * k, false, alpha);
        TextTex.draw(g, mc, Math.round(prog * 100f) + "%", 12.9f * k, 0xF1EEFC, bx + bw - 13.9f * k, by + bh + 5.4f * k, true, alpha);
        TextTex.draw(g, mc, "ATENAS  \u00b7  iniciando", 5.9f * k, 0x6D6A8F, cx, h - m - 7.5f * k, false, alpha);
    }

    private boolean loadLogo() {
        try (InputStream in = AtenasLoading.class.getResourceAsStream("/assets/atenasclientfabric/textures/gui/logo.png")) {
            if (in == null) return false;
            NativeImage img = NativeImage.read(in);
            mc.getTextureManager().registerTexture(LOGO, new NativeImageBackedTexture(() -> "atenas loading logo", img));
            return true;
        } catch (Throwable t) { return false; }
    }
}
