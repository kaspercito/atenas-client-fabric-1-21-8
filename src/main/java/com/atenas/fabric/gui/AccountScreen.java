package com.atenas.fabric.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.atenas.fabric.net.Accounts;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Cuentas dentro del juego (la misma AccountScreen de 1.8.9):
 *  - cambiar de cuenta (las sin conexion al instante; las de Microsoft reinician el juego y el launcher lo abre otra vez),
 *  - agregar una cuenta premium (el launcher abre el inicio de sesion de Microsoft),
 *  - quitar una cuenta guardada o cerrar sesion de la activa,
 *  - entrar con un nick sin conexion.
 * Todo lo que toca la lista de cuentas lo hace el launcher (ver {@link Accounts}); esta pantalla solo pide y espera la respuesta.
 */
public final class AccountScreen extends Screen {

    private static final int ROW = 28, GAP = 3;
    private static final long ACK_MS = 6000L;          // si el launcher no toma el pedido en este tiempo, no esta abierto
    private static final long LOGIN_MS = 6 * 60000L;   // tiempo maximo esperando el inicio de sesion de Microsoft

    private static final class Hit {
        final int x, y, w, h;
        final Runnable click;
        Hit(int x, int y, int w, int h, Runnable click) { this.x = x; this.y = y; this.w = w; this.h = h; this.click = click; }
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    /** Ventana de confirmacion encima de la pantalla. */
    private static final class Confirm {
        final String title, text, okLabel;
        final boolean danger;
        final Runnable onOk;
        Confirm(String title, String text, String okLabel, boolean danger, Runnable onOk) {
            this.title = title; this.text = text; this.okLabel = okLabel; this.danger = danger; this.onOk = onOk;
        }
    }

    /** Pedido enviado al launcher que todavia no tiene respuesta. */
    private static final class Pending {
        final String id, label;
        final boolean slow;          // true = espera al usuario (ventana de Microsoft)
        final Runnable onOk;
        final long since = System.currentTimeMillis();
        Pending(String id, String label, boolean slow, Runnable onOk) { this.id = id; this.label = label; this.slow = slow; this.onOk = onOk; }
    }

    private final Screen parent;
    private final List<Hit> hits = new ArrayList<>();
    private List<Accounts.Entry> list = new ArrayList<>();
    private TextFieldWidget nameField;
    private Confirm confirm;
    private Pending pending;
    private Accounts.Entry switchTarget;
    private String notice = "";
    private boolean noticeBad;
    private long listStamp;
    private int scroll, rows, px, py, pw, ph, listTop, premiumY, fieldY, bottomY, ticks;

    public AccountScreen(Screen parent) { super(Text.literal("Cuenta")); this.parent = parent; }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    protected void init() {
        reloadList();
        pw = Math.min(340, width - 16);
        rows = Math.max(1, Math.min(list.size(), 4));
        // alto = cabecera + lista + boton premium + bloque "otro nombre" + fila inferior (aviso + Volver)
        while (true) {
            ph = 50 + rows * (ROW + GAP) + 4 + 20 + 8 + 11 + 22 + 8 + 20 + 10;
            if (ph <= height - 8 || rows == 1) break;
            rows--;
        }
        if (ph > height - 4) ph = height - 4;
        px = (width - pw) / 2;
        py = Math.max(2, (height - ph) / 2);
        listTop = py + 50;
        premiumY = listTop + rows * (ROW + GAP) + 4;
        fieldY = premiumY + 20 + 8 + 11;
        bottomY = fieldY + 22 + 8;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - rows)));

        String keep = nameField == null ? "" : nameField.getText();
        clearChildren();
        // misma geometria que la caja que se dibuja: texto centrado en vertical y con aire a los lados
        nameField = new TextFieldWidget(textRenderer, px + 14 + 5, fieldY + (22 - 8) / 2, pw - 28 - 78 - 10, 8, Text.empty());
        nameField.setDrawsBackground(false);
        nameField.setMaxLength(16);
        nameField.setText(keep);
        addDrawableChild(nameField);

        addDrawableChild(new Ui.Btn(px + pw - 14 - 72, fieldY, 72, 22, Text.literal("Entrar"), b -> { if (!modal()) enterOffline(); }));
        addDrawableChild(new Ui.Btn(px + pw - 14 - 90, bottomY, 90, 20, Text.literal("Volver"), b -> { if (!modal()) back(); }));
    }

    private void reloadList() {
        list = Accounts.list();
        listStamp = Accounts.listStamp();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - Math.max(1, rows))));
    }

    @Override
    public void tick() {
        ticks++;
        // el launcher agrego o quito una cuenta: se vuelve a leer la lista (y se reacomoda la pantalla si cambia el alto)
        if (ticks % 10 == 0 && Accounts.listStamp() != listStamp) {
            int before = list.size();
            reloadList();
            if (list.size() != before) init();
        }
        if (pending != null && ticks % 4 == 0) checkPending();
    }

    // ------------------------------------------------------------ acciones

    private void say(String text, boolean bad) { notice = text; noticeBad = bad; }

    private void back() { close(); }

    /** Envia un pedido al launcher y se queda esperando su respuesta. */
    private void ask(String action, String target, String name, String label, boolean slow, Runnable onOk) {
        if (pending != null) return;
        try {
            String id = Accounts.request(action, target, name);
            pending = new Pending(id, label, slow, onOk);
            say("", false);
        } catch (IOException e) {
            say(e.getMessage(), true);
        }
    }

    private void checkPending() {
        Pending p = pending;
        Accounts.Result r = Accounts.result(p.id);
        if (r != null) {
            pending = null;
            say(!r.message.isEmpty() ? r.message : (r.ok ? "Listo" : "No se pudo completar"), !r.ok);
            if (r.ok) {
                int before = list.size();
                reloadList();
                if (list.size() != before) init();
                if (p.onOk != null) p.onOk.run();
            }
            return;
        }
        long age = System.currentTimeMillis() - p.since;
        if (Accounts.requestQueued() && age > ACK_MS) { // nadie lo tomo: el launcher esta cerrado
            Accounts.cancelRequest();
            pending = null;
            say("El launcher no responde. Abrelo y vuelve a intentar.", true);
        } else if (age > (p.slow ? LOGIN_MS : ACK_MS * 2)) {
            pending = null;
            say("Se acabo el tiempo de espera.", true);
        }
    }

    private void useAccount(Accounts.Entry e) {
        if (Accounts.isActive(e)) return;
        if (e.offline()) {
            String err = Accounts.switchToOffline(e.name);
            if (err != null) say(err, true);
            else say("Ahora juegas como " + e.name, false);
        } else {
            switchTarget = e;
            confirm = new Confirm("Entrar como §e" + e.name,
                    "El juego se va a cerrar y el launcher lo abre otra vez con esa cuenta.", "Reiniciar", false, this::switchMicrosoft);
        }
    }

    private void switchMicrosoft() {
        Accounts.Entry e = switchTarget;
        switchTarget = null;
        if (e == null) return;
        String err = Accounts.requestLauncherSwitch(e);
        if (err != null) { say(err, true); return; }
        if (client != null) client.scheduleStop(); // el launcher activa la cuenta y abre el juego otra vez
    }

    private void addPremium() {
        if (!Accounts.launchedByLauncher()) { say("Abre el juego desde el launcher para agregar una cuenta premium.", true); return; }
        ask("add-microsoft", null, null, "Inicia sesion en la ventana de Microsoft", true, null);
    }

    /** Quitar una cuenta guardada que no esta en uso. */
    private void askRemove(final Accounts.Entry e) {
        if (!Accounts.launchedByLauncher()) { say("Abre el juego desde el launcher para quitar cuentas.", true); return; }
        confirm = new Confirm("Quitar §e" + e.name,
                "Se borra de la lista del launcher. Tu cuenta no se elimina: puedes agregarla otra vez cuando quieras.", "Quitar", true,
                () -> ask("remove", e.id, null, "Quitando " + e.name, false, null));
    }

    /** Cerrar sesion de la cuenta que esta jugando: se quita del launcher y el juego se cierra. */
    private void askLogout(final Accounts.Entry e) {
        if (!Accounts.launchedByLauncher()) { say("Abre el juego desde el launcher para cerrar sesion.", true); return; }
        confirm = new Confirm("Cerrar sesion de §e" + e.name,
                "Se quita esta cuenta del launcher y el juego se cierra. Para volver a jugar con ella tendras que iniciar sesion otra vez.", "Cerrar sesion", true,
                () -> ask("remove", e.id, null, "Cerrando sesion", false, () -> { if (client != null) client.scheduleStop(); }));
    }

    private void enterOffline() {
        String n = nameField.getText().trim();
        String err = Accounts.switchToOffline(n);
        if (err != null) say(err, true);
        else { say("Ahora juegas como " + n + " (sin conexion)", false); nameField.setText(""); }
    }

    // ------------------------------------------------------------ dibujo

    private static String kindLabel(Accounts.Entry e) { return e.offline() ? "Sin conexion" : "Premium"; }

    private boolean modal() { return confirm != null || pending != null; }

    /** Boton chico de la lista: registra su zona de clic. */
    private void small(DrawContext g, String label, int x, int y, int w, int h, int mx, int my, int hoverColor, boolean dim, Runnable r) {
        boolean over = !modal() && Ui.inside(mx, my, x, y, w, h);
        Ui.smallButton(g, label, x, y, w, h, over, hoverColor, dim);
        hits.add(new Hit(x, y, w, h, r));
    }

    @Override
    public void renderBackground(DrawContext g, int mx, int my, float d) { }

    @Override
    public void render(DrawContext g, int mx, int my, float pt) {
        hits.clear();
        if (client != null && client.world == null) {
            // el menu de atras se ve apagado: asi se entiende que esto es una ventana encima
            Ui.gradient(g, 0, 0, width, height, 0xFF080A14, 0xFF171C3C, true);
        }
        Ui.rect(g, 0, 0, width, height, 0x99000000);
        Ui.card(g, px - 3, py - 3, pw + 6, ph + 6, 0x30000000, 0x188B5CF6);
        Ui.card(g, px, py, pw, ph, 0xF00A0D1C, Ui.BORDER);
        Ui.gradient(g, px + 1, py, pw - 2, 2, Ui.ACCENT_DIM, Ui.ACCENT, false);

        Fx.scaled(g, "CUENTAS", px + pw / 2f, py + 10, 1.5f, Ui.ACCENT, true);
        String cur = Accounts.currentName();
        Ui.centered(g, "Jugando como §f" + cur, px + pw / 2, py + 28, Ui.DIM);
        Ui.rect(g, px + 14, py + 41, pw - 28, 1, Ui.BORDER);

        boolean viaLauncher = Accounts.launchedByLauncher();

        // lista de cuentas
        if (list.isEmpty()) {
            Ui.centered(g, "El launcher todavia no guardo cuentas.", px + pw / 2, listTop + 4, Ui.DIM);
            Ui.centered(g, "Agrega una premium o entra con un nombre.", px + pw / 2, listTop + 16, Ui.DIM);
        }
        int from = scroll, to = Math.min(list.size(), scroll + rows);
        for (int i = from; i < to; i++) {
            final Accounts.Entry e = list.get(i);
            int y = listTop + (i - from) * (ROW + GAP);
            int x = px + 14, w = pw - 28 - (list.size() > rows ? 8 : 0);
            boolean active = Accounts.isActive(e);
            boolean over = !modal() && Ui.inside(mx, my, x, y, w, ROW);
            Ui.rect(g, x, y, w, ROW, over ? Ui.PANEL2 : Ui.PANEL);
            Ui.border(g, x, y, w, ROW, active ? Ui.ACCENT_DIM : over ? Ui.VIOLET : Ui.BORDER);
            Ui.rect(g, x, y, 2, ROW, active ? Ui.VIOLET : Ui.ACCENT_DIM);
            // cara del jugador (o su inicial mientras llega)
            if (!Faces.draw(g, e.name, x + 8, y + 5, 18, 1f, e.offline())) {
                Ui.rect(g, x + 8, y + 5, 18, 18, active ? Ui.VIOLET : Ui.ACCENT_DIM);
                Ui.centered(g, e.name.substring(0, 1).toUpperCase(Locale.ROOT), x + 17, y + 10, Ui.INK);
            }

            int by = y + 6, bh = 16;
            int right = x + w - 6;
            if (active) {
                int cw = 78;
                small(g, "Cerrar sesion", right - cw, by, cw, bh, mx, my, Ui.RED, !viaLauncher, () -> askLogout(e));
                String t = "● Activa";
                int tw = Fx.width(t);
                Ui.text(g, t, right - cw - 6 - tw, y + 10, Ui.GREEN);
                right -= cw + 6 + tw + 6;
            } else {
                small(g, "x", right - 18, by, 18, bh, mx, my, Ui.RED, !viaLauncher, () -> askRemove(e));
                small(g, "Usar", right - 18 - 4 - 44, by, 44, bh, mx, my, Ui.ACCENT, false, () -> useAccount(e));
                right -= 18 + 4 + 44 + 6;
            }
            int nameW = Math.max(30, right - (x + 34));
            Ui.text(g, Fx.fit(e.name, nameW), x + 34, y + 6, active ? Ui.ACCENT : Ui.TEXT);
            Ui.text(g, kindLabel(e), x + 34, y + 16, e.offline() ? Ui.DIM : Ui.CYAN);
        }
        if (list.size() > rows) { // barra de scroll
            int bx = px + pw - 14 - 4, th = rows * (ROW + GAP) - GAP;
            Ui.rect(g, bx, listTop, 3, th, 0x20FFFFFF);
            int bh = Math.max(14, th * rows / list.size());
            int by = listTop + (th - bh) * scroll / Math.max(1, list.size() - rows);
            Ui.rect(g, bx, by, 3, bh, Ui.ACCENT_DIM);
        }

        // agregar cuenta premium
        {
            int x = px + 14, w = pw - 28, h = 20;
            boolean over = !modal() && viaLauncher && Ui.inside(mx, my, x, premiumY, w, h);
            Ui.rect(g, x, premiumY, w, h, over ? Ui.PANEL2 : Ui.PANEL);
            Ui.border(g, x, premiumY, w, h, over ? Ui.VIOLET : viaLauncher ? Ui.ACCENT_DIM : Ui.BORDER);
            Ui.rect(g, x, premiumY, 2, h, viaLauncher ? Ui.VIOLET : Ui.OFF);
            Ui.centered(g, "+  Agregar cuenta premium (Microsoft)", x + w / 2, premiumY + 6, !viaLauncher ? Ui.OFF : over ? Ui.ACCENT : Ui.TEXT);
            hits.add(new Hit(x, premiumY, w, h, this::addPremium));
        }

        // entrar con un nombre sin conexion
        Ui.text(g, "Entrar con otro nombre (sin conexion)", px + 14, premiumY + 20 + 8, Ui.DIM);
        Ui.rect(g, px + 14, fieldY, pw - 28 - 78, 22, 0xFF080A14);
        Ui.border(g, px + 14, fieldY, pw - 28 - 78, 22, nameField.isFocused() ? Ui.VIOLET : Ui.BORDER);
        if (nameField.getText().isEmpty() && !nameField.isFocused()) Ui.text(g, "Nick de Minecraft", px + 14 + 5, fieldY + (22 - 8) / 2, Ui.OFF);

        // aviso (izquierda de "Volver")
        int noticeW = pw - 28 - 90 - 8;
        if (!notice.isEmpty()) {
            Ui.text(g, Fx.fit(notice, noticeW), px + 14, bottomY + 6, noticeBad ? Ui.RED : Ui.GREEN);
        } else if (!viaLauncher) {
            Ui.text(g, Fx.fit("Abre el juego desde el launcher para usar cuentas premium.", noticeW), px + 14, bottomY + 6, Ui.DIM);
        }

        super.render(g, mx, my, pt); // campo de texto y botones (invisibles de vanilla)
        if (modal()) {
            // con una ventana abierta solo cuentan sus propios botones (los de la lista de abajo no deben recibir el clic)
            hits.clear();
            if (confirm != null) drawConfirm(g, mx, my); else drawPending(g, mx, my);
        } else {
            Ui.paintButtons(g, textRenderer, children());
        }
    }

    private void drawConfirm(DrawContext g, int mx, int my) {
        Ui.rect(g, px, py, pw, ph, 0xCC000000);
        int w = Math.min(pw - 24, 290), x = px + (pw - w) / 2;
        List<String> lines = Fx.wrap(confirm.text, w - 20, 4);
        int h = 14 + 10 + 6 + lines.size() * 10 + 10 + 18 + 10, y = py + (ph - h) / 2;
        int accent = confirm.danger ? Ui.RED : Ui.ACCENT_DIM;
        Ui.card(g, x, y, w, h, 0xFF0F1224, accent);
        Ui.gradient(g, x + 1, y, w - 2, 2, accent, confirm.danger ? Ui.PINK : Ui.ACCENT, false);
        Ui.centered(g, Fx.fit(confirm.title, w - 16), x + w / 2, y + 9, Ui.TEXT);
        for (int i = 0; i < lines.size(); i++) Ui.centered(g, lines.get(i), x + w / 2, y + 26 + i * 10, Ui.DIM);
        int bw = (w - 30) / 2, by = y + h - 28;
        boolean okHover = Ui.inside(mx, my, x + 10, by, bw, 18);
        int hc = confirm.danger ? Ui.RED : Ui.ACCENT;
        Ui.card(g, x + 10, by, bw, 18, okHover ? Ui.PANEL2 : Ui.PANEL, okHover ? hc : Ui.BORDER);
        Ui.centered(g, confirm.okLabel, x + 10 + bw / 2, by + 5, okHover ? hc : Ui.TEXT);
        Ui.button(g, "Cancelar", x + w - 10 - bw, by, bw, 18, Ui.inside(mx, my, x + w - 10 - bw, by, bw, 18));
        final Confirm c = confirm;
        hits.add(new Hit(x + 10, by, bw, 18, () -> { confirm = null; if (c.onOk != null) c.onOk.run(); }));
        hits.add(new Hit(x + w - 10 - bw, by, bw, 18, () -> { confirm = null; switchTarget = null; }));
    }

    private void drawPending(DrawContext g, int mx, int my) {
        Ui.rect(g, px, py, pw, ph, 0xCC000000);
        int w = Math.min(pw - 24, 290), x = px + (pw - w) / 2;
        String hint = pending.slow
                ? "Se abrio la ventana de Microsoft en el launcher. Si no la ves, busca el launcher en la barra de tareas."
                : "Esperando al launcher...";
        List<String> lines = Fx.wrap(hint, w - 20, 3);
        int h = 14 + 10 + 6 + lines.size() * 10 + 10 + 18 + 10, y = py + (ph - h) / 2;
        Ui.card(g, x, y, w, h, 0xFF0F1224, Ui.ACCENT_DIM);
        Ui.gradient(g, x + 1, y, w - 2, 2, Ui.ACCENT_DIM, Ui.ACCENT, false);
        int dots = (int) ((System.currentTimeMillis() / 400) % 4);
        String dot = "...".substring(0, dots);
        Ui.centered(g, Fx.fit(pending.label, w - 24) + dot, x + w / 2, y + 9, Ui.TEXT);
        for (int i = 0; i < lines.size(); i++) Ui.centered(g, lines.get(i), x + w / 2, y + 26 + i * 10, Ui.DIM);
        int bw = 90, bx = x + (w - bw) / 2, by = y + h - 28;
        Ui.button(g, "Cancelar", bx, by, bw, 18, Ui.inside(mx, my, bx, by, bw, 18));
        hits.add(new Hit(bx, by, bw, 18, () -> { Accounts.cancelRequest(); pending = null; say("Cancelado.", false); }));
    }

    // ------------------------------------------------------------ entrada

    @Override
    public boolean mouseClicked(double clickX, double clickY, int clickBtn) {
        if (clickBtn != 0) return false;
        double mx = clickX, my = clickY;
        if (modal()) { // con una ventana abierta solo cuentan sus propios botones
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (h.contains(mx, my)) { h.click.run(); break; }
            }
            return true;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.contains(mx, my)) { h.click.run(); return true; }
        }
        if (Ui.inside(mx, my, px + 14, fieldY, pw - 28 - 78, 22)) { setFocused(nameField); return true; }
        if (super.mouseClicked(clickX, clickY, clickBtn)) return true; // botones Entrar / Volver
        setFocused(null);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        if (vy != 0 && !modal() && list.size() > rows) {
            scroll = Math.max(0, Math.min(list.size() - rows, scroll + (vy > 0 ? -1 : 1)));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int code = keyCode;
        if (code == 256) { // Esc
            if (pending != null) { Accounts.cancelRequest(); pending = null; say("Cancelado.", false); }
            else if (confirm != null) { confirm = null; switchTarget = null; }
            else back();
            return true;
        }
        if (confirm != null) {
            if (code == 257 || code == 335) { // Enter
                Confirm cf = confirm;
                confirm = null;
                if (cf.onOk != null) cf.onOk.run();
            }
            return true;
        }
        if (pending != null) return true;
        if (nameField.isFocused()) {
            if (code == 257 || code == 335) { enterOffline(); return true; }
            return nameField.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (modal()) return true;
        return nameField.isFocused() && nameField.charTyped(chr, modifiers);
    }

    @Override
    public void close() { if (client != null) client.setScreen(parent); }
}
