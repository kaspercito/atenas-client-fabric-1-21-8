package com.atenas.fabric.hud;

import com.atenas.fabric.shared.Config;

import net.minecraft.client.gui.DrawContext;

/** Posicion y escala de cada panel del HUD. La escala es el ajuste "Escala" del modulo; la posicion es un desplazamiento (hp.<id>.x / .y) sobre su ancla. */
public final class HudPos {
    private HudPos() {}

    private static boolean isHud(String id) { for (String s : Settings.HUD_MODULES) if (s.equals(id)) return true; return false; }

    public static float dx(String id) { return Config.getFloat("hp." + id + ".x", 0f); }
    public static float dy(String id) { return Config.getFloat("hp." + id + ".y", 0f); }

    public static float scale(String id) {
        if (isHud(id)) return Math.max(0.5f, Math.min(2.5f, Settings.clamped(id, "scale")));
        return Math.max(0.5f, Math.min(2.5f, Config.getFloat("hp." + id + ".s", 1f)));
    }

    public static void setScale(String id, float s) {
        s = Math.max(0.5f, Math.min(2.5f, Math.round(s * 10f) / 10f));
        if (isHud(id)) Settings.setNum(id, "scale", s); else Config.putFloat("hp." + id + ".s", s);
    }

    public static void setOffset(String id, float dx, float dy) { Config.putFloat("hp." + id + ".x", dx); Config.putFloat("hp." + id + ".y", dy); }

    public static void reset() {
        for (HudPanels.Panel p : HudPanels.all()) { setOffset(p.id, 0f, 0f); setScale(p.id, 1f); }
        Config.save();
    }

    /** Ancla del panel sin desplazamiento: {x, y} de su esquina superior izquierda antes de sumar dx/dy (la usa el Editor HUD para arrastrar). */
    public static int[] anchor(HudPanels.Panel p, int sw, int w) {
        int sW = Math.max(1, (int) (w * scale(p.id)));
        int ax = p.right ? sw - sW - 6 : p.id.equals("wp") ? sw / 2 - 80 : p.ax;
        return new int[] {ax, p.ay};
    }

    /** Esquina superior izquierda del panel en pantalla: ancla (izquierda o pegado a la derecha) + desplazamiento, siempre dentro de la pantalla. */
    public static int[] pos(HudPanels.Panel p, int sw, int sh, int w, int h) {
        float sc = scale(p.id);
        int sW = Math.max(1, (int) (w * sc)), sH = Math.max(1, (int) (h * sc));
        int[] a = anchor(p, sw, w);
        int x = Math.round(a[0] + dx(p.id)), y = Math.round(a[1] + dy(p.id));
        x = Math.max(0, Math.min(Math.max(0, sw - Math.min(sW, 12)), x));
        y = Math.max(0, Math.min(Math.max(0, sh - Math.min(sH, 12)), y));
        return new int[] {x, y, sW, sH};
    }

    public static void begin(DrawContext g, int x, int y, float scale) {
        g.getMatrices().pushMatrix();
        g.getMatrices().translate((float) x, (float) y);
        g.getMatrices().scale(scale, scale);
    }

    public static void end(DrawContext g) { g.getMatrices().popMatrix(); }
}
