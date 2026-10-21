package com.atenas.fabric.hud;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Resalte del bloque que miras: color, grosor y relleno salen de los ajustes del modulo (Settings "blockoverlay": color, width, fill, fillalpha;
 * mismos valores por defecto que 1.8.9). Sin tocar el render del mundo: se proyectan las 8 esquinas del bloque a la pantalla y se dibujan sus
 * caras visibles con el DrawContext. Solo en primera persona. El contorno negro de vanilla queda debajo.
 */
public final class BlockOverlay {
    private BlockOverlay() {}

    private static final int[][] FACES = {{0, 1, 2, 3}, {4, 5, 6, 7}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 3, 7, 4}, {1, 2, 6, 5}};
    private static final double[][] NORMAL = {{0, 0, -1}, {0, 0, 1}, {0, -1, 0}, {0, 1, 0}, {-1, 0, 0}, {1, 0, 0}};

    public static void draw(MinecraftClient mc, DrawContext g, float pt) {
        if (!Modules.on("blockoverlay") || mc.world == null || mc.player == null) return;
        if (mc.options.getPerspective() != Perspective.FIRST_PERSON) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();
        BlockState st = mc.world.getBlockState(pos);
        if (st.isAir()) return;
        var shape = st.getOutlineShape(mc.world, pos);
        if (shape.isEmpty()) return;
        Box b = shape.getBoundingBox().offset(pos).expand(0.002);

        Vec3d cam = mc.player.getCameraPosVec(pt);
        double yaw = Math.toRadians(mc.player.getYaw(pt)), pitch = Math.toRadians(mc.player.getPitch(pt));
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw), ry = 0, rz = -Math.sin(yaw);
        double ux = ry * fz - rz * fy, uy = rz * fx - rx * fz, uz = rx * fy - ry * fx;
        int W = g.getScaledWindowWidth(), H = g.getScaledWindowHeight();
        double f = (H / 2.0) / Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);

        double[][] c = {{b.minX, b.minY, b.minZ}, {b.maxX, b.minY, b.minZ}, {b.maxX, b.maxY, b.minZ}, {b.minX, b.maxY, b.minZ},
                        {b.minX, b.minY, b.maxZ}, {b.maxX, b.minY, b.maxZ}, {b.maxX, b.maxY, b.maxZ}, {b.minX, b.maxY, b.maxZ}};
        double[][] sp = new double[8][];
        for (int i = 0; i < 8; i++) {
            double dx = c[i][0] - cam.x, dy = c[i][1] - cam.y, dz = c[i][2] - cam.z;
            double d = dx * fx + dy * fy + dz * fz;
            if (d < 0.05) { sp[i] = null; continue; }
            sp[i] = new double[] {W / 2.0 + (dx * rx + dy * ry + dz * rz) / d * f, H / 2.0 - (dx * ux + dy * uy + dz * uz) / d * f};
        }
        double[] mid = {(b.minX + b.maxX) / 2, (b.minY + b.maxY) / 2, (b.minZ + b.maxZ) / 2};
        double[] half = {(b.maxX - b.minX) / 2, (b.maxY - b.minY) / 2, (b.maxZ - b.minZ) / 2};
        boolean[] vis = new boolean[6];
        for (int i = 0; i < 6; i++) {
            double px = mid[0] + NORMAL[i][0] * half[0] - cam.x, py = mid[1] + NORMAL[i][1] * half[1] - cam.y, pz = mid[2] + NORMAL[i][2] * half[2] - cam.z;
            vis[i] = px * NORMAL[i][0] + py * NORMAL[i][1] + pz * NORMAL[i][2] < 0;
        }
        int rgb = Settings.color("blockoverlay", "color") & 0xFFFFFF;
        int thick = Math.max(1, Math.round(Settings.clamped("blockoverlay", "width")));
        boolean fill = Settings.b("blockoverlay", "fill");
        int fillCol = ((int) (Math.max(0.05f, Math.min(0.6f, Settings.clamped("blockoverlay", "fillalpha"))) * 255f) << 24) | rgb;
        int lineCol = 0xFF000000 | rgb;
        for (int i = 0; i < 6; i++) {
            if (!vis[i]) continue;
            int[] q = FACES[i];
            boolean ok = true;
            for (int k : q) if (sp[k] == null) ok = false;
            if (!ok) continue;
            if (fill) fillQuad(g, sp[q[0]], sp[q[1]], sp[q[2]], sp[q[3]], W, H, fillCol);
            for (int k = 0; k < 4; k++) line(g, sp[q[k]], sp[q[(k + 1) % 4]], lineCol, thick);
        }
    }

    /** Linea de Bresenham con grosor: cada punto es un cuadrado de "t" x "t" centrado en la linea. */
    private static void line(DrawContext g, double[] a, double[] b, int col, int t) {
        int x0 = (int) Math.round(a[0]), y0 = (int) Math.round(a[1]), x1 = (int) Math.round(b[0]), y1 = (int) Math.round(b[1]);
        if (Math.abs(x0) > 4000 || Math.abs(y0) > 4000 || Math.abs(x1) > 4000 || Math.abs(y1) > 4000) return;
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx - dy;
        for (int n = 0; n < 3000; n++) {
            g.fill(x0 - t / 2, y0 - t / 2, x0 - t / 2 + t, y0 - t / 2 + t, col);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx) { err += dx; y0 += sy; }
        }
    }

    /** Relleno de un cuadrilatero convexo por filas. */
    private static void fillQuad(DrawContext g, double[] p0, double[] p1, double[] p2, double[] p3, int W, int H, int col) {
        double[][] p = {p0, p1, p2, p3};
        double minY = Math.min(Math.min(p0[1], p1[1]), Math.min(p2[1], p3[1])), maxY = Math.max(Math.max(p0[1], p1[1]), Math.max(p2[1], p3[1]));
        int y0 = Math.max(0, (int) Math.floor(minY)), y1 = Math.min(H, (int) Math.ceil(maxY));
        for (int y = y0; y < y1; y++) {
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            double yc = y + 0.5;
            for (int i = 0; i < 4; i++) {
                double[] a = p[i], b = p[(i + 1) % 4];
                if ((a[1] <= yc && b[1] > yc) || (b[1] <= yc && a[1] > yc)) {
                    double x = a[0] + (yc - a[1]) / (b[1] - a[1]) * (b[0] - a[0]);
                    lo = Math.min(lo, x); hi = Math.max(hi, x);
                }
            }
            if (hi >= lo) g.fill(Math.max(0, (int) lo), y, Math.min(W, (int) hi + 1), y + 1, col);
        }
    }
}
