package com.atenas.fabric.hud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.atenas.fabric.gui.Fx;
import com.atenas.fabric.gui.Ui;
import com.atenas.fabric.net.TeamSync;
import com.atenas.fabric.shared.Config;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

/**
 * Lista de equipo (H, o el engranaje de Equipo): a la izquierda los nicks que cuentan como companeros (los guarda {@link TeamStore},
 * el mismo archivo que 1.8.9) y a la derecha los jugadores del servidor para anadir con un clic.
 */
public final class TeamScreen extends Screen {
    private static final int ROW = 18;
    private final Screen parent;
    private int px, py, pw, ph, colW, leftX, rightX, listY, listH, scrollL, scrollR;
    private TextFieldWidget input;
    private String message = "";
    private long messageAt;

    public TeamScreen(Screen parent) { super(Text.literal("Equipo")); this.parent = parent; }

    @Override
    protected void init() {
        pw = Math.min(430, width - 16);
        ph = Math.min(270, height - 16);
        px = (width - pw) / 2; py = (height - ph) / 2;
        colW = (pw - 30) / 2;
        leftX = px + 10; rightX = leftX + colW + 10;
        listY = py + 62; listH = ph - 62 - 38;
        String keep = input == null ? "" : input.getText();
        input = new TextFieldWidget(textRenderer, leftX + 5, py + 39, colW - 62 - 10, 8, Text.empty());
        input.setDrawsBackground(false);
        input.setMaxLength(16);
        input.setText(keep);
        addDrawableChild(input);
        addDrawableChild(new Ui.Btn(leftX + colW - 58, py + 32, 58, 20, Text.literal("A\u00f1adir"), b -> addTyped()));
        addDrawableChild(new Ui.Btn(rightX, py + 32, colW, 20, Text.literal(toggleLabel()), b -> {
            Config.toggle("team", Modules.def("team"));
            b.setMessage(Text.literal(toggleLabel()));
        }));
        addDrawableChild(new Ui.Btn(px + pw - 10 - 90, py + ph - 28, 90, 20, Text.literal("Cerrar"), b -> close()));
        setFocused(input);
    }

    private static String toggleLabel() { return Modules.on("team") ? "Team View: activado" : "Team View: apagado (activar)"; }

    private void say(String s) { message = s; messageAt = System.currentTimeMillis(); }

    private void addTyped() {
        String n = input.getText().trim();
        if (!TeamStore.valid(n)) { say("Escribe un nick v\u00e1lido (3 a 16 letras, n\u00fameros o _)."); return; }
        if (TeamStore.has(n)) { say("Ya est\u00e1 en tu lista."); return; }
        if (TeamStore.add(n)) { input.setText(""); say(""); }
        else say("Tu lista est\u00e1 llena.");
    }

    /** Jugadores del servidor (lista TAB) que todavia no estan en tu lista. */
    private List<String> online() {
        List<String> out = new ArrayList<>();
        if (client == null || client.getNetworkHandler() == null) return out;
        String me = client.getSession().getUsername();
        for (PlayerListEntry e : client.getNetworkHandler().getPlayerList()) {
            String n = e.getProfile() == null ? null : e.getProfile().name();
            if (n != null && !n.equalsIgnoreCase(me) && TeamStore.valid(n) && !TeamStore.has(n) && !out.contains(n)) out.add(n);
        }
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private int rows() { return Math.max(1, listH / ROW); }

    // ------------------------------------------------------------ dibujo

    @Override
    public void renderBackground(DrawContext g, int mx, int my, float d) { }

    @Override
    public void render(DrawContext g, int mx, int my, float delta) {
        List<String> mine = TeamStore.names(), on = online();
        scrollL = Math.max(0, Math.min(scrollL, Math.max(0, mine.size() - rows())));
        scrollR = Math.max(0, Math.min(scrollR, Math.max(0, on.size() - rows())));

        Ui.gradient(g, 0, 0, width, height, 0xC0070914, 0xE00E0B22, true);
        Ui.card(g, px - 3, py - 3, pw + 6, ph + 6, 0x30000000, 0x188B5CF6);
        Ui.card(g, px, py, pw, ph, 0xF00A0D1C, Ui.BORDER);
        Ui.gradient(g, px + 1, py, pw - 2, 2, Ui.CYAN, Ui.VIOLET, false);
        Fx.heading(g, "Equipo", px + 12, py + 12, Ui.ACCENT);
        String hint = "H abre esto";
        Ui.text(g, hint, px + pw - 12 - Fx.width(hint), py + 12, Ui.DIM);
        Ui.text(g, Ui.fit("Mi lista (nicks)", colW), leftX, py + 22, Ui.DIM);
        Ui.text(g, Ui.fit("Jugadores en este servidor (clic para a\u00f1adir)", colW), rightX, py + 22, Ui.DIM);
        Ui.card(g, leftX, py + 34, colW - 62, 18, 0xFF0A0918, input.isFocused() ? Ui.ACCENT : Ui.BORDER);
        if (input.getText().isEmpty() && !input.isFocused()) Ui.text(g, "Nick del jugador", leftX + 5, py + 39, Ui.OFF);

        // columna izquierda: mi lista
        Ui.card(g, leftX - 2, listY - 2, colW + 4, listH + 4, Ui.PANEL, Ui.BORDER);
        g.enableScissor(leftX, listY, leftX + colW, listY + listH);
        if (mine.isEmpty()) {
            int yy = listY + 6;
            for (String l : Fx.wrap("Vac\u00eda. Los del equipo del servidor y tus amigos de Atenas salen solos.", colW - 12, 4)) { Ui.text(g, l, leftX + 6, yy, Ui.DIM); yy += 11; }
        }
        for (int i = scrollL; i < mine.size() && (i - scrollL) * ROW + ROW <= listH; i++) {
            int y = listY + (i - scrollL) * ROW;
            String n = mine.get(i);
            boolean seen = TeamSync.mates().containsKey(n.toLowerCase(Locale.ROOT));
            boolean hx = Ui.inside(mx, my, leftX + colW - 20, y, 18, ROW - 2);
            Ui.rect(g, leftX + 6, y + 6, 6, 6, seen ? Ui.GREEN : Ui.OFF);
            Ui.text(g, Ui.fit(n, colW - 50), leftX + 18, y + 4, Ui.TEXT);
            int xc = hx ? Ui.RED : Ui.DIM;
            for (int k = 0; k < 6; k++) { Ui.rect(g, leftX + colW - 16 + k, y + 6 + k, 1, 1, xc); Ui.rect(g, leftX + colW - 11 - k, y + 6 + k, 1, 1, xc); }
        }
        g.disableScissor();

        // columna derecha: jugadores del servidor
        Ui.card(g, rightX - 2, listY - 2, colW + 4, listH + 4, Ui.PANEL, Ui.BORDER);
        g.enableScissor(rightX, listY, rightX + colW, listY + listH);
        if (on.isEmpty()) {
            int yy = listY + 6;
            for (String l : Fx.wrap("No hay otros jugadores (o no est\u00e1s en un servidor).", colW - 12, 4)) { Ui.text(g, l, rightX + 6, yy, Ui.DIM); yy += 11; }
        }
        for (int i = scrollR; i < on.size() && (i - scrollR) * ROW + ROW <= listH; i++) {
            int y = listY + (i - scrollR) * ROW;
            boolean hov = Ui.inside(mx, my, rightX, y, colW, ROW - 2);
            if (hov) Ui.rect(g, rightX, y, colW, ROW - 2, 0x33FFFFFF);
            Ui.text(g, Ui.fit(on.get(i), colW - 30), rightX + 6, y + 4, hov ? Ui.ACCENT : Ui.TEXT);
            int pc = hov ? Ui.GREEN : Ui.DIM;
            Ui.rect(g, rightX + colW - 17, y + 8, 7, 1, pc);
            Ui.rect(g, rightX + colW - 14, y + 5, 1, 7, pc);
        }
        g.disableScissor();

        // estado del servicio y aviso de privacidad
        String err = TeamSync.error();
        boolean bad = !err.isEmpty(), ok = !bad && TeamSync.active();
        Ui.text(g, bad ? "Servicio: sin conexi\u00f3n" : ok ? "Servicio: conectado" : "Servicio: en espera (entra a un servidor)", leftX, py + ph - 24, bad ? Ui.RED : ok ? Ui.GREEN : Ui.DIM);
        Ui.text(g, Ui.fit("Solo ven tu posici\u00f3n tus amigos de Atenas y quienes te tienen en su lista.", pw - 120), leftX, py + ph - 14, Ui.DIM);
        if (!message.isEmpty() && System.currentTimeMillis() - messageAt < 4000L) Ui.text(g, Ui.fit(message, colW), leftX, py + 54, Ui.AMBER);

        super.render(g, mx, my, delta);
        Ui.paintButtons(g, textRenderer, children());
    }

    // ------------------------------------------------------------ entrada

    @Override
    public boolean mouseClicked(double clickX, double clickY, int clickBtn) {
        double mx = clickX, my = clickY;
        if (clickBtn == 0 && Ui.inside(mx, my, leftX, py + 34, colW - 62, 18)) { setFocused(input); return true; }
        if (super.mouseClicked(clickX, clickY, clickBtn)) return true; // botones
        if (clickBtn != 0) return false;
        List<String> mine = TeamStore.names();
        for (int i = scrollL; i < mine.size(); i++) {
            int y = listY + (i - scrollL) * ROW;
            if (y + ROW > listY + listH) break;
            if (Ui.inside(mx, my, leftX + colW - 20, y, 18, ROW - 2)) { TeamStore.remove(mine.get(i)); return true; }
        }
        List<String> on = online();
        for (int i = scrollR; i < on.size(); i++) {
            int y = listY + (i - scrollR) * ROW;
            if (y + ROW > listY + listH) break;
            if (Ui.inside(mx, my, rightX, y, colW, ROW - 2)) {
                if (!TeamStore.add(on.get(i))) say("Tu lista est\u00e1 llena.");
                return true;
            }
        }
        setFocused(null);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        if (vy == 0) return false;
        int d = vy > 0 ? -1 : 1;
        if (mx < rightX - 5) scrollL = Math.max(0, scrollL + d);
        else scrollR = Math.max(0, scrollR + d);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int key = keyCode;
        if (key == 256) { close(); return true; }
        if ((key == 257 || key == 335) && this.input.isFocused()) { addTyped(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        Element f = getFocused();
        if (f == input) { // nicks: solo letras, numeros y _
            int cp = (int) chr;
            boolean okChar = (cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z') || (cp >= '0' && cp <= '9') || cp == '_';
            if (!okChar) return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void close() { if (client != null) client.setScreen(parent); }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public boolean shouldPause() { return false; }
}
