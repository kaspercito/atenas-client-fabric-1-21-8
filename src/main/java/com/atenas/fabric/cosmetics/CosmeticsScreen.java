package com.atenas.fabric.cosmetics;

import java.util.ArrayList;
import java.util.List;

import com.atenas.fabric.gui.Ui;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Cosmeticos con la misma ventana que 1.8.9: panel con esquinas cortadas, titulo + aviso, pestanas (Capas / Alas / Sombreros / Mascotas),
 * cuadricula de tarjetas con icono y "Ninguno" primero, vista previa a la derecha y botones "Animadas" y "Listo".
 * Los privados (como Catalina) solo aparecen si tu nick esta en su lista del servicio.
 */
public final class CosmeticsScreen extends Screen {
    private static final String[] TYPES = {"cape", "wings", "hat", "pet"};
    private static final String[] LABELS = {"Capas", "Alas", "Sombreros", "Mascotas"};
    private static final int CW = 62, CH = 58, GAP = 6, PREVIEW_W = 118;
    private static int tab;
    private final Screen parent;
    private int scroll, px, py, pw, ph, gx, gy, gw, gh, tabW;

    public CosmeticsScreen(Screen parent) { super(Text.literal("Cosmeticos")); this.parent = parent; }

    @Override
    protected void init() {
        CosmeticSync.refreshNow(); // un cambio hecho en 1.8.9 o 1.20.1 aparece al abrir
        pw = Math.min(width - 16, 440); ph = Math.min(height - 16, 270);
        px = (width - pw) / 2; py = (height - ph) / 2;
        gx = px + 8; gy = py + 52; gw = pw - PREVIEW_W - 24; gh = ph - 60;
        tabW = Math.min(70, (gw - 8) / TYPES.length - 4);
    }

    private static final java.util.Map<Identifier, int[]> ICON_SIZES = new java.util.HashMap<>();

    /** Medidas reales del PNG (cabecera IHDR). */
    private int[] iconSize(Identifier id) {
        int[] r = ICON_SIZES.get(id);
        if (r != null) return r;
        r = new int[] {32, 32};
        try {
            var res = client.getResourceManager().getResource(id);
            if (res.isPresent()) {
                try (java.io.InputStream in = res.get().getInputStream()) {
                    byte[] h = in.readNBytes(24);
                    if (h.length == 24) {
                        int w = ((h[16] & 255) << 24) | ((h[17] & 255) << 16) | ((h[18] & 255) << 8) | (h[19] & 255);
                        int hh = ((h[20] & 255) << 24) | ((h[21] & 255) << 16) | ((h[22] & 255) << 8) | (h[23] & 255);
                        if (w > 0 && hh > 0) r = new int[] {w, hh};
                    }
                }
            }
        } catch (Throwable ignored) { }
        ICON_SIZES.put(id, r);
        return r;
    }

    private int cols() { return Math.max(1, (gw + GAP) / (CW + GAP)); }

    /** null = "Ninguno". */
    private List<Cosmetics.Item> entries() {
        List<Cosmetics.Item> l = new ArrayList<>();
        l.add(null);
        for (Cosmetics.Item i : Cosmetics.listOf(TYPES[tab])) if (!i.locked) l.add(i);
        return l;
    }

    private int maxScroll() { int rows = (entries().size() + cols() - 1) / cols(); return Math.max(0, rows * (CH + GAP) - gh); }
    private static boolean in(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    @Override
    public boolean mouseClicked(double clickX, double clickY, int clickBtn) {
        double mx = clickX, my = clickY;
        for (int t = 0; t < TYPES.length; t++) if (in(mx, my, gx + t * (tabW + 4), py + 26, tabW, 18)) { tab = t; scroll = 0; return true; }
        int bx = px + pw - PREVIEW_W - 4, bw = PREVIEW_W - 4;
        if (in(mx, my, bx, py + ph - 48, bw, 18)) { Cosmetics.animate = !Cosmetics.animate; return true; }
        if (in(mx, my, bx, py + ph - 26, bw, 18)) { close(); return true; }
        if (in(mx, my, gx, gy, gw, gh)) {
            List<Cosmetics.Item> list = entries();
            for (int i = 0; i < list.size(); i++) {
                int x = gx + (i % cols()) * (CW + GAP), y = gy + (i / cols()) * (CH + GAP) - scroll;
                if (in(mx, my, x, y, CW, CH)) { Cosmetics.select(TYPES[tab], list.get(i) == null ? null : list.get(i).id); return true; }
            }
        }
        return super.mouseClicked(clickX, clickY, clickBtn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (vy * 24)));
        return true;
    }

    private void button(DrawContext g, String label, int x, int y, int w, int h, boolean primary, boolean hot) {
        Ui.card(g, x, y, w, h, primary ? Ui.VIOLET : hot ? Ui.PANEL2 : Ui.PANEL, hot ? Ui.ACCENT : Ui.BORDER);
        Ui.centered(g, textRenderer, label, x + w / 2, y + (h - 8) / 2, Ui.TEXT);
    }

    @Override
    public void renderBackground(DrawContext g, int mx, int my, float d) { }

    @Override
    public void render(DrawContext g, int mx, int my, float delta) {
        if (client != null && client.world != null) g.fillGradient(0, 0, width, height, 0xC0070914, 0xE00E0B22);
        else g.fillGradient(0, 0, width, height, 0xFF04030B, 0xFF120A2E);
        for (int k = 3; k >= 1; k--) Ui.chamfer(g, px - k * 2, py - k * 2, pw + k * 4, ph + k * 4, 6 + k * 2, ((0x16 / k) << 24) | 0x8B5CF6);
        Ui.chamfer(g, px, py, pw, ph, 6, 0xF4090818);
        Ui.chamferBorder(g, px, py, pw, ph, 6, 0xFF3A3466);
        Ui.hgrad(g, px + 6, py, pw - 12, 2, Ui.ACCENT, Ui.PINK);
        Ui.text(g, textRenderer, "Cosm\u00e9ticos", px + 10, py + 8, Ui.TEXT);
        int hw = textRenderer.getWidth("Cosm\u00e9ticos");
        String problem = CosmeticSync.problem();
        Ui.text(g, textRenderer, Ui.fit(textRenderer, problem != null ? problem : "Los ven todos los que usan Atenas", pw - hw - 30), px + 10 + hw + 10, py + 8, problem != null ? Ui.AMBER : Ui.DIM);

        for (int t = 0; t < TYPES.length; t++) {
            int x = gx + t * (tabW + 4);
            boolean on = t == tab, hot = in(mx, my, x, py + 26, tabW, 18);
            Ui.card(g, x, py + 26, tabW, 18, on ? Ui.PANEL2 : hot ? 0xFF191631 : Ui.PANEL, on || hot ? Ui.ACCENT : Ui.BORDER);
            Ui.centered(g, textRenderer, LABELS[t], x + tabW / 2, py + 31, on ? Ui.TEXT : Ui.DIM);
            if (on) g.fill(x, py + 44, x + tabW, py + 46, Ui.ACCENT);
        }

        scroll = Math.max(0, Math.min(maxScroll(), scroll));
        g.enableScissor(gx, gy, gx + gw, gy + gh);
        List<Cosmetics.Item> list = entries();
        String curId = Cosmetics.selectedId(TYPES[tab]);
        for (int i = 0; i < list.size(); i++) {
            Cosmetics.Item it = list.get(i);
            int x = gx + (i % cols()) * (CW + GAP), y = gy + (i / cols()) * (CH + GAP) - scroll;
            if (y + CH < gy || y > gy + gh) continue;
            boolean on = it == null ? curId == null : it.id.equals(curId), hot = in(mx, my, x, y, CW, CH) && my >= gy && my < gy + gh;
            Ui.card(g, x, y, CW, CH, hot ? Ui.PANEL2 : Ui.PANEL, on ? Ui.ACCENT : hot ? Ui.ACCENT_DIM : Ui.BORDER);
            if (it == null) {
                Ui.centered(g, textRenderer, "\u2715", x + CW / 2, y + 16, Ui.DIM);
                Ui.centered(g, textRenderer, "Ninguno", x + CW / 2, y + CH - 14, on ? Ui.TEXT : Ui.DIM);
            } else {
                g.fill(x + (CW - 32) / 2, y + 6, x + (CW - 32) / 2 + 32, y + 38, Ui.a(it.color, 0.25f));
                Identifier icon = Identifier.of(Cosmetics.NS, "textures/cosmetics/icons/" + TYPES[tab] + "_" + it.id + ".png");
                int[] sz = iconSize(icon);
                try {
                    // los iconos miden 32, 64 o 128 px: se dibuja la textura ENTERA (antes se pedia siempre una region de 32x32 y los de 128 salian recortados)
                    g.drawTexture(RenderPipelines.GUI_TEXTURED, icon, x + (CW - 32) / 2, y + 6, 0f, 0f, 32, 32, sz[0], sz[1], sz[0], sz[1]);
                } catch (Throwable ignored) { }
                Ui.centered(g, textRenderer, Ui.fit(textRenderer, it.name, CW - 4), x + CW / 2, y + CH - 14, on ? Ui.TEXT : Ui.DIM);
            }
            if (on) g.fill(x + CW - 9, y + 3, x + CW - 3, y + 9, Ui.ACCENT);
        }
        g.disableScissor();

        int rx = px + pw - PREVIEW_W - 4, ry = py + 26, rw = PREVIEW_W - 4, rh = ph - 80;
        Ui.card(g, rx, ry, rw, rh, 0xFF090812, Ui.BORDER);
        if (client != null && client.player != null) {
            g.enableScissor(rx + 1, ry + 1, rx + rw - 1, ry + rh - 1);
            drawPlayer(g, rx, ry, rw, rh);
            g.disableScissor();
        } else {
            Ui.centered(g, textRenderer, "Entra a un mundo", rx + rw / 2, ry + rh / 2 - 8, Ui.TEXT);
            Ui.centered(g, textRenderer, "para verte puesto", rx + rw / 2, ry + rh / 2 + 4, Ui.DIM);
        }
        StringBuilder sb = new StringBuilder();
        for (String t : TYPES) {
            Cosmetics.Item s = Cosmetics.find(t, Cosmetics.selectedId(t));
            if (s != null) { if (sb.length() > 0) sb.append(" \u00b7 "); sb.append(s.name); }
        }
        Ui.centered(g, textRenderer, Ui.fit(textRenderer, sb.length() == 0 ? "Sin cosm\u00e9ticos" : sb.toString(), rw - 6), rx + rw / 2, ry + rh - 12, Ui.DIM);

        button(g, "Animadas: " + (Cosmetics.animate ? "S\u00ed" : "No"), rx, py + ph - 48, rw, 18, false, in(mx, my, rx, py + ph - 48, rw, 18));
        button(g, "Listo", rx, py + ph - 26, rw, 18, true, in(mx, my, rx, py + ph - 26, rw, 18));
    }


    /** Estado de render del personaje de la vista previa: la capa de cosmeticos lo reconoce por identidad. */
    public static volatile EntityRenderState previewState;

    /** Jugador girando (60 grados por segundo, como el 1.8.9), escala 42, pies cerca del fondo y 10 px a la izquierda del centro. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void drawPlayer(DrawContext g, int rx, int ry, int rw, int rh) {
        ClientPlayerEntity pl = client.player;
        final int size = 42;
        float spin = (System.currentTimeMillis() * 0.06f) % 360f;
        int cx = rx + rw / 2 - 10, feet = ry + rh - 22;
        int cy = feet - (int) (0.9f * size);
        float ob = pl.bodyYaw, oy = pl.getYaw(), op = pl.getPitch(), olh = pl.lastHeadYaw, oh = pl.headYaw;
        pl.bodyYaw = spin; pl.setYaw(spin); pl.setPitch(0f); pl.headYaw = spin; pl.lastHeadYaw = spin;
        try {
            EntityRenderer r = client.getEntityRenderDispatcher().getRenderer(pl);
            EntityRenderState st = r.getAndUpdateRenderState(pl, 1.0f);
            previewState = st;
            Vector3f t = new Vector3f(0f, pl.getHeight() / 2f + 0.0625f, 0f);
            g.addEntity(st, size, t, new Quaternionf().rotateZ((float) Math.PI), new Quaternionf(), cx - rw / 2, cy - rh / 2, cx + rw / 2, cy + rh / 2);
        } finally {
            pl.bodyYaw = ob; pl.setYaw(oy); pl.setPitch(op); pl.lastHeadYaw = olh; pl.headYaw = oh;
        }
    }

    @Override
    public void close() { if (client != null) client.setScreen(parent); }

    @Override
    public boolean shouldPause() { return false; }
}
