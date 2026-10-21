package com.atenas.fabric.hud;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.atenas.fabric.gui.Fx;
import com.atenas.fabric.gui.Keys;
import com.atenas.fabric.gui.Ui;
import com.atenas.fabric.shared.Config;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Editor HUD: arrastra cada panel con el mouse, la rueda cambia su tamano, clic derecho lo restablece y las flechas lo mueven de 1 en 1
 * (con Shift de 5 en 5). Trabaja sobre {@link HudPanels} (los paneles) y {@link HudPos} (posicion y escala). Se guarda al soltar.
 * Los paneles los dibuja {@link Hud} (con editing = true); aqui solo se pinta el marco de cada uno.
 */
public final class HudEditorScreen extends Screen {
    private final Screen parent;
    private HudPanels.Panel drag, sel;
    private int grabX, grabY;
    private boolean snapX, snapY;

    public HudEditorScreen(Screen parent) { super(Text.literal("Editor HUD")); this.parent = parent; }

    @Override
    protected void init() {
        addDrawableChild(new Ui.Btn(width / 2 - 104, height - 34, 100, 20, Text.literal("Restablecer"), b -> { HudPos.reset(); sel = null; }));
        addDrawableChild(new Ui.Btn(width / 2 + 4, height - 34, 100, 20, Text.literal("Listo"), b -> close()));
    }

    /** Un panel visible con su caja en pantalla (ya escalada). */
    private static final class Box {
        final HudPanels.Panel p; final int x, y, w, h;
        Box(HudPanels.Panel p, int x, int y, int w, int h) { this.p = p; this.x = x; this.y = y; this.w = w; this.h = h; }
        boolean contains(double mx, double my) { return mx >= x - 2 && mx < x + w + 2 && my >= y - 2 && my < y + h + 2; }
    }

    private boolean inWorld() { return client != null && client.player != null && client.world != null; }

    /** Los paneles que se estan dibujando ahora: el HUD activo y el modulo de cada panel activado. */
    private List<Box> boxes() {
        List<Box> out = new ArrayList<>();
        if (!inWorld() || !Config.get("hud", true)) return out;
        for (HudPanels.Panel p : HudPanels.all()) {
            if (!p.enabled()) continue;
            try {
                int w = p.width(client), h = p.height(client);
                if (w <= 0 || h <= 0) continue;
                int[] r = HudPos.pos(p, width, height, w, h);
                out.add(new Box(p, r[0], r[1], r[2], r[3]));
            } catch (RuntimeException ignored) { }
        }
        return out;
    }

    private static Box hit(List<Box> boxes, double mx, double my) {
        for (int i = boxes.size() - 1; i >= 0; i--) if (boxes.get(i).contains(mx, my)) return boxes.get(i);
        return null;
    }

    @Override
    public void renderBackground(DrawContext g, int mx, int my, float d) { }

    /** Esquinas tipo visor alrededor de una caja. */
    private static void corners(DrawContext g, int x, int y, int w, int h, int c) {
        int l = Math.max(3, Math.min(5, Math.min(w, h) / 2));
        Ui.rect(g, x, y, l, 1, c); Ui.rect(g, x, y, 1, l, c);
        Ui.rect(g, x + w - l, y, l, 1, c); Ui.rect(g, x + w - 1, y, 1, l, c);
        Ui.rect(g, x, y + h - 1, l, 1, c); Ui.rect(g, x, y + h - l, 1, l, c);
        Ui.rect(g, x + w - l, y + h - 1, l, 1, c); Ui.rect(g, x + w - 1, y + h - l, 1, l, c);
    }

    @Override
    public void render(DrawContext g, int mx, int my, float delta) {
        Ui.gradient(g, 0, 0, width, height, 0x80070914, 0xA00E0B22, true);
        // ejes centrales: se iluminan cuando el panel que arrastras queda centrado
        Ui.rect(g, width / 2, 0, 1, height, snapX ? 0x8022D3EE : 0x2822D3EE);
        Ui.rect(g, 0, height / 2, width, 1, snapY ? 0x8022D3EE : 0x2822D3EE);

        List<Box> boxes = boxes();
        Box over = drag == null ? hit(boxes, mx, my) : null;
        for (Box b : boxes) {
            boolean act = b.p == drag || (over != null && b.p == over.p), picked = b.p == sel;
            Ui.rect(g, b.x, b.y, b.w, b.h, act ? 0x308B5CF6 : 0x148B5CF6);
            corners(g, b.x - 2, b.y - 2, b.w + 4, b.h + 4, act ? Ui.ACCENT : picked ? Ui.ACCENT_DIM : 0x70FFFFFF);
        }
        for (Box b : boxes) {
            if (!(b.p == drag || (over != null && b.p == over.p) || b.p == sel)) continue;
            String tag = b.p.name + "  " + Math.round(HudPos.scale(b.p.id) * 100f) + "%";
            int tw = Fx.width(tag) + 10, ty = b.y > 18 ? b.y - 16 : b.y + b.h + 4, tx = Math.max(2, Math.min(width - tw - 2, b.x));
            Ui.rect(g, tx, ty, tw, 12, 0xE0090812);
            Ui.rect(g, tx, ty, 2, 12, Ui.ACCENT);
            Ui.text(g, tag, tx + 6, ty + 2, Ui.TEXT);
        }

        // cabecera
        int hw = Math.min(width - 20, 400), hx = width / 2 - hw / 2;
        Ui.rect(g, hx, 6, hw, 28, 0xE0090812);
        Ui.border(g, hx, 6, hw, 28, Ui.BORDER);
        Ui.gradient(g, hx, 6, hw, 2, Ui.ACCENT, 0x0022D3EE, false);
        Ui.logo(g, hx + 6, 10, 20);
        Fx.heading(g, "EDITOR HUD", hx + 32, 12, Ui.ACCENT);
        Ui.text(g, Ui.fit("Arrastra  \u00b7  Rueda: tama\u00f1o  \u00b7  Clic der.: restablecer  \u00b7  Flechas: afinar  \u00b7  Esc: guardar", hw - 40), hx + 32, 22, Ui.DIM);

        if (!inWorld()) Ui.centered(g, "Entra a un mundo para colocar los paneles.", width / 2, height / 2 + 6, Ui.DIM);
        else if (!Config.get("hud", true)) Ui.centered(g, "El HUD est\u00e1 desactivado. Act\u00edvalo en Mods.", width / 2, height / 2 + 6, Ui.DIM);
        else if (boxes.isEmpty()) Ui.centered(g, "No hay m\u00f3dulos de HUD activados. Act\u00edvalos en Mods.", width / 2, height / 2 + 6, Ui.DIM);

        super.render(g, mx, my, delta);
        Ui.paintButtons(g, textRenderer, children());
    }

    @Override
    public boolean mouseClicked(double clickX, double clickY, int clickBtn) {
        if (super.mouseClicked(clickX, clickY, clickBtn)) return true;
        double mx = clickX, my = clickY;
        int btn = clickBtn;
        if (btn != 0 && btn != 1) return false;
        Box b = hit(boxes(), mx, my);
        if (b == null) { if (btn == 0) sel = null; return false; }
        sel = b.p;
        if (btn == 1) { // clic derecho: ese panel vuelve a su sitio y a tamano 1
            HudPos.setOffset(b.p.id, 0f, 0f);
            HudPos.setScale(b.p.id, 1f);
            Config.save();
            return true;
        }
        drag = b.p;
        grabX = (int) mx - b.x; grabY = (int) my - b.y;
        return true;
    }

    @Override
    public boolean mouseDragged(double clickX, double clickY, int clickBtn, double dx, double dy) {
        if (drag == null || clickBtn != 0 || client == null) return super.mouseDragged(clickX, clickY, clickBtn, dx, dy);
        int w = drag.width(client), h = drag.height(client);
        float sc = HudPos.scale(drag.id);
        int sW = Math.max(1, (int) (w * sc)), sH = Math.max(1, (int) (h * sc));
        int nx = Math.max(0, Math.min(Math.max(0, width - sW), (int) clickX - grabX));
        int ny = Math.max(0, Math.min(Math.max(0, height - sH), (int) clickY - grabY));
        snapX = Math.abs(nx + sW / 2 - width / 2) < 4;
        snapY = Math.abs(ny + sH / 2 - height / 2) < 4;
        if (snapX) nx = width / 2 - sW / 2;
        if (snapY) ny = height / 2 - sH / 2;
        int[] a = HudPos.anchor(drag, width, w);
        HudPos.setOffset(drag.id, nx - a[0], ny - a[1]);
        return true;
    }

    @Override
    public boolean mouseReleased(double clickX, double clickY, int clickBtn) {
        if (drag != null) { drag = null; snapX = false; snapY = false; Config.save(); return true; }
        return super.mouseReleased(clickX, clickY, clickBtn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        if (vy == 0) return false;
        Box b = hit(boxes(), mx, my);
        if (b == null) return false;
        HudPos.setScale(b.p.id, HudPos.scale(b.p.id) + (vy > 0 ? 0.1f : -0.1f));
        Config.save();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int key = keyCode;
        if (key == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        if (sel != null && sel.enabled()) {
            int step = Keys.shift() ? 5 : 1, ddx = 0, ddy = 0;
            if (key == GLFW.GLFW_KEY_LEFT) ddx = -step;
            else if (key == GLFW.GLFW_KEY_RIGHT) ddx = step;
            else if (key == GLFW.GLFW_KEY_UP) ddy = -step;
            else if (key == GLFW.GLFW_KEY_DOWN) ddy = step;
            if (ddx != 0 || ddy != 0) {
                HudPos.setOffset(sel.id, HudPos.dx(sel.id) + ddx, HudPos.dy(sel.id) + ddy);
                Config.save();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() { Config.save(); if (client != null) client.setScreen(parent); }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public boolean shouldPause() { return false; }
}
