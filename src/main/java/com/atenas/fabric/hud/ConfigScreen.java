package com.atenas.fabric.hud;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.atenas.fabric.gui.FriendsScreen;
import com.atenas.fabric.gui.Ui;
import com.atenas.fabric.net.Friends;
import com.atenas.fabric.shared.Config;

import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Menu de Atenas Client (Right Shift): mismo diseno que AtenasMenu de 1.8.9. Clic: activa/desactiva; engranaje o clic derecho: ajustes. */
public final class ConfigScreen extends Screen {
    // clave de Config -> [nombre visible, activado por defecto]. Sale de Modules: una sola fuente para este menu, los paneles del HUD y /atenas.
    private static final Map<String, Object[]> ITEMS = new LinkedHashMap<>();
    /** Sin portar todavia a 1.21.11: no se muestran para no ofrecer interruptores que no hacen nada (Animaciones 1.7 y Rendimiento). */
    private static final java.util.Set<String> HIDDEN = java.util.Set.of("anim17", "perf");

    // categorias de 1.8.9 (Module.Category): HUD, PvP, Animaciones, Rendimiento, Chat, Utilidades
    private static final String[] CATS = {"Todos", "HUD", "PvP", "Animaciones", "Rendimiento", "Chat", "Utilidades"};
    private static final int[] CAT_COL = {Ui.ACCENT, Ui.CYAN, Ui.RED, Ui.ACCENT, Ui.GREEN, Ui.VIOLET, Ui.AMBER};
    private static final Map<String, Object[]> META = new java.util.HashMap<>();
    static {
        for (Modules.M m : Modules.all().values()) {
            if (HIDDEN.contains(m.key)) continue;
            ITEMS.put(m.key, new Object[] {m.name, m.def});
            META.put(m.key, new Object[] {m.cat, m.desc});
        }
    }

    /** Claves de los modulos que muestra el menu (las usa /atenas). */
    public static java.util.Collection<String> visibleModules() { return ITEMS.keySet(); }

    private static final int SIDE = 104, SIDE_LABELS = 7;
    private int tab, filter, scroll, x0, y0, pw, ph;
    private float scrollSmooth;
    private final Screen parent;
    private String query = "";
    private boolean searchFocus;

    public ConfigScreen() { this(null); }
    public ConfigScreen(Screen parent) { super(Text.literal("Atenas Client")); this.parent = parent; }

    private static int cat(String k) { return (Integer) META.getOrDefault(k, new Object[] {1, ""})[0]; }
    private static String desc(String k) { return (String) META.getOrDefault(k, new Object[] {1, ""})[1]; }
    private boolean on(String key) { Object[] it = ITEMS.get(key); return Config.get(key, (Boolean) it[1]); }

    private void flip(String k) {
        boolean def = (Boolean) ITEMS.get(k)[1];
        boolean on = Config.toggle(k, def);
        if (k.equals("fullbright") && !on && client != null && client.player != null) {
            client.player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.NIGHT_VISION);
        }
    }

    private void geometry() {
        pw = Math.min(600, width - 24); ph = Math.min(330, height - 24);
        x0 = (width - pw) / 2; y0 = (height - ph) / 2;
    }

    @Override
    protected void init() {
        geometry();
        Friends.sync(false, "");
    }

    private List<String> visibleKeys() {
        List<String> out = new ArrayList<>();
        String q = query.trim().toLowerCase(java.util.Locale.ROOT);
        for (String k : ITEMS.keySet()) {
            if (filter > 0 && cat(k) != filter) continue;
            String n = ((String) ITEMS.get(k)[0]).toLowerCase(java.util.Locale.ROOT);
            if (!q.isEmpty() && !n.contains(q) && !desc(k).toLowerCase(java.util.Locale.ROOT).contains(q)) continue;
            out.add(k);
        }
        return out;
    }

    private int cols(int bw) { return bw >= 540 ? 3 : bw >= 300 ? 2 : 1; }

    @Override
    public void close() { client.setScreen(parent); }

    /** Engranaje de TODOS los modulos: Autotext abre su editor de atajos; el resto, su panel de ajustes (con scroll). */
    private void openSettings(String k) {
        if (k.equals("autotext")) client.setScreen(new AutotextScreen(this));
        else client.setScreen(new ModSettingsScreen(this, k, (String) ITEMS.get(k)[0]));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double clickX, double clickY, int clickBtn) {
        geometry();
        int mx = (int) clickX, my = (int) clickY, btn = clickBtn;
        int bx = x0 + SIDE + 10, by = y0 + 10, bw = pw - SIDE - 20, bh = ph - 20;
        searchFocus = false;
        if (btn != 0 && btn != 1) return true;
        if (btn == 0) {
            int step = Math.max(17, Math.min(25, (ph - 78) / SIDE_LABELS)), rowH = step - 3, y = y0 + 50;
            for (int i = 0; i < SIDE_LABELS; i++, y += step) {
                if (Ui.inside(mx, my, x0 + 6, y, SIDE - 12, rowH)) {
                    switch (i) {
                        case 0: tab = 0; break;
                        case 1: com.atenas.fabric.cosmetics.CosmeticSync.refreshNow(); client.setScreen(new com.atenas.fabric.cosmetics.CosmeticsScreen(this)); break;
                        case 2: client.setScreen(new HudEditorScreen(this)); break;
                        case 3: client.setScreen(new FriendsScreen(this)); break;
                        case 4: client.setScreen(new WaypointScreen(this)); break;
                        case 5: client.setScreen(new TeamScreen(this)); break;
                        default: tab = 1; break;
                    }
                    return true;
                }
            }
        }
        if (tab != 0) return true;
        int sw = 120, sx = bx + bw - sw;
        if (btn == 0 && Ui.inside(mx, my, sx, by - 2, sw, 16)) { searchFocus = true; return true; }
        if (btn == 0) {
            int cx = bx, chipY = by + 20;
            for (int c = 0; c < CATS.length; c++) {
                int w = com.atenas.fabric.gui.Fx.width(CATS[c]) + 16;
                if (cx + w > bx + bw) break;
                if (Ui.inside(mx, my, cx, chipY, w, 15)) { filter = c; scroll = 0; return true; }
                cx += w + 4;
            }
        }
        int top = by + 42, listH = bh - 42, cols = cols(bw), gap = 8, cw = (bw - 6 - gap * (cols - 1)) / cols, chh = 56;
        List<String> keys = visibleKeys();
        int off = Math.round(scrollSmooth);
        if (my >= top && my < top + listH) for (int i = 0; i < keys.size(); i++) {
            int x = bx + (i % cols) * (cw + gap), yy = top + (i / cols) * (chh + gap) - off;
            if (!Ui.inside(mx, my, x, yy, cw, chh)) continue;
            String k = keys.get(i);
            if (btn == 1) { openSettings(k); return true; }
            if (Ui.inside(mx, my, x + cw - 22, yy + chh - 20, 20, 20)) { openSettings(k); return true; }
            flip(k);
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        if (tab != 0 || vy == 0) return true;
        int bh = ph - 20, bw = pw - SIDE - 20, cols = cols(bw), rows = (visibleKeys().size() + cols - 1) / cols;
        int total = Math.max(0, rows * 64 - 8), listH = bh - 42;
        scroll = Math.max(0, Math.min(Math.max(0, total - listH), scroll + (vy > 0 ? -17 : 17)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int key = keyCode;
        if (searchFocus && tab == 0) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { searchFocus = false; return true; }
            if (key == GLFW.GLFW_KEY_BACKSPACE) { if (!query.isEmpty()) query = query.substring(0, query.length() - 1); scroll = 0; return true; }
            return true; // el buscador se queda con las teclas
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || Mods.isMenuKey(key)) { close(); return true; }
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!searchFocus || tab != 0 || query.length() >= 24) return true;
        int cp = (int) chr;
        if (cp >= 32 && cp != 127) { query += new String(Character.toChars(cp)); scroll = 0; }
        return true;
    }

    @Override
    public void removed() { Config.save(); }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void renderBackground(DrawContext g, int mx, int my, float d) { }

    // ----------------------------------------------------------------- dibujo

    @Override
    public void render(DrawContext g, int mx, int my, float delta) {
        geometry();
        g.fillGradient(0, 0, width, height, 0xB0070610, 0xC0140A2A);
        Ui.card(g, x0 - 3, y0 - 3, pw + 6, ph + 6, 0x30000000, 0x18A78BFA);
        Ui.card(g, x0, y0, pw, ph, Ui.BG, Ui.BORDER);
        // barra lateral
        g.fill(x0 + 1, y0 + 2, x0 + SIDE, y0 + ph - 1, Ui.SIDE);
        g.fill(x0 + SIDE, y0 + 2, x0 + SIDE + 1, y0 + ph - 1, Ui.BORDER);
        Ui.logo(g, x0 + 10, y0 + 12, 28);
        com.atenas.fabric.gui.Fx.heading(g, "ATENAS", x0 + 44, y0 + 14, Ui.ACCENT);
        com.atenas.fabric.gui.Fx.heading(g, "CLIENT", x0 + 44, y0 + 25, Ui.DIM);
        String[] labels = {"Mods", "Cosm\u00e9ticos", "Editor HUD", Friends.badge("Amigos"), "Waypoints", "Equipo", "Acerca de"};
        int step = Math.max(17, Math.min(25, (ph - 78) / labels.length)), rowH = step - 3, y = y0 + 50;
        for (int i = 0; i < labels.length; i++, y += step) {
            boolean active = (i == 0 && tab == 0) || (i == labels.length - 1 && tab == 1);
            boolean hover = Ui.inside(mx, my, x0 + 6, y, SIDE - 12, rowH);
            if (active) { Ui.card(g, x0 + 6, y, SIDE - 12, rowH, Ui.PANEL2, Ui.BORDER); Ui.gradient(g, x0 + 6, y + 3, 2, rowH - 6, Ui.VIOLET, Ui.PINK, true); }
            else if (hover) Ui.card(g, x0 + 6, y, SIDE - 12, rowH, Ui.HOVER, Ui.HOVER);
            Ui.text(g, Ui.fit(labels[i], SIDE - 28), x0 + 16, y + (rowH - 8) / 2, active || hover ? Ui.TEXT : Ui.DIM);
        }
        if (y + 6 <= y0 + ph - 24) {
            Ui.hgrad(g, x0 + 10, y0 + ph - 24, SIDE - 20, 1, Ui.ACCENT_DIM, 0x006D4FC7);
            Ui.text(g, "v" + com.atenas.fabric.AtenasFabricClient.VERSION, x0 + 12, y0 + ph - 16, Ui.DIM);
        }
        Ui.gradient(g, x0 + 1, y0, pw - 2, 2, Ui.VIOLET, Ui.PINK, false);

        int bx = x0 + SIDE + 10, by = y0 + 10, bw = pw - SIDE - 20, bh = ph - 20;
        if (tab == 1) { drawAbout(g, bx, by, bw, bh); return; }
        com.atenas.fabric.gui.Fx.scaled(g, "Mods", bx, by, 1.5f, Ui.TEXT, false);
        // buscador
        int sw = 120, sx = bx + bw - sw;
        Ui.card(g, sx, by - 2, sw, 16, Ui.PANEL, searchFocus ? Ui.ACCENT : Ui.BORDER);
        if (query.isEmpty() && !searchFocus) Ui.text(g, "Buscar...", sx + 6, by + 2, Ui.OFF);
        else {
            String shown = Ui.fit(query, sw - 18);
            Ui.text(g, shown, sx + 6, by + 2, Ui.TEXT);
            if (searchFocus && (System.currentTimeMillis() / 500) % 2 == 0) g.fill(sx + 6 + com.atenas.fabric.gui.Fx.width(shown), by + 1, sx + 7 + com.atenas.fabric.gui.Fx.width(shown), by + 11, Ui.TEXT);
        }
        // filtros por categoria
        int cx = bx, chipY = by + 20;
        for (int c = 0; c < CATS.length; c++) {
            int w = com.atenas.fabric.gui.Fx.width(CATS[c]) + 16;
            if (cx + w > bx + bw) break;
            boolean onC = c == filter, hov = Ui.inside(mx, my, cx, chipY, w, 15);
            Ui.card(g, cx, chipY, w, 15, onC ? Ui.PANEL2 : hov ? Ui.HOVER : Ui.PANEL, onC ? CAT_COL[c] : Ui.BORDER);
            if (c > 0) Ui.rect(g, cx + 5, chipY + 6, 3, 3, CAT_COL[c]);
            Ui.text(g, CATS[c], cx + (c == 0 ? 8 : 11), chipY + 4, onC ? Ui.TEXT : Ui.DIM);
            cx += w + 4;
        }
        // cuadricula de tarjetas
        int top = by + 42, listH = bh - 42, cols = cols(bw), gap = 8, cw = (bw - 6 - gap * (cols - 1)) / cols, chh = 56;
        List<String> keys = visibleKeys();
        int rows = (keys.size() + cols - 1) / cols, total = Math.max(0, rows * (chh + gap) - gap);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - listH)));
        scrollSmooth += (scroll - scrollSmooth) * 0.35f;
        if (Math.abs(scroll - scrollSmooth) < 0.5f) scrollSmooth = scroll;
        int off = Math.round(scrollSmooth);
        if (keys.isEmpty()) Ui.centered(g, "No hay mods con ese filtro.", bx + bw / 2, top + 30, Ui.DIM);
        g.enableScissor(bx, top, bx + bw, top + listH);
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            int x = bx + (i % cols) * (cw + gap), yy = top + (i / cols) * (chh + gap) - off;
            if (yy + chh < top || yy > top + listH) continue;
            int col = CAT_COL[cat(k)];
            boolean onK = on(k), has = true, hov = Ui.inside(mx, my, x, yy, cw, chh) && my >= top && my < top + listH;
            Ui.card(g, x, yy, cw, chh, hov ? Ui.PANEL2 : Ui.PANEL, onK ? col : hov ? Ui.ACCENT_DIM : Ui.BORDER);
            if (onK) g.fillGradient(x + 1, yy + 1, x + cw - 1, yy + chh - 1, 0x00000000, (0x26 << 24) | (col & 0xFFFFFF));
            String name = (String) ITEMS.get(k)[0];
            Ui.rect(g, x + 8, yy + 8, 22, 22, (0x40 << 24) | (col & 0xFFFFFF));
            Ui.border(g, x + 8, yy + 8, 22, 22, col);
            Ui.centered(g, name.substring(0, 1).toUpperCase(java.util.Locale.ROOT), x + 19, yy + 15, Ui.TEXT);
            Ui.text(g, Ui.fit(name, cw - 36 - 34), x + 37, yy + 9, Ui.TEXT);
            Ui.text(g, CATS[cat(k)], x + 37, yy + 20, col);
            Ui.toggle(g, x + cw - 30, yy + 8, onK);
            List<String> d = com.atenas.fabric.gui.Fx.wrap(desc(k), cw - 16 - (has ? 16 : 0), 2);
            for (int l = 0; l < d.size(); l++) Ui.text(g, d.get(l), x + 8, yy + 34 + l * 10, Ui.DIM);
            if (has) {
                boolean gh = Ui.inside(mx, my, x + cw - 20, yy + chh - 18, 16, 14) && my >= top && my < top + listH;
                Ui.sliders(g, x + cw - 17, yy + chh - 16, gh ? Ui.ACCENT : Ui.OFF);
            }
        }
        g.disableScissor();
        if (total > listH) {
            int barH = Math.max(18, listH * listH / total), barY = top + (int) ((listH - barH) * (scrollSmooth / (double) (total - listH)));
            g.fill(bx + bw - 3, top, bx + bw - 1, top + listH, 0x20FFFFFF);
            g.fillGradient(bx + bw - 3, barY, bx + bw - 1, barY + barH, Ui.VIOLET, Ui.PINK);
        }
    }

    private void drawAbout(DrawContext g, int bx, int by, int bw, int bh) {
        com.atenas.fabric.gui.Fx.scaled(g, "Acerca de", bx, by, 1.5f, Ui.TEXT, false);
        int top = by + 20;
        Ui.card(g, bx, top, bw, bh - 20, Ui.PANEL, Ui.BORDER);
        Ui.logo(g, bx + 14, top + 14, 56);
        com.atenas.fabric.gui.Fx.scaled(g, "Atenas Client", bx + 82, top + 18, 2f, Ui.TEXT, false);
        Ui.text(g, "Versi\u00f3n " + com.atenas.fabric.AtenasFabricClient.VERSION + "  \u00b7  Minecraft 1.21.8 (Fabric)", bx + 82, top + 40, Ui.DIM);
        String[] info = {
            "RShift  \u2192  abrir / cerrar este men\u00fa   \u00b7   Shift+Tab  \u2192  amigos y chat",
            "Mods \u2192 elige un m\u00f3dulo para ver sus ajustes.",
            "Editor HUD \u2192 arrastra cada m\u00f3dulo y usa la rueda para su tama\u00f1o.",
            "Sin ventajas injustas: solo mejoras visuales y de utilidad."
        };
        int y = top + 78;
        for (String s : info) { Ui.text(g, s, bx + 14, y, Ui.TEXT); y += 13; }
    }

    @Override
    public boolean shouldPause() { return false; }
}
