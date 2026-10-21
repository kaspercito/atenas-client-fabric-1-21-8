package com.atenas.fabric.hud;

import java.util.List;

import com.atenas.fabric.gui.Fx;
import com.atenas.fabric.gui.Ui;
import com.atenas.fabric.shared.Waypoint;
import com.atenas.fabric.shared.WaypointStore;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Administrador de waypoints del mundo actual (B, o el engranaje de Waypoints): lista a la izquierda y editor a la derecha.
 * Mismo archivo que 1.8.9 (waypoints.json): nombre, X/Y/Z, color y visibilidad. Los cambios se guardan al cerrar.
 */
public final class WaypointScreen extends Screen {
    private static final int ROW = 24;
    private final Screen parent;
    private int px, py, pw, ph, listX, listY, listW, listH, edX, edW, scroll;
    private Waypoint sel;
    private TextFieldWidget fName, fX, fY, fZ;
    private Ui.Btn bHere, bVisible, bDelete;
    private boolean dirty, loading;
    private String message = "";
    private long messageAt;

    public WaypointScreen(Screen parent) { super(Text.literal("Waypoints")); this.parent = parent; }

    // ------------------------------------------------------------ geometria

    private int ey() { return py + 38; }
    private int cw() { return (edW - 16) / 3; }

    @Override
    protected void init() {
        pw = Math.min(430, width - 16);
        ph = Math.min(270, height - 16);
        px = (width - pw) / 2; py = (height - ph) / 2;
        listX = px + 10; listY = py + 38; listW = Math.min(190, pw / 2 - 14); listH = ph - 38 - 40;
        edX = listX + listW + 14; edW = px + pw - 10 - edX;
        int ey = ey(), cw = cw();
        fName = field(edX + 5, ey + 15, edW - 10, 24);
        fX = field(edX + 5, ey + 49, cw - 10, 9);
        fY = field(edX + cw + 8 + 5, ey + 49, cw - 10, 9);
        fZ = field(edX + 2 * (cw + 8) + 5, ey + 49, cw - 10, 9);
        addDrawableChild(new Ui.Btn(listX, py + ph - 30, listW, 20, Text.literal("+ Nuevo aqu\u00ed"), b -> addHere()));
        addDrawableChild(new Ui.Btn(px + pw - 10 - 90, py + ph - 30, 90, 20, Text.literal("Cerrar"), b -> close()));
        bHere = addDrawableChild(new Ui.Btn(edX, ey + 112, edW, 18, Text.literal("Mover a mi posici\u00f3n"), b -> moveHere()));
        bVisible = addDrawableChild(new Ui.Btn(edX, ey + 134, edW, 18, Text.literal("Mostrar / ocultar"), b -> { if (sel != null) { sel.visible = !sel.visible; dirty = true; } }));
        bDelete = addDrawableChild(new Ui.Btn(edX, ey + 156, edW, 18, Text.literal("Borrar"), b -> deleteSel()));
        select(sel != null && WaypointStore.current().contains(sel) ? sel : null);
    }

    private TextFieldWidget field(int x, int y, int w, int max) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 8, Text.empty());
        f.setDrawsBackground(false);
        f.setMaxLength(max);
        f.setChangedListener(t -> onEdit());
        addDrawableChild(f);
        return f;
    }

    // ------------------------------------------------------------ logica

    private void say(String s) { message = s; messageAt = System.currentTimeMillis(); }

    private static String dimName(int d) { return d == -1 ? "Nether" : d == 1 ? "End" : d == 0 ? "Overworld" : "Dim " + d; }

    private static String fmtDist(double d) { return d >= 1000 ? String.format(java.util.Locale.ROOT, "%.1fkm", d / 1000.0) : Math.round(d) + "m"; }

    private double distTo(Waypoint w) {
        if (client == null || client.player == null) return 0;
        double dx = w.x - client.player.getX(), dy = w.y - client.player.getY(), dz = w.z - client.player.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void select(Waypoint w) {
        loading = true;
        sel = w;
        boolean on = w != null;
        bHere.active = on; bVisible.active = on; bDelete.active = on;
        fName.setText(on ? w.name : "");
        fX.setText(on ? String.valueOf((int) Math.floor(w.x)) : "");
        fY.setText(on ? String.valueOf((int) Math.floor(w.y)) : "");
        fZ.setText(on ? String.valueOf((int) Math.floor(w.z)) : "");
        if (!on) setFocused(null);
        loading = false;
    }

    private void addHere() {
        if (client == null || client.player == null || WaypointStore.worldKey() == null) { say("Entra a un mundo para guardar un punto."); return; }
        Waypoint w = WaypointStore.add(WaypointStore.nextName(), Math.floor(client.player.getX()) + 0.5, Math.floor(client.player.getY()),
            Math.floor(client.player.getZ()) + 0.5, WaypointStore.currentDim(), 3);
        if (w == null) { say("L\u00edmite de waypoints alcanzado en este mundo."); return; }
        select(w);
        scroll = Math.max(0, WaypointStore.current().size() - Math.max(1, listH / ROW)); // baja hasta el nuevo
    }

    private void moveHere() {
        if (sel == null || client == null || client.player == null) return;
        sel.x = Math.floor(client.player.getX()) + 0.5; sel.y = Math.floor(client.player.getY()); sel.z = Math.floor(client.player.getZ()) + 0.5;
        sel.dim = WaypointStore.currentDim();
        select(sel);
        dirty = true;
    }

    private void deleteSel() {
        if (sel == null) return;
        WaypointStore.remove(sel); // guarda todo el archivo
        select(null);
        dirty = false;
    }

    private static boolean isInt(String s) { try { Integer.parseInt(s.trim()); return true; } catch (Exception e) { return false; } }

    private static double parse(String s, double fallback) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; } }

    /** Cambio en un campo: se aplica al punto elegido (el nombre no puede quedar vacio; una coordenada no valida se ignora). */
    private void onEdit() {
        if (loading || sel == null) return;
        String n = fName.getText().trim();
        if (!n.isEmpty()) sel.name = n;
        sel.x = parse(fX.getText(), sel.x) + (isInt(fX.getText()) ? 0.5 : 0);
        sel.y = parse(fY.getText(), sel.y);
        sel.z = parse(fZ.getText(), sel.z) + (isInt(fZ.getText()) ? 0.5 : 0);
        dirty = true;
    }

    // ------------------------------------------------------------ dibujo

    @Override
    public void renderBackground(DrawContext g, int mx, int my, float d) { }

    @Override
    public void render(DrawContext g, int mx, int my, float delta) {
        List<Waypoint> list = WaypointStore.current();
        if (sel != null && !list.contains(sel)) select(null); // otro cliente cambio el archivo
        int maxScroll = Math.max(0, list.size() - listH / ROW);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        boolean on = sel != null;
        fName.visible = on; fX.visible = on; fY.visible = on; fZ.visible = on;

        Ui.gradient(g, 0, 0, width, height, 0xC0070914, 0xE00E0B22, true);
        Ui.card(g, px - 3, py - 3, pw + 6, ph + 6, 0x30000000, 0x188B5CF6);
        Ui.card(g, px, py, pw, ph, 0xF00A0D1C, Ui.BORDER);
        Ui.gradient(g, px + 1, py, pw - 2, 2, Ui.VIOLET, Ui.PINK, false);
        Fx.heading(g, "Waypoints", px + 12, py + 12, Ui.ACCENT);
        String hint = "B abre esto  \u00b7  N guarda un punto aqu\u00ed";
        Ui.text(g, Ui.fit(hint, pw - 140), px + pw - 12 - Math.min(Fx.width(hint), pw - 140), py + 12, Ui.DIM);
        if (!message.isEmpty() && System.currentTimeMillis() - messageAt < 4000L) Ui.text(g, Ui.fit(message, pw - 24), px + 12, py + 25, Ui.AMBER);

        // lista
        Ui.card(g, listX - 2, listY - 2, listW + 4, listH + 4, Ui.PANEL, Ui.BORDER);
        int dim = WaypointStore.currentDim();
        g.enableScissor(listX, listY, listX + listW, listY + listH);
        for (int i = scroll; i < list.size() && (i - scroll) * ROW < listH; i++) {
            Waypoint w = list.get(i);
            int y = listY + (i - scroll) * ROW;
            boolean hov = Ui.inside(mx, my, listX, y, listW, ROW);
            if (w == sel) Ui.rect(g, listX, y, listW, ROW - 1, 0x558B5CF6);
            else if (hov) Ui.rect(g, listX, y, listW, ROW - 1, 0x33FFFFFF);
            Ui.rect(g, listX + 6, y + 6, 10, 10, w.visible ? Markers.argb(w) : Ui.OFF);
            Ui.border(g, listX + 6, y + 6, 10, 10, 0xFF0A0814);
            Ui.text(g, Ui.fit(w.name, listW - 30), listX + 22, y + 3, w.visible ? Ui.TEXT : Ui.DIM);
            boolean here = w.dim == dim;
            Ui.text(g, here ? fmtDist(distTo(w)) : dimName(w.dim), listX + 22, y + 13, here ? Ui.CYAN : Ui.AMBER);
        }
        if (list.isEmpty()) {
            if (WaypointStore.worldKey() == null) {
                Ui.text(g, Ui.fit("Entra a un mundo para ver tus puntos.", listW - 12), listX + 8, listY + 8, Ui.DIM);
            } else {
                Ui.text(g, "A\u00fan no tienes puntos.", listX + 8, listY + 8, Ui.DIM);
                Ui.text(g, Ui.fit("Pulsa \"+ Nuevo aqu\u00ed\" o la tecla N.", listW - 12), listX + 8, listY + 20, Ui.DIM);
            }
        }
        g.disableScissor();
        if (list.size() * ROW > listH) {
            int total = list.size() * ROW, bh = Math.max(14, listH * listH / total);
            int by = listY + (int) ((listH - bh) * (scroll * ROW / (double) (total - listH)));
            Ui.rect(g, listX + listW - 3, by, 2, bh, Ui.ACCENT_DIM);
        }

        // editor
        int ey = ey(), cw = cw();
        if (sel == null) {
            Ui.lines(g, Fx.wrap("Elige un punto de la lista para editarlo.", edW, 3), edX, ey + 4, 11, Ui.DIM);
        } else {
            Ui.text(g, "Nombre", edX, ey, Ui.DIM);
            Ui.card(g, edX, ey + 10, edW, 18, 0xFF0A0918, fName.isFocused() ? Ui.ACCENT : Ui.BORDER);
            Ui.text(g, "X", edX, ey + 34, Ui.DIM);
            Ui.text(g, "Y", edX + cw + 8, ey + 34, Ui.DIM);
            Ui.text(g, "Z", edX + 2 * (cw + 8), ey + 34, Ui.DIM);
            Ui.card(g, edX, ey + 44, cw, 18, 0xFF0A0918, fX.isFocused() ? Ui.ACCENT : Ui.BORDER);
            Ui.card(g, edX + cw + 8, ey + 44, cw, 18, 0xFF0A0918, fY.isFocused() ? Ui.ACCENT : Ui.BORDER);
            Ui.card(g, edX + 2 * (cw + 8), ey + 44, cw, 18, 0xFF0A0918, fZ.isFocused() ? Ui.ACCENT : Ui.BORDER);
            Ui.text(g, "Color", edX, ey + 68, Ui.DIM);
            int n = Markers.WP_COLORS.length, step = Math.max(12, Math.min(20, edW / n)), size = step - 4, cur = Math.abs(sel.color) % n;
            for (int i = 0; i < n; i++) {
                int sx = edX + i * step;
                Ui.rect(g, sx, ey + 78, size, size, Markers.WP_COLORS[i]);
                if (cur == i) Ui.border(g, sx - 1, ey + 77, size + 2, size + 2, Ui.TEXT);
            }
            String info = dimName(sel.dim) + (sel.dim == dim ? "  \u00b7  " + fmtDist(distTo(sel)) : "");
            Ui.text(g, Ui.fit(info, edW), edX, ey + 98, Ui.CYAN);
        }

        super.render(g, mx, my, delta);
        Ui.paintButtons(g, textRenderer, children());
    }

    // ------------------------------------------------------------ entrada

    @Override
    public boolean mouseClicked(double clickX, double clickY, int clickBtn) {
        double mx = clickX, my = clickY;
        int btn = clickBtn;
        int ey = ey(), cw = cw();
        if (btn == 0 && sel != null) { // las cajas son mas grandes que el campo de texto: clic en cualquier parte de la caja = enfocar
            if (Ui.inside(mx, my, edX, ey + 10, edW, 18)) { setFocused(fName); return true; }
            if (Ui.inside(mx, my, edX, ey + 44, cw, 18)) { setFocused(fX); return true; }
            if (Ui.inside(mx, my, edX + cw + 8, ey + 44, cw, 18)) { setFocused(fY); return true; }
            if (Ui.inside(mx, my, edX + 2 * (cw + 8), ey + 44, cw, 18)) { setFocused(fZ); return true; }
        }
        if (super.mouseClicked(clickX, clickY, clickBtn)) return true; // botones
        if (btn != 0) return false;
        List<Waypoint> list = WaypointStore.current();
        if (Ui.inside(mx, my, listX, listY, listW, listH)) {
            int i = scroll + ((int) my - listY) / ROW;
            if (i >= 0 && i < list.size()) { select(list.get(i)); return true; }
        }
        if (sel != null) {
            int n = Markers.WP_COLORS.length, step = Math.max(12, Math.min(20, edW / n)), size = step - 4;
            for (int i = 0; i < n; i++) {
                if (Ui.inside(mx, my, edX + i * step, ey + 78, size, size)) { sel.color = i; dirty = true; return true; }
            }
        }
        setFocused(null);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        if (vy == 0) return false;
        int max = Math.max(0, WaypointStore.current().size() - listH / ROW);
        scroll = Math.max(0, Math.min(max, scroll + (vy > 0 ? -1 : 1)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        Element f = getFocused();
        if (f == fX || f == fY || f == fZ) { // coordenadas: solo digitos y signo menos
            int cp = (int) chr;
            if (!(cp >= '0' && cp <= '9') && cp != '-') return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void removed() { if (dirty) { WaypointStore.save(); dirty = false; } }

    @Override
    public void close() { if (dirty) { WaypointStore.save(); dirty = false; } if (client != null) client.setScreen(parent); }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public boolean shouldPause() { return false; }
}
