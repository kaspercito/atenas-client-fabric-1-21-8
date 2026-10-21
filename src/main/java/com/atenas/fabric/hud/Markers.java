package com.atenas.fabric.hud;

import java.util.List;
import java.util.Locale;

import com.atenas.fabric.shared.Config;
import com.atenas.fabric.shared.Waypoint;
import com.atenas.fabric.shared.WaypointStore;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;

/**
 * Marcadores en pantalla de waypoints y companeros (a traves de paredes y a cualquier distancia), con rayo vertical,
 * flechas en el borde y atenuado cerca de la mira, como en 1.8.9. Sin tocar el render del mundo: se proyecta cada punto a la pantalla.
 */
public final class Markers {
    private Markers() {}

    /** Colores de waypoint (el indice se guarda en waypoints.json, compartido con 1.8.9). */
    public static final int[] WP_COLORS = {0xFFF87171, 0xFFFB923C, 0xFFFBBF24, 0xFF34D399, 0xFF22D3EE, 0xFF60A5FA, 0xFFA78BFA, 0xFFEC4899, 0xFFF1EEFC};

    public static int argb(Waypoint w) { return WP_COLORS[Math.abs(w.color) % WP_COLORS.length]; }

    /** Camara: origen, base (adelante, derecha, arriba) y distancia focal en pixeles de GUI. */
    private static final class Cam {
        double x, y, z, fx, fy, fz, rx, rz, ux, uy, uz, f; int W, H;
        /** {pantallaX, pantallaY, profundidad}. Con profundidad <= 0.05 el punto esta detras (x,y son la direccion en pantalla, no una posicion). */
        double[] project(double px, double py, double pz) {
            double dx = px - x, dy = py - y, dz = pz - z;
            double d = dx * fx + dy * fy + dz * fz, side = dx * rx + dz * rz, up = dx * ux + dy * uy + dz * uz;
            if (d > 0.05) return new double[] {W / 2.0 + side / d * f, H / 2.0 - up / d * f, d};
            return new double[] {side, -up, d};
        }
    }

    private static Cam cam(MinecraftClient mc, DrawContext g, float pt) {
        Cam c = new Cam();
        var cp = mc.player.getCameraPosVec(pt);
        c.x = cp.x; c.y = cp.y; c.z = cp.z;
        double yaw = Math.toRadians(mc.player.getYaw(pt)), pitch = Math.toRadians(mc.player.getPitch(pt));
        c.fx = -Math.sin(yaw) * Math.cos(pitch); c.fy = -Math.sin(pitch); c.fz = Math.cos(yaw) * Math.cos(pitch);
        c.rx = -Math.cos(yaw); c.rz = -Math.sin(yaw);
        c.ux = -c.rz * c.fy; c.uy = c.rz * c.fx - c.rx * c.fz; c.uz = c.rx * c.fy;
        c.W = g.getScaledWindowWidth(); c.H = g.getScaledWindowHeight();
        c.f = (c.H / 2.0) / Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0) * Mods.zoomMul();
        return c;
    }

    private static void diamond(DrawContext g, int r, int color) {
        for (int dy = -r; dy <= r; dy++) { int half = r - Math.abs(dy); g.fill(-half, dy, half + 1, dy + 1, color); }
    }

    private static void arrow(DrawContext g, float x, float y, double ang, int size, int color) {
        g.getMatrices().pushMatrix();
        g.getMatrices().translate(x, y);
        g.getMatrices().rotate((float) ang);
        for (int i = 0; i < size; i++) { int h = (size - i) * 7 / 10; g.fill(i - size / 2, -h, i - size / 2 + 1, h + 1, color); }
        g.getMatrices().popMatrix();
    }

    private static int alpha(int argb, int a) { return (Math.max(0, Math.min(255, a)) << 24) | (argb & 0xFFFFFF); }

    private static String fmt(double d) { return d >= 1000 ? String.format(Locale.ROOT, "%.1fkm", d / 1000.0) : Math.round(d) + "m"; }

    /** Punto con marcador: rombo + nombre arriba + texto de abajo (o flecha en el borde si esta fuera de pantalla). */
    private static void mark(DrawContext g, MinecraftClient mc, Cam c, double wx, double wy, double wz, String name, String sub, int rgb, float k,
                             boolean beam, boolean edge, boolean fade, double base) {
        double[] p = c.project(wx, wy, wz);
        boolean on = p[2] > 0.05 && p[0] >= 0 && p[0] <= c.W && p[1] >= 0 && p[1] <= c.H;
        if (on) {
            float alpha = 1f;
            if (fade) {
                double cdx = p[0] - c.W / 2.0, cdy = p[1] - c.H / 2.0;
                alpha = (float) Math.max(0.35, Math.min(1.0, Math.sqrt(cdx * cdx + cdy * cdy) / 70.0));
            }
            int a = (int) (alpha * 255);
            if (beam) {
                double[] b = c.project(wx, base, wz), t = c.project(wx, base + 256, wz);
                if (b[2] > 0.05 && t[2] > 0.05) {
                    int bx = (int) Math.round(b[0]), ty = (int) Math.max(-20, Math.min(c.H + 20, t[1])), by = (int) Math.max(-20, Math.min(c.H + 20, b[1]));
                    g.fillGradient(bx, Math.min(ty, by), bx + 1, Math.max(ty, by), alpha(rgb, 0), alpha(rgb, a * 2 / 3));
                }
            }
            g.getMatrices().pushMatrix();
            g.getMatrices().translate((float) p[0], (float) p[1]);
            g.getMatrices().scale(k, k);
            diamond(g, 6, alpha(0x0A0814, a * 3 / 4));
            diamond(g, 5, alpha(rgb, a));
            diamond(g, 2, alpha(0xFFFFFF, a));
            g.drawText(mc.textRenderer, name, -mc.textRenderer.getWidth(name) / 2, -19, alpha(0xFFFFFF, a) , true);
            if (sub != null) {
                g.getMatrices().pushMatrix();
                g.getMatrices().scale(0.8f, 0.8f);
                g.drawText(mc.textRenderer, sub, -mc.textRenderer.getWidth(sub) / 2, 9, alpha(rgb, a), true);
                g.getMatrices().popMatrix();
            }
            g.getMatrices().popMatrix();
        } else if (edge) {
            double dx = p[2] > 0.05 ? p[0] - c.W / 2.0 : p[0], dy = p[2] > 0.05 ? p[1] - c.H / 2.0 : p[1];
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len < 1e-6) { dx = 0; dy = 1; len = 1; }
            dx /= len; dy /= len;
            double m = 16, tx = Math.abs(dx) < 1e-6 ? 1e9 : (c.W / 2.0 - m) / Math.abs(dx), ty = Math.abs(dy) < 1e-6 ? 1e9 : (c.H / 2.0 - m) / Math.abs(dy);
            double t = Math.min(tx, ty);
            float ex = (float) (c.W / 2.0 + dx * t), ey = (float) (c.H / 2.0 + dy * t);
            double ang = Math.atan2(dy, dx);
            arrow(g, ex, ey, ang, (int) (8 * k + 2), 0xC00A0814);
            arrow(g, ex, ey, ang, (int) (7 * k), 0xFF000000 | rgb);
            String t2 = sub != null ? name + " \u00b7 " + sub : name;
            g.getMatrices().pushMatrix();
            g.getMatrices().translate((float) (ex - dx * 15), (float) (ey - dy * 15));
            g.getMatrices().scale(0.8f, 0.8f);
            g.drawText(mc.textRenderer, t2, -mc.textRenderer.getWidth(t2) / 2, -4, 0xFF000000 | rgb, true);
            g.getMatrices().popMatrix();
        }
    }

    public static void draw(MinecraftClient mc, DrawContext g, float pt) {
        if (mc.player == null || mc.world == null || mc.options.hudHidden || !Config.get("hud", true)) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof ChatScreen)) return;
        boolean wp = Modules.on("waypoints"), tm = Modules.on("team") && Settings.b("team", "markers");
        if (!wp && !tm) return;
        Cam c = cam(mc, g, pt);
        if (wp) {
            List<Waypoint> list = WaypointStore.current();
            int dim = WaypointStore.currentDim();
            double max = Settings.n("waypoints", "maxdist"), near = Settings.n("waypoints", "hidenear");
            boolean dist = Settings.b("waypoints", "dist"), beam = Settings.b("waypoints", "beam"), edge = Settings.b("waypoints", "edge"), fade = Settings.b("waypoints", "fade");
            float k = Settings.clamped("waypoints", "size");
            for (Waypoint w : list) {
                if (!w.visible || w.dim != dim) continue;
                double dx = w.x - c.x, dy = w.y + 1.0 - c.y, dz = w.z - c.z, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (max > 0 && d > max) continue;
                if (near > 0 && d < near) continue;
                mark(g, mc, c, w.x, w.y + 1.0, w.z, w.name, dist ? fmt(d) : null, argb(w) & 0xFFFFFF, k, beam, edge, fade, w.y);
            }
        }
        if (tm) {
            double max = Settings.n("team", "maxdist");
            float k = Settings.clamped("team", "marksize");
            int rgb = Settings.color("team", "color") & 0xFFFFFF;
            boolean hp = Settings.b("team", "hp"), dist = Settings.b("team", "dist");
            for (TeamView.E e : TeamView.list()) {
                double dx = e.x - c.x, dy = e.y + 2.2 - c.y, dz = e.z - c.z, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (max > 0 && d > max) continue;
                StringBuilder sb = new StringBuilder();
                if (dist) sb.append(fmt(d));
                if (hp) sb.append(sb.length() > 0 ? "  " : "").append(String.format(Locale.ROOT, "%.0f\u2764", e.hp / 2f));
                mark(g, mc, c, e.x, e.y + 2.2, e.z, e.name, sb.length() > 0 ? sb.toString() : null, rgb, k, false, true, false, e.y);
            }
        }
    }
}
