package com.atenas.fabric.hud;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.atenas.fabric.gui.Fx;
import com.atenas.fabric.gui.Ui;
import com.atenas.fabric.shared.Config;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Panel de ajustes de un modulo (interruptores, deslizadores, listas, colores y textos). Todo se guarda al instante.
 * La lista tiene scroll (rueda del mouse), un aviso cuando el modulo no tiene ajustes y, en Waypoints y Equipo, un boton para abrir su administrador.
 */
public final class ModSettingsScreen extends Screen {
    private static final int ROW = 24, TOP = 44, FOOT = 38;
    private final Screen parent;
    private final String mod, title;
    private final List<Settings.Def> defs;
    private final Map<Integer, TextFieldWidget> fieldOf = new HashMap<>();
    private int px, py, pw, ph, listTop, listH, scroll, dragging = -1;
    private float scrollSmooth;

    public ModSettingsScreen(Screen parent, String mod, String title) {
        super(Text.literal(title));
        this.parent = parent; this.mod = mod; this.title = title;
        this.defs = Settings.of(mod);
    }

    @Override
    protected void init() {
        pw = Math.min(380, width - 16);
        ph = Math.min(Math.max(150, TOP + Math.max(defs.size(), 2) * ROW + FOOT), height - 8);
        px = (width - pw) / 2; py = (height - ph) / 2;
        listTop = py + TOP; listH = Math.max(ROW, ph - TOP - FOOT);
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        scrollSmooth = scroll;
        fieldOf.clear();
        for (int i = 0; i < defs.size(); i++) {
            Settings.Def d = defs.get(i);
            if (d.type != Settings.T.TEXT) continue;
            final String name = d.name;
            TextFieldWidget f = new TextFieldWidget(textRenderer, ctlX() + 5, rowY(i) + 8, 156, 8, Text.empty());
            f.setDrawsBackground(false);
            f.setMaxLength(100);
            f.setText(Settings.str(mod, name, ""));
            f.setChangedListener(t -> Settings.setStr(mod, name, t));
            addDrawableChild(f);
            fieldOf.put(i, f);
        }
        if (mod.equals("waypoints")) {
            addDrawableChild(new Ui.Btn(px + 14, py + ph - 28, 160, 20, Text.literal("Administrar waypoints"), b -> client.setScreen(new WaypointScreen(this))));
        } else if (mod.equals("team")) {
            addDrawableChild(new Ui.Btn(px + 14, py + ph - 28, 160, 20, Text.literal("Administrar equipo"), b -> client.setScreen(new TeamScreen(this))));
        }
        addDrawableChild(new Ui.Btn(px + pw - 14 - 90, py + ph - 28, 90, 20, Text.literal("Listo"), b -> close()));
    }

    private int maxScroll() { return Math.max(0, defs.size() * ROW + 4 - listH); }
    private int rowY(int i) { return listTop + i * ROW - Math.round(scrollSmooth); }
    private int ctlX() { return px + pw - 176; }
    private boolean inList(double my) { return my >= listTop && my < listTop + listH; }

    @Override
    public void renderBackground(DrawContext g, int mx, int my, float d) { }

    @Override
    public void render(DrawContext g, int mx, int my, float delta) {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        scrollSmooth += (scroll - scrollSmooth) * 0.35f;
        if (Math.abs(scroll - scrollSmooth) < 0.5f) scrollSmooth = scroll;

        Ui.gradient(g, 0, 0, width, height, 0xC0070914, 0xE00E0B22, true);
        Ui.card(g, px - 3, py - 3, pw + 6, ph + 6, 0x30000000, 0x188B5CF6);
        Ui.card(g, px, py, pw, ph, 0xF00A0D1C, Ui.BORDER);
        Ui.gradient(g, px + 1, py, pw - 2, 2, Ui.VIOLET, Ui.PINK, false);
        Fx.heading(g, title, px + 14, py + 12, Ui.TEXT);
        Ui.text(g, "Ajustes del modulo", px + 14, py + 26, Ui.DIM);
        Ui.rect(g, px + 14, py + 40, pw - 28, 1, Ui.BORDER);
        Ui.rect(g, px + 14, py + ph - FOOT + 2, pw - 28, 1, Ui.BORDER);

        // los campos de texto siguen a su fila y se ocultan si la fila no se ve entera
        for (Map.Entry<Integer, TextFieldWidget> e : fieldOf.entrySet()) {
            int y = rowY(e.getKey());
            TextFieldWidget f = e.getValue();
            f.setY(y + 8);
            f.visible = y >= listTop && y + ROW <= listTop + listH;
            if (!f.visible && f.isFocused()) f.setFocused(false);
        }

        if (defs.isEmpty()) {
            Ui.centered(g, "Este m\u00f3dulo no tiene ajustes.", px + pw / 2, listTop + 22, Ui.TEXT);
            Ui.centered(g, Ui.fit("Se activa o se desactiva desde el men\u00fa Mods.", pw - 40), px + pw / 2, listTop + 38, Ui.DIM);
        }

        g.enableScissor(px + 1, listTop, px + pw - 1, listTop + listH);
        for (int i = 0; i < defs.size(); i++) {
            Settings.Def d = defs.get(i);
            int y = rowY(i), cx = ctlX();
            if (y + ROW < listTop || y > listTop + listH) continue;
            Ui.text(g, Fx.fit(d.label, cx - px - 24), px + 14, y + 8, Ui.TEXT);
            switch (d.type) {
                case BOOL -> Ui.toggle(g, px + pw - 14 - 22 - 6, y + 6, Settings.b(mod, d.name));
                case NUM -> {
                    float v = Settings.n(mod, d.name);
                    double t = Math.max(0, Math.min(1, (v - d.min) / (d.max - d.min)));
                    Ui.rect(g, cx, y + 11, 120, 2, Ui.BORDER);
                    Ui.rect(g, cx, y + 11, (int) (120 * t), 2, Ui.VIOLET);
                    Ui.rect(g, cx + (int) (120 * t) - 2, y + 7, 5, 10, Ui.ACCENT);
                    Ui.text(g, d.step < 1 ? String.format(java.util.Locale.ROOT, "%.1f", v) : String.valueOf(Math.round(v)), cx + 128, y + 8, Ui.DIM);
                }
                case CHOICE, COLOR -> {
                    int idx = Settings.c(mod, d.name);
                    boolean hov = Ui.inside(mx, my, cx, y + 2, 156, 18) && inList(my);
                    Ui.card(g, cx, y + 2, 156, 18, hov ? Ui.PANEL2 : Ui.PANEL, hov ? Ui.ACCENT : Ui.BORDER);
                    if (d.type == Settings.T.COLOR) Ui.rect(g, cx + 6, y + 7, 8, 8, Settings.COLORS[Math.max(0, Math.min(idx, Settings.COLORS.length - 1))]);
                    Ui.text(g, d.opts[Math.max(0, Math.min(idx, d.opts.length - 1))], cx + (d.type == Settings.T.COLOR ? 20 : 8), y + 8, Ui.TEXT);
                    Ui.text(g, ">", cx + 146, y + 8, Ui.DIM);
                }
                case TEXT -> {
                    TextFieldWidget f = fieldOf.get(i);
                    Ui.card(g, cx, y + 2, 166, 18, 0xFF0A0918, f != null && f.isFocused() ? Ui.ACCENT : Ui.BORDER);
                }
            }
        }
        g.disableScissor();

        int max = maxScroll();
        if (max > 0) {
            int barH = Math.max(18, listH * listH / (listH + max)), barY = listTop + (int) ((listH - barH) * (scrollSmooth / (double) max));
            g.fill(px + pw - 7, listTop, px + pw - 5, listTop + listH, 0x20FFFFFF);
            g.fillGradient(px + pw - 7, barY, px + pw - 5, barY + barH, Ui.VIOLET, Ui.PINK);
        }

        super.render(g, mx, my, delta);
        Ui.paintButtons(g, textRenderer, children());
    }

    private void slide(int i, double mx) {
        Settings.Def d = defs.get(i);
        double t = Math.max(0, Math.min(1, (mx - ctlX()) / 120.0));
        double v = d.min + t * (d.max - d.min);
        v = Math.round(v / d.step) * d.step;
        Settings.setNum(mod, d.name, (float) Math.max(d.min, Math.min(d.max, v)));
    }

    @Override
    public boolean mouseClicked(double clickX, double clickY, int clickBtn) {
        if (clickBtn != 0) return false;
        double mx = clickX, my = clickY;
        if (inList(my)) {
            setFocused(null);
            int cx = ctlX();
            for (int i = 0; i < defs.size(); i++) {
                Settings.Def d = defs.get(i);
                int y = rowY(i);
                if (my < y || my >= y + ROW) continue;
                switch (d.type) {
                    case BOOL -> Settings.setBool(mod, d.name, !Settings.b(mod, d.name));
                    case NUM -> { if (mx >= cx - 4 && mx <= cx + 124) { dragging = i; slide(i, mx); } }
                    case CHOICE, COLOR -> { if (mx >= cx && mx < cx + 156) Settings.setInt(mod, d.name, (Settings.c(mod, d.name) + 1) % d.opts.length); }
                    case TEXT -> { TextFieldWidget f = fieldOf.get(i); if (f != null && f.visible && mx >= cx && mx < cx + 166) setFocused(f); }
                }
                return true;
            }
            return true;
        }
        return super.mouseClicked(clickX, clickY, clickBtn);
    }

    @Override
    public boolean mouseDragged(double clickX, double clickY, int clickBtn, double dx, double dy) {
        if (dragging >= 0) { slide(dragging, clickX); return true; }
        return super.mouseDragged(clickX, clickY, clickBtn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double clickX, double clickY, int clickBtn) {
        if (dragging >= 0) { dragging = -1; Config.save(); return true; }
        return super.mouseReleased(clickX, clickY, clickBtn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        if (vy == 0 || maxScroll() == 0) return false;
        scroll = Math.max(0, Math.min(maxScroll(), scroll + (vy > 0 ? -ROW : ROW)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() { Config.save(); if (client != null) client.setScreen(parent); }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public boolean shouldPause() { return false; }
}
