package com.atenas.fabric.hud;

import java.util.ArrayList;
import java.util.List;

import com.atenas.fabric.gui.Fx;
import com.atenas.fabric.gui.Ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Editor de Autotext (engranaje del modulo): hasta {@link Autotext#MAX} atajos. Cada fila tiene la tecla o boton del mouse (clic para
 * cambiarla y pulsar la nueva), el mensaje o comando (si empieza con / se envia como comando) y una X para borrarla. Se guarda al cerrar.
 */
public final class AutotextScreen extends Screen {
    private static final int ROW = 26, TOP = 56, FOOT = 38, KEY_W = 112, DEL_W = 20;
    private final Screen parent;
    private final List<TextFieldWidget> fields = new ArrayList<>();
    private Ui.Btn addBtn;
    private int px, py, pw, ph, listTop, listH, scroll, capturing = -1;
    private float scrollSmooth;

    public AutotextScreen(Screen parent) { super(Text.literal("Autotext")); this.parent = parent; }

    // ------------------------------------------------------------ geometria

    private int keyX() { return px + 14; }
    private int innerW() { return pw - 28 - 6; }                 // 6 px para la barra de scroll
    private int delX() { return keyX() + innerW() - DEL_W; }
    private int fieldX() { return keyX() + KEY_W + 6; }
    private int fieldW() { return delX() - 6 - fieldX(); }
    private int rowY(int i) { return listTop + i * ROW - Math.round(scrollSmooth); }
    private int maxScroll() { return Math.max(0, Autotext.entries().size() * ROW + 2 - listH); }
    private boolean inList(double my) { return my >= listTop && my < listTop + listH; }

    @Override
    protected void init() {
        pw = Math.min(470, width - 16);
        ph = Math.min(300, height - 16);
        px = (width - pw) / 2; py = (height - ph) / 2;
        listTop = py + TOP; listH = Math.max(ROW, ph - TOP - FOOT);
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        scrollSmooth = scroll;
        capturing = -1;
        fields.clear();
        List<Autotext.Entry> es = Autotext.entries();
        for (int i = 0; i < es.size(); i++) {
            final Autotext.Entry e = es.get(i);
            TextFieldWidget f = new TextFieldWidget(textRenderer, fieldX() + 6, rowY(i) + 9, fieldW() - 12, 8, Text.empty());
            f.setDrawsBackground(false);
            f.setMaxLength(256);
            f.setText(e.msg == null ? "" : e.msg);
            f.setChangedListener(t -> e.msg = t);
            addDrawableChild(f);
            fields.add(f);
        }
        addBtn = addDrawableChild(new Ui.Btn(px + 14, py + ph - 28, 130, 20, Text.literal("+ Agregar atajo"), b -> addEntry()));
        addDrawableChild(new Ui.Btn(px + pw - 14 - 90, py + ph - 28, 90, 20, Text.literal("Listo"), b -> close()));
    }

    // ------------------------------------------------------------ acciones

    private void addEntry() {
        List<Autotext.Entry> es = Autotext.entries();
        if (es.size() >= Autotext.MAX) return;
        es.add(new Autotext.Entry(0, ""));
        Autotext.save();
        scroll = maxScroll();
        clearChildren(); init();
        if (!fields.isEmpty()) setFocused(fields.get(fields.size() - 1));
    }

    private void deleteEntry(int i) {
        List<Autotext.Entry> es = Autotext.entries();
        if (i < 0 || i >= es.size()) return;
        es.remove(i);
        Autotext.save();
        clearChildren(); init();
    }

    // ------------------------------------------------------------ dibujo

    @Override
    public void renderBackground(DrawContext g, int mx, int my, float d) { }

    @Override
    public void render(DrawContext g, int mx, int my, float delta) {
        List<Autotext.Entry> es = Autotext.entries();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        scrollSmooth += (scroll - scrollSmooth) * 0.35f;
        if (Math.abs(scroll - scrollSmooth) < 0.5f) scrollSmooth = scroll;
        addBtn.active = es.size() < Autotext.MAX;

        Ui.gradient(g, 0, 0, width, height, 0xC0070914, 0xE00E0B22, true);
        Ui.card(g, px - 3, py - 3, pw + 6, ph + 6, 0x30000000, 0x188B5CF6);
        Ui.card(g, px, py, pw, ph, 0xF00A0D1C, Ui.BORDER);
        Ui.gradient(g, px + 1, py, pw - 2, 2, Ui.VIOLET, Ui.PINK, false);
        Fx.heading(g, "Autotext", px + 14, py + 12, Ui.TEXT);
        String count = es.size() + "/" + Autotext.MAX;
        Ui.text(g, count, px + pw - 14 - Fx.width(count), py + 12, Ui.DIM);
        Ui.text(g, Ui.fit("Una tecla o un bot\u00f3n del mouse env\u00eda un mensaje o comando (si empieza con /).", pw - 28), px + 14, py + 26, Ui.DIM);
        if (capturing >= 0) Ui.text(g, Ui.fit("Pulsa una tecla o un bot\u00f3n del mouse.  Esc cancela  \u00b7  Supr la quita.", pw - 28), px + 14, py + 38, Ui.ACCENT);
        else if (!Modules.on("autotext")) Ui.text(g, Ui.fit("Autotext est\u00e1 desactivado en Mods: los atajos no se env\u00edan.", pw - 28), px + 14, py + 38, Ui.AMBER);
        else Ui.text(g, "Haz clic en la tecla de una fila para cambiarla.", px + 14, py + 38, Ui.DIM);
        Ui.rect(g, px + 14, listTop - 5, pw - 28, 1, Ui.BORDER);
        Ui.rect(g, px + 14, py + ph - FOOT + 2, pw - 28, 1, Ui.BORDER);

        // los campos de texto siguen a su fila y se ocultan si la fila no se ve entera
        for (int i = 0; i < fields.size(); i++) {
            int y = rowY(i);
            TextFieldWidget f = fields.get(i);
            f.setY(y + 9);
            f.visible = y >= listTop && y + ROW <= listTop + listH;
            if (!f.visible && f.isFocused()) f.setFocused(false);
        }

        if (es.isEmpty()) {
            Ui.centered(g, "No tienes atajos todav\u00eda.", px + pw / 2, listTop + 24, Ui.TEXT);
            Ui.centered(g, Ui.fit("Pulsa \"+ Agregar atajo\" para crear el primero.", pw - 40), px + pw / 2, listTop + 40, Ui.DIM);
        }

        g.enableScissor(px + 1, listTop, px + pw - 1, listTop + listH);
        for (int i = 0; i < es.size(); i++) {
            int y = rowY(i);
            if (y + ROW < listTop || y > listTop + listH) continue;
            Autotext.Entry e = es.get(i);
            boolean cap = capturing == i, hovKey = Ui.inside(mx, my, keyX(), y + 2, KEY_W, 20) && inList(my);
            Ui.card(g, keyX(), y + 2, KEY_W, 20, cap || hovKey ? Ui.PANEL2 : Ui.PANEL, cap ? Ui.ACCENT : hovKey ? Ui.ACCENT_DIM : Ui.BORDER);
            String label = cap ? "Pulsa una tecla..." : Autotext.keyName(e.key);
            Ui.centered(g, Ui.fit(label, KEY_W - 10), keyX() + KEY_W / 2, y + 8, cap ? Ui.ACCENT : e.key == 0 ? Ui.DIM : Ui.TEXT);

            TextFieldWidget f = fields.get(i);
            Ui.card(g, fieldX(), y + 2, fieldW(), 20, 0xFF0A0918, f.isFocused() ? Ui.ACCENT : Ui.BORDER);
            if ((e.msg == null || e.msg.isEmpty()) && !f.isFocused()) Ui.text(g, "Mensaje o /comando", fieldX() + 6, y + 8, Ui.OFF);

            boolean hovDel = Ui.inside(mx, my, delX(), y + 2, DEL_W, 20) && inList(my);
            Ui.card(g, delX(), y + 2, DEL_W, 20, hovDel ? Ui.PANEL2 : Ui.PANEL, hovDel ? Ui.RED : Ui.BORDER);
            int xc = hovDel ? Ui.RED : Ui.DIM;
            for (int k = 0; k < 7; k++) { Ui.rect(g, delX() + 7 + k, y + 8 + k, 1, 1, xc); Ui.rect(g, delX() + 13 - k, y + 8 + k, 1, 1, xc); }
        }
        g.disableScissor();

        int max = maxScroll();
        if (max > 0) {
            int barH = Math.max(18, listH * listH / (listH + max)), barY = listTop + (int) ((listH - barH) * (scrollSmooth / (double) max));
            g.fill(px + pw - 9, listTop, px + pw - 7, listTop + listH, 0x20FFFFFF);
            g.fillGradient(px + pw - 9, barY, px + pw - 7, barY + barH, Ui.VIOLET, Ui.PINK);
        }

        super.render(g, mx, my, delta);
        Ui.paintButtons(g, textRenderer, children());
    }

    // ------------------------------------------------------------ entrada

    @Override
    public boolean mouseClicked(double clickX, double clickY, int clickBtn) {
        double mx = clickX, my = clickY;
        int btn = clickBtn;
        if (capturing >= 0) {
            if (inList(my)) { // cualquier boton del mouse dentro de la lista se asigna al atajo
                List<Autotext.Entry> es = Autotext.entries();
                if (capturing < es.size()) es.get(capturing).key = -100 + btn;
                capturing = -1;
                return true;
            }
            capturing = -1; // clic fuera de la lista (botones de abajo): se cancela y el clic sigue su camino
        }
        if (btn == 0 && inList(my)) {
            int count = Autotext.entries().size();
            for (int i = 0; i < count; i++) {
                int y = rowY(i);
                if (my < y || my >= y + ROW) continue;
                if (Ui.inside(mx, my, keyX(), y + 2, KEY_W, 20)) { setFocused(null); capturing = i; return true; }
                if (Ui.inside(mx, my, delX(), y + 2, DEL_W, 20)) { deleteEntry(i); return true; }
                if (Ui.inside(mx, my, fieldX(), y + 2, fieldW(), 20) && i < fields.size() && fields.get(i).visible) { setFocused(fields.get(i)); return true; }
                break;
            }
            setFocused(null);
            return true;
        }
        return super.mouseClicked(clickX, clickY, clickBtn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        if (vy == 0 || maxScroll() == 0) return false;
        scroll = Math.max(0, Math.min(maxScroll(), scroll + (vy > 0 ? -ROW : ROW)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int key = keyCode;
        if (capturing >= 0) {
            List<Autotext.Entry> es = Autotext.entries();
            if (key == 256) { capturing = -1; return true; }                       // Esc: cancelar
            if (key == 261 || key == 259) { if (capturing < es.size()) es.get(capturing).key = 0; capturing = -1; return true; } // Supr / Retroceso: sin tecla
            if (key > 0) { if (capturing < es.size()) es.get(capturing).key = key; capturing = -1; }   // tecla desconocida (-1): se ignora
            return true;
        }
        if (key == 256) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() { Autotext.save(); }

    @Override
    public void close() { Autotext.save(); if (client != null) client.setScreen(parent); }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public boolean shouldPause() { return false; }
}
