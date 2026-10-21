package com.atenas.fabric.gui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.atenas.fabric.net.Friends;
import com.atenas.fabric.net.Social;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Amigos + chat (igual que SocialScreen de 1.8.9: mismas medidas, colores y textos). Izquierda: campo para agregar y lista con caras
 * agrupada (solicitudes, jugando ahora, en linea, desconectados, enviadas); derecha: cabecera del amigo, conversacion en burbujas y caja
 * de mensaje. Se abre con Shift+Tab, desde el menu principal y desde el menu de Atenas.
 */
public final class FriendsScreen extends Screen {

    private static String openChatKey; // para no avisar de mensajes del chat que ya estas viendo
    public static boolean isChattingWith(String key) { return key != null && key.equals(openChatKey); }

    private static final class Hit {
        final int x, y, w, h;
        final Runnable click;
        Hit(int x, int y, int w, int h, Runnable click) { this.x = x; this.y = y; this.w = w; this.h = h; this.click = click; }
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    /** Un mensaje ya partido en lineas (se recalcula solo si cambia). */
    private static final class Bub {
        List<String> lines;
        int w, h, sig, maxW;
        String time;
    }

    /** Titulo de grupo de la lista (Jugando ahora, En linea...), con su color y cuantos hay. */
    private static final class Title {
        final String text;
        final int count, color;
        Title(String text, int count, int color) { this.text = text; this.count = count; this.color = color; }
    }

    // Medidas y colores IGUALES a SocialScreen de 1.8.9.
    private static final int HEAD = 38, FOOT = 18, PAD = 10, CUT = 6, FIELD_H = 20, FRIEND_H = 32, REQ_H = 30, SEC_H = 20;
    private static final int PANEL = 0xFF0F0D22, PANEL2 = 0xFF1E1B38, LINE = 0xFF2B2848, ACCENT = 0xFF8B5CF6, ACCENT_LIGHT = 0xFFA78BFA, PINK = 0xFFEC4899;
    private static final int TEXT = 0xFFF1EEFC, MUTED = 0xFF9B97B8, FAINT = 0xFF6E6A8F, GREEN = 0xFF34D399, RED = 0xFFFB7185, AMBER = 0xFFFBBF24, CYAN = 0xFF22D3EE, OFFLINE = 0xFF5A5680;
    private static final int NOTICE_MS = 6000;

    private final Screen parent;
    private final List<Hit> hits = new ArrayList<>();
    private final List<Friends.Msg> msgs = new ArrayList<>();
    private final Map<Friends.Msg, Bub> bubCache = new IdentityHashMap<>();
    private TextFieldWidget addField, chatField;
    private String selected;
    private long lastId;
    private int scrollList, scrollChat, ticks, pollTick;
    private boolean loadingChat;
    private String confirmRemove;
    private String notice = "";
    private long noticeAt;
    private int x0, y0, pw, ph;
    private final float[] glow = new float[3]; // brillo animado de los botones: 0 = agregar, 1 = enviar, 2 = quitar

    public FriendsScreen(Screen parent) { super(Text.literal("Amigos")); this.parent = parent; }

    @Override
    public boolean shouldPause() { return false; }

    // ------------------------------------------------------------ medidas (init y el dibujo usan las MISMAS)
    private int lw() { return pw < 440 ? 150 : 178; }
    private int bodyTop() { return y0 + HEAD + 8; }
    private int bodyBottom() { return y0 + ph - FOOT - 6; }
    private int listX() { return x0 + PAD; }
    private int listTop() { return bodyTop() + FIELD_H + 6; }
    private int cx() { return x0 + PAD + lw() + 8; }
    private int cw() { return pw - lw() - PAD * 2 - 8; }
    private int inputY() { return bodyBottom() - FIELD_H - 6; }
    private int inputX() { return cx() + 6; }
    private int inputW() { return cw() - 12 - 56 - 4; }

    @Override
    protected void init() {
        pw = Math.min(520, width - 16);
        ph = Math.min(320, height - 16);
        x0 = (width - pw) / 2;
        y0 = (height - ph) / 2;
        String keepAdd = addField == null ? "" : addField.getText();
        String keepChat = chatField == null ? "" : chatField.getText();
        addField = field(listX() + 22, bodyTop() + (FIELD_H - 8) / 2, lw() - 54, 16);
        addField.setText(keepAdd);
        chatField = field(inputX() + 8, inputY() + (FIELD_H - 8) / 2, inputW() - 16, 300);
        chatField.setText(keepChat);
        if (selected != null) setFocused(chatField);
        else setFocused(null);
        if (Friends.sinceLastSync() > 3000) Friends.sync(client != null && client.world != null, "");
    }

    /** Campo de texto sin fondo propio (la caja la dibuja inputBox): el texto se pinta justo en (fx, fy). */
    private TextFieldWidget field(int fx, int fy, int fw, int max) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, fx, fy, fw, 8, Text.empty());
        f.setMaxLength(max);
        f.setDrawsBackground(false);
        addDrawableChild(f);
        return f;
    }

    @Override
    public void removed() { openChatKey = null; }

    @Override
    public void tick() {
        ticks++;
        if (selected != null && ++pollTick >= 50) { pollTick = 0; pollChat(); }
        if (selected != null && Friends.find(selected) == null && Friends.usable()) closeChat();
        if (Friends.sinceLastSync() > 6000) Friends.sync(client != null && client.world != null, "");
    }

    // ------------------------------------------------------------ acciones

    private void say(final String text) {
        MinecraftClient.getInstance().execute(() -> { notice = text == null ? "" : text; noticeAt = System.currentTimeMillis(); });
    }

    private void openChat(String key) {
        if (key.equals(selected)) return;
        selected = key;
        openChatKey = key;
        msgs.clear();
        bubCache.clear();
        lastId = 0;
        scrollChat = 0;
        confirmRemove = null;
        setFocused(chatField);
        loadChat(0);
    }

    private void closeChat() { selected = null; openChatKey = null; msgs.clear(); bubCache.clear(); lastId = 0; }

    private void loadChat(final long after) {
        final String key = selected;
        if (key == null || loadingChat) return;
        loadingChat = true;
        Friends.history(key, after, (list, err) -> MinecraftClient.getInstance().execute(() -> {
            loadingChat = false;
            if (!key.equals(selected)) return;
            if (err != null) { notice = err; noticeAt = System.currentTimeMillis(); return; }
            // los mensajes que se estan enviando (pending) se quedan hasta que llegue la respuesta del envio
            boolean added = false;
            for (Friends.Msg m : list) {
                boolean dup = false;
                for (Friends.Msg o : msgs) if (o.id == m.id) { dup = true; break; }
                if (!dup) { msgs.add(m); added = true; }
                if (m.id > lastId) lastId = m.id;
            }
            if (added) { sortMsgs(); scrollChat = 0; }
        }));
    }

    private void pollChat() { loadChat(lastId); }

    /** Por id; los que todavia no tienen id (recien enviados) van al final. */
    private void sortMsgs() {
        Collections.sort(msgs, new Comparator<Friends.Msg>() {
            @Override public int compare(Friends.Msg a, Friends.Msg b) {
                long x = a.id == 0 ? Long.MAX_VALUE : a.id, y = b.id == 0 ? Long.MAX_VALUE : b.id;
                if (x != y) return x < y ? -1 : 1;
                return Long.compare(a.ts, b.ts);
            }
        });
    }

    private void sendChat() {
        final String text = chatField.getText().trim();
        final String key = selected;
        if (text.isEmpty() || key == null) return;
        chatField.setText("");
        final Friends.Msg temp = new Friends.Msg();
        temp.mine = true;
        temp.body = text;
        temp.ts = System.currentTimeMillis();
        temp.pending = true;
        msgs.add(temp);
        scrollChat = 0;
        Friends.send(key, text, (m, err) -> MinecraftClient.getInstance().execute(() -> {
            if (err != null || m == null) { temp.pending = false; temp.failed = true; say(err); return; }
            temp.pending = false;
            boolean dupe = false;
            for (Friends.Msg o : msgs) if (o != temp && o.id == m.id) { dupe = true; break; }
            if (dupe) msgs.remove(temp); // el sondeo ya trajo este mensaje desde el servidor
            else { temp.id = m.id; temp.ts = m.ts; }
            // lastId NO se sube aqui: si el amigo escribio justo antes, su mensaje tiene un id menor y no se pediria nunca.
            sortMsgs();
            pollTick = 45;
        }));
    }

    private void sendRequest() {
        String n = addField.getText().trim();
        if (n.isEmpty()) return;
        if (!Social.validName(n)) { say("Nick no valido (3-16 letras, numeros o _)."); return; }
        addField.setText("");
        Friends.add(n, (v, err) -> {
            if (err != null) say(err);
            else say(v.startsWith("accepted") ? "Ahora eres amigo de " + v.substring(v.indexOf('|') + 1) : "Solicitud enviada a " + v.substring(v.indexOf('|') + 1));
        });
    }

    private java.util.function.BiConsumer<String, String> plain(final String ok) {
        return (v, err) -> say(err != null ? err : ok);
    }

    // ------------------------------------------------------------ piezas de dibujo

    private static String stateText(Friends.Friend f) {
        if (f.playing()) return f.server.isEmpty() ? "Jugando" : "Jugando en " + f.server;
        return f.online() ? "En el launcher" : "Desconectado";
    }

    private static int stateColor(Friends.Friend f) { return f.playing() ? GREEN : f.online() ? CYAN : OFFLINE; }

    /** Cara del jugador (o su inicial mientras llega) con marco del color de su estado y un punto de estado opcional. */
    private void avatar(DrawContext g, String name, int x, int y, int s, int ring, boolean dim, boolean dot) {
        Ui.rect(g, x - 1, y - 1, s + 2, s + 2, ring);
        if (!Faces.draw(g, name, x, y, s, 1f, false)) {
            Ui.rect(g, x, y, s, s, 0xFF2A2550);
            String ini = name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
            Ui.centered(g, ini, x + s / 2, y + (s - 8) / 2, ACCENT_LIGHT);
        }
        if (dim) Ui.rect(g, x, y, s, s, 0x88090812);
        if (dot) {
            Ui.rect(g, x + s - 5, y + s - 5, 7, 7, PANEL);
            Ui.rect(g, x + s - 4, y + s - 4, 5, 5, ring);
        }
    }

    private void smallBtn(DrawContext g, String label, int x, int y, int w, int h, int mx, int my, int color, Runnable r) {
        boolean hov = Ui.inside(mx, my, x, y, w, h);
        Ui.chamfer(g, x, y, w, h, 2, hov ? PANEL2 : PANEL);
        Ui.chamferBorder(g, x, y, w, h, 2, hov ? color : LINE);
        Ui.centered(g, label, x + w / 2, y + (h - 8) / 2, hov ? color : TEXT);
        hits.add(new Hit(x, y, w, h, r));
    }

    /** Boton futurista: "primary" lleva degradado violeta -> rosa; el otro es cristal con barra de neon al pasar el mouse. */
    private void btn(DrawContext g, int slot, String label, int x, int y, int w, int h, int mx, int my, boolean primary, Runnable r) {
        boolean hot = Ui.inside(mx, my, x, y, w, h);
        glow[slot] += ((hot ? 1f : 0f) - glow[slot]) * 0.22f;
        float gl = glow[slot];
        int cut = h >= 18 ? 4 : 3;
        if (primary) {
            Ui.chamfer(g, x, y, w, h, cut, 0xFF2A1A5E);
            Ui.gradient(g, x + cut, y + 1, w - 2 * cut, h - 2, hot ? ACCENT_LIGHT : ACCENT, hot ? PINK : 0xFF6D4FC7, true);
            Ui.chamferBorder(g, x, y, w, h, cut, hot ? 0xFFFFFFFF : ACCENT_LIGHT);
        } else {
            Ui.chamfer(g, x, y, w, h, cut, 0xE60D0B1E);
            int a = (int) (0x46 * gl);
            Ui.gradient(g, x + cut, y + h / 2, w - 2 * cut, h - 1 - h / 2, 0x008B5CF6, (a << 24) | 0x8B5CF6, true);
            Ui.chamferBorder(g, x, y, w, h, cut, hot ? ACCENT_LIGHT : 0xFF3A3466);
            int bar = (int) (gl * (w - 2 * cut));
            if (bar > 0) Ui.gradient(g, x + w / 2 - bar / 2, y + h - 2, bar, 1, ACCENT, CYAN, false);
            Ui.rect(g, x + w - cut - 5, y, 5, 1, hot ? CYAN : 0xFF6D4FC7);
            Ui.rect(g, x + cut, y + h - 1, 5, 1, hot ? PINK : 0xFF6D4FC7);
        }
        Fx.headingFit(g, label, x + cut, w - 2 * cut, y + (h - 8) / 2, hot ? 0xFFFFFFFF : TEXT, (int) (gl * 1.5f));
        hits.add(new Hit(x, y, w, h, r));
    }

    private void closeBtn(DrawContext g, int x, int y, int s, int mx, int my) {
        boolean hov = Ui.inside(mx, my, x, y, s, s);
        Ui.chamfer(g, x, y, s, s, 3, hov ? 0xFF3A1F3F : PANEL);
        Ui.chamferBorder(g, x, y, s, s, 3, hov ? RED : LINE);
        int c = hov ? RED : TEXT;
        for (int i = 0; i < 7; i++) {
            Ui.rect(g, x + 5 + i, y + 5 + i, 1, 1, c);
            Ui.rect(g, x + 11 - i, y + 5 + i, 1, 1, c);
        }
        hits.add(new Hit(x, y, s, s, this::close));
    }

    /** Caja de texto: cristal oscuro con esquinas cortadas; con el foco, borde claro y una linea de degradado abajo. */
    private void inputBox(DrawContext g, int x, int y, int w, int h, boolean focus) {
        Ui.chamfer(g, x, y, w, h, 3, 0xFF0A0918);
        Ui.chamferBorder(g, x, y, w, h, 3, focus ? ACCENT_LIGHT : LINE);
        if (focus) Ui.gradient(g, x + 4, y + h - 1, w - 8, 1, ACCENT, PINK, false);
    }

    private static void userIcon(DrawContext g, int x, int y, int c) {
        Ui.rect(g, x + 3, y, 4, 4, c);
        Ui.rect(g, x + 2, y + 5, 6, 1, c);
        Ui.rect(g, x + 1, y + 6, 8, 3, c);
    }

    // ------------------------------------------------------------ dibujo

    @Override
    public void renderBackground(DrawContext g, int mx, int my, float d) { }

    @Override
    public void render(DrawContext g, int mx, int my, float delta) {
        hits.clear();
        long t = System.currentTimeMillis();

        // fondo: con mundo detras, un velo oscuro; sin mundo, el fondo animado de Atenas
        boolean inWorld = client != null && client.world != null;
        if (!inWorld) Ui.animatedBackdrop(g, width, height);
        else Ui.gradient(g, 0, 0, width, height, 0xC0070914, 0xE00E0B22, true);

        // carcasa: resplandor, panel y cabecera
        for (int k = 3; k >= 1; k--) Ui.chamfer(g, x0 - k * 2, y0 - k * 2, pw + k * 4, ph + k * 4, CUT + k * 2, ((0x16 / k) << 24) | 0x8B5CF6);
        Ui.chamfer(g, x0, y0, pw, ph, CUT, 0xF4090818);
        Ui.chamferBorder(g, x0, y0, pw, ph, CUT, 0xFF3A3466);
        Ui.gradient(g, x0 + CUT, y0 + 1, pw - 2 * CUT, CUT - 1, 0xFF1B1740, 0xFF181436, true);
        Ui.gradient(g, x0 + 1, y0 + CUT, pw - 2, HEAD - CUT, 0xFF181436, 0xFF0C0A1A, true);
        Ui.rect(g, x0 + 1, y0 + HEAD, pw - 2, 1, LINE);
        Ui.gradient(g, x0 + CUT, y0, pw - 2 * CUT, 2, ACCENT, PINK, false);
        Ui.gradient(g, x0 + 14, y0 + HEAD, 70, 1, ACCENT, 0x008B5CF6, false);
        float pulse = (float) (0.5 + 0.5 * Math.sin(t * 0.003));
        Ui.rect(g, x0 + 10, y0 + 7, 26, 26, (((int) (12 + 16 * pulse)) << 24) | 0x8B5CF6);

        Ui.logo(g, x0 + 12, y0 + 9, 22);
        g.getMatrices().pushMatrix();
        g.getMatrices().translate((float) (x0 + 42), (float) (y0 + 8));
        g.getMatrices().scale(1.25f, 1.25f);
        Fx.heading(g, "Amigos", 0, 0, TEXT);
        g.getMatrices().popMatrix();

        String st = Friends.status();
        Friends.Snapshot snap = Friends.snapshot();
        int sx = x0 + 42, sy = y0 + 23;
        if (!"ok".equals(st)) {
            boolean err = "error".equals(st);
            String sub = "noaccount".equals(st) ? "Inicia sesión" : err ? "Sin conexión" : "Conectando...";
            Ui.rect(g, sx, sy + 2, 5, 5, err ? RED : AMBER);
            Ui.text(g, sub, sx + 9, sy, err ? RED : MUTED);
        } else {
            int on = onlineNow();
            Ui.rect(g, sx, sy + 2, 5, 5, on > 0 ? GREEN : OFFLINE);
            String line = snap.friends.isEmpty() ? "Sin amigos todavía" : on + " de " + snap.friends.size() + " conectados";
            Ui.text(g, line, sx + 9, sy, MUTED);
        }
        closeBtn(g, x0 + pw - 26, y0 + 11, 16, mx, my);
        if (!notice.isEmpty() && System.currentTimeMillis() - noticeAt < NOTICE_MS) { // aviso dentro de la cabecera (hasta 2 lineas)
            int fx = x0 + 170, fw = pw - 170 - 40;
            if (fw > 60) {
                List<String> ls = Fx.wrap(notice, fw - 12, 2);
                int n = ls.size();
                int fy = y0 + (HEAD - n * 10) / 2 + 1;
                Ui.chamfer(g, fx - 6, y0 + 6, fw, HEAD - 12, 2, 0xCC2A1A5E);
                Ui.rect(g, fx - 6, y0 + 8, 2, HEAD - 16, AMBER);
                for (int i = 0; i < n; i++) Ui.text(g, ls.get(i), fx + 2, fy + i * 10, AMBER);
            }
        }

        drawList(g, mx, my);
        drawChat(g, mx, my);
        drawFooter(g);
        super.render(g, mx, my, delta); // los dos campos de texto
    }

    /** Pie: atajos a la izquierda y, a la derecha, tu nick y si la cuenta esta verificada con Mojang (los cosmeticos privados lo exigen). */
    private void drawFooter(DrawContext g) {
        int fy = y0 + ph - FOOT;
        Ui.rect(g, x0 + 1, fy, pw - 2, 1, LINE);
        if (pw >= 400) Ui.text(g, "Esc cerrar  ·  Enter enviar  ·  Rueda para desplazar", x0 + PAD, fy + 6, FAINT);
        boolean ok = Friends.verified();
        String tag = Friends.me() + "  ·  " + (ok ? "cuenta verificada" : "sin verificar");
        int tx = x0 + pw - PAD - Fx.width(tag);
        Ui.rect(g, tx - 9, fy + 8, 5, 5, ok ? GREEN : AMBER);
        Ui.text(g, tag, tx, fy + 6, ok ? MUTED : AMBER);
    }

    private int onlineNow() {
        int on = 0;
        for (Friends.Friend f : Friends.snapshot().friends) if (f.online()) on++;
        return on;
    }

    private void drawList(DrawContext g, int mx, int my) {
        int lx = listX(), top = bodyTop(), w = lw();
        // campo para agregar
        boolean af = addField.isFocused();
        inputBox(g, lx, top, w - 26, FIELD_H, af);
        userIcon(g, lx + 7, top + 6, af ? ACCENT_LIGHT : FAINT);
        if (addField.getText().isEmpty() && !af) Ui.text(g, "Agregar amigo...", lx + 22, top + (FIELD_H - 8) / 2, FAINT);
        btn(g, 0, "+", lx + w - 22, top, 22, FIELD_H, mx, my, true, this::sendRequest);

        int ly = listTop(), lb = bodyBottom(), lh = lb - ly;
        Ui.chamfer(g, lx, ly, w, lh, 3, PANEL);
        Ui.chamferBorder(g, lx, ly, w, lh, 3, LINE);
        Friends.Snapshot s = Friends.snapshot();
        if (!Friends.usable()) {
            boolean err = "error".equals(Friends.status());
            String msg = err ? "Sin conexión. " + Friends.error()
                    : "noaccount".equals(Friends.status()) ? "Inicia sesión para usar amigos" : "Conectando...";
            drawWrapped(g, msg, lx + 10, ly + 12, w - 20, err ? RED : MUTED);
            return;
        }
        List<Object> rows = new ArrayList<>();
        if (!s.incoming.isEmpty()) { rows.add(new Title("Solicitudes", s.incoming.size(), AMBER)); rows.addAll(s.incoming); }
        addGroup(rows, s, "playing", "Jugando ahora", GREEN);
        addGroup(rows, s, "online", "En línea", CYAN);
        addGroup(rows, s, "offline", "Desconectados", OFFLINE);
        if (!s.outgoing.isEmpty()) { rows.add(new Title("Enviadas", s.outgoing.size(), FAINT)); for (Friends.Req r : s.outgoing) rows.add(new Object[] {r}); }
        if (rows.isEmpty()) {
            Ui.logo(g, lx + w / 2 - 14, ly + lh / 2 - 48, 28);
            Ui.centered(g, "Aún no tienes amigos", lx + w / 2, ly + lh / 2 - 12, TEXT);
            drawWrapped(g, "Escribe su nick arriba y envía una solicitud.", lx + 12, ly + lh / 2 + 2, w - 24, MUTED);
            return;
        }
        int total = 0;
        for (Object o : rows) total += rowHeight(o);
        scrollList = Math.max(0, Math.min(scrollList, Math.max(0, total - lh + 4)));
        g.enableScissor(lx + 1, ly + 1, lx + w - 1, ly + lh - 1);
        int y = ly + 2 - scrollList;
        int right = lx + w - 8;
        for (Object o : rows) {
            int rh = rowHeight(o);
            if (y + rh < ly || y > lb) { y += rh; continue; }
            if (o instanceof Title ti) {
                String cnt = String.valueOf(ti.count);
                int cw = Fx.width(cnt);
                Ui.rect(g, lx + 9, y + 8, 4, 4, ti.color);
                Fx.heading(g, ti.text, lx + 18, y + 6, ti.color);
                int hw = Fx.headingWidth(ti.text);
                int lineX = lx + 18 + hw + 6, lineW = right - cw - 6 - lineX;
                if (lineW > 0) Ui.rect(g, lineX, y + 10, lineW, 1, LINE);
                Ui.text(g, cnt, right - cw, y + 6, MUTED);
            } else if (o instanceof Friends.Friend f) {
                boolean active = f.key.equals(selected);
                boolean hover = Ui.inside(mx, my, lx, y, w, rh) && my >= ly && my < lb;
                if (active) {
                    Ui.gradient(g, lx + 1, y, w - 2, rh, 0xFF2A2458, 0xFF1D1940, true);
                    Ui.gradient(g, lx + 1, y, 2, rh, ACCENT, PINK, true);
                } else if (hover) {
                    Ui.rect(g, lx + 1, y, w - 2, rh, 0xFF181530);
                }
                int col = stateColor(f);
                avatar(g, f.name, lx + 9, y + 4, 24, col, !f.online(), true);
                boolean preview = f.unread > 0 && !f.previewBody.isEmpty();
                int badge = f.unread > 0 ? 24 : 0;
                int tw = w - 46 - badge;
                Ui.text(g, Fx.fit(f.name, tw), lx + 40, y + 6, f.online() ? TEXT : MUTED);
                Ui.text(g, Fx.fit(preview ? f.previewBody : stateText(f), tw), lx + 40, y + 18, preview ? AMBER : col);
                if (f.unread > 0) {
                    String n = f.unread > 9 ? "9+" : String.valueOf(f.unread);
                    Ui.chamfer(g, right - 16, y + 9, 17, 13, 3, ACCENT);
                    Ui.gradient(g, right - 14, y + 10, 13, 11, ACCENT_LIGHT, PINK, true);
                    Ui.text(g, n, right - 7 - Fx.width(n) / 2, y + 12, 0xFFFFFFFF);
                }
                final String key = f.key;
                addClipped(lx, y, w, rh, ly + 1, lb - 1, () -> openChat(key));
            } else if (o instanceof Friends.Req r) {
                avatar(g, r.name, lx + 9, y + 3, 22, AMBER, false, false);
                Ui.text(g, Fx.fit(r.name, w - 98), lx + 38, y + 5, TEXT);
                Ui.text(g, Fx.fit("quiere ser tu amigo", w - 98), lx + 38, y + 16, MUTED);
                smallBtn(g, "+", right - 38, y + 7, 17, 16, mx, my, GREEN, () -> Friends.accept(reqKey(r), plain("Ahora eres amigo de " + r.name)));
                smallBtn(g, "x", right - 18, y + 7, 17, 16, mx, my, RED, () -> Friends.remove(reqKey(r), plain("Solicitud rechazada")));
            } else {
                final Friends.Req r = (Friends.Req) ((Object[]) o)[0];
                avatar(g, r.name, lx + 9, y + 3, 22, OFFLINE, true, false);
                Ui.text(g, Fx.fit(r.name, w - 70), lx + 38, y + 5, MUTED);
                Ui.text(g, Fx.fit("esperando respuesta...", w - 70), lx + 38, y + 16, FAINT);
                smallBtn(g, "x", right - 18, y + 7, 17, 16, mx, my, RED, () -> Friends.remove(reqKey(r), plain("Solicitud cancelada")));
            }
            y += rh;
        }
        g.disableScissor();
        if (total > lh) { // barra de scroll fina
            int sbH = Math.max(14, (int) ((long) lh * lh / total));
            int sbY = ly + 2 + (int) ((long) (lh - 4 - sbH) * scrollList / Math.max(1, total - lh + 4));
            Ui.rect(g, lx + w - 3, sbY, 2, sbH, 0xFF6D4FC7);
        }
    }

    private static String reqKey(Friends.Req r) { return r.key.isEmpty() ? r.name : r.key; }

    private static int rowHeight(Object o) {
        if (o instanceof Title) return SEC_H;
        if (o instanceof Friends.Friend) return FRIEND_H;
        return REQ_H;
    }

    private void addClipped(int x, int y, int w, int h, int top, int bottom, Runnable r) {
        int hy = Math.max(y, top), hh = Math.min(y + h, bottom) - hy;
        if (hh > 0) hits.add(new Hit(x, hy, w, hh, r));
    }

    private void addGroup(List<Object> rows, Friends.Snapshot s, String state, String title, int color) {
        int n = 0;
        for (Friends.Friend f : s.friends) if (f.state.equals(state)) n++;
        if (n == 0) return;
        rows.add(new Title(title, n, color));
        for (Friends.Friend f : s.friends) if (f.state.equals(state)) rows.add(f);
    }

    private void drawWrapped(DrawContext g, String text, int x, int y, int w, int color) {
        for (String l : Fx.wrap(text, w, 6)) { Ui.text(g, l, x, y, color); y += 10; }
    }

    // ------------------------------------------------------------ chat

    /** Parte el texto en lineas de maxW pixeles; una palabra mas ancha que la linea se corta por letras. */
    private List<String> wrapText(String s, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            while (Fx.width(word) > maxW && word.length() > 1) {
                int n = word.length();
                while (n > 1 && Fx.width(word.substring(0, n)) > maxW) n--;
                if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
                out.add(word.substring(0, n));
                word = word.substring(n);
            }
            String test = cur.length() == 0 ? word : cur + " " + word;
            if (cur.length() > 0 && Fx.width(test) > maxW) { out.add(cur.toString()); cur.setLength(0); cur.append(word); }
            else { cur.setLength(0); cur.append(test); }
        }
        if (cur.length() > 0 || out.isEmpty()) out.add(cur.toString());
        return out;
    }

    private Bub layout(Friends.Msg m, int maxW) {
        int sig = (m.pending ? 1 : 0) | (m.failed ? 2 : 0) | (int) (m.ts / 60000L) << 2;
        Bub b = bubCache.get(m);
        if (b != null && b.maxW == maxW && b.sig == sig) return b;
        b = new Bub();
        b.maxW = maxW;
        b.sig = sig;
        b.lines = wrapText(m.body, maxW);
        int w = 0;
        for (String l : b.lines) w = Math.max(w, Fx.width(l));
        b.w = w + 14;
        b.h = b.lines.size() * 10 + 8;
        b.time = m.failed ? "no enviado" : m.pending ? "enviando..." : new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date(m.ts));
        bubCache.put(m, b);
        return b;
    }

    private void drawChat(DrawContext g, int mx, int my) {
        int cx = cx(), top = bodyTop(), cw = cw(), bottom = bodyBottom(), ch = bottom - top;
        Ui.chamfer(g, cx, top, cw, ch, 3, PANEL);
        Ui.chamferBorder(g, cx, top, cw, ch, 3, LINE);
        final Friends.Friend f = selected == null ? null : Friends.find(selected);
        if (f == null) {
            Ui.logo(g, cx + cw / 2 - 24, top + ch / 2 - 58, 48);
            String title = "Tus conversaciones";
            Fx.heading(g, title, cx + cw / 2 - Fx.headingWidth(title) / 2, top + ch / 2 - 2, TEXT);
            Ui.centered(g, "Elige a un amigo de la lista para chatear", cx + cw / 2, top + ch / 2 + 13, MUTED);
            return;
        }
        // cabecera del chat
        Ui.gradient(g, cx + 1, top + 1, cw - 2, 39, 0xFF1E1B38, 0xFF171431, true);
        Ui.rect(g, cx + 1, top + 40, cw - 2, 1, LINE);
        int ring = stateColor(f);
        avatar(g, f.name, cx + 9, top + 7, 26, ring, !f.online(), true);
        Ui.text(g, f.name, cx + 44, top + 9, TEXT);
        Ui.text(g, Fx.fit(stateText(f), cw - 44 - 76), cx + 44, top + 22, ring);
        boolean armed = f.key.equals(confirmRemove);
        btn(g, 2, armed ? "¿Seguro?" : "Quitar", cx + cw - 6 - 58, top + 11, 58, 18, mx, my, false, () -> {
            if (f.key.equals(confirmRemove)) {
                confirmRemove = null;
                Friends.remove(f.key, plain(f.name + " ya no está en tu lista"));
                closeChat();
            } else {
                confirmRemove = f.key;
                say("Pulsa «Quitar» otra vez para confirmar (borra el chat)");
            }
        });

        // mensajes: se arman de abajo hacia arriba; la rueda los desplaza
        int logTop = top + 45, logBottom = inputY() - 6, logH = logBottom - logTop;
        int maxW = Math.max(60, (int) ((cw - 28) * 0.78f));
        int n = msgs.size();
        Bub[] bs = new Bub[n];
        int[] after = new int[n], ys = new int[n];
        boolean[] showTime = new boolean[n];
        int total = 0;
        for (int i = 0; i < n; i++) {
            Friends.Msg m = msgs.get(i);
            bs[i] = layout(m, maxW);
            Friends.Msg nx = i + 1 < n ? msgs.get(i + 1) : null;
            showTime[i] = nx == null || nx.mine != m.mine || nx.ts - m.ts > 120000L || m.failed || m.pending;
            after[i] = showTime[i] ? 13 : 3;
            total += bs[i].h + after[i];
        }
        scrollChat = Math.max(0, Math.min(scrollChat, Math.max(0, total - logH + 4)));
        g.enableScissor(cx + 1, logTop, cx + cw - 1, logTop + logH);
        int y = logBottom - 2 + scrollChat - total;
        for (int i = 0; i < n; i++) { ys[i] = y; y += bs[i].h + after[i]; }
        for (int i = 0; i < n; i++) { // burbujas
            Bub b = bs[i];
            if (ys[i] + b.h + after[i] <= logTop - 2 || ys[i] >= logBottom) continue;
            Friends.Msg m = msgs.get(i);
            int bx = m.mine ? cx + cw - 10 - b.w : cx + 10;
            if (m.mine) {
                Ui.chamfer(g, bx, ys[i], b.w, b.h, 3, m.failed ? 0xFF5A1F2C : 0xFF3B2A86);
                if (!m.failed) Ui.gradient(g, bx + 3, ys[i] + 1, b.w - 6, b.h - 2, m.pending ? 0xFF5B4AA8 : 0xFF6D4FC7, m.pending ? 0xFF7A3FA6 : 0xFF9D3FD0, true);
            } else {
                Ui.chamfer(g, bx, ys[i], b.w, b.h, 3, PANEL2);
                Ui.chamferBorder(g, bx, ys[i], b.w, b.h, 3, 0xFF2F2B52);
            }
        }
        for (int i = 0; i < n; i++) { // texto y hora
            Bub b = bs[i];
            if (ys[i] + b.h + after[i] <= logTop - 2 || ys[i] >= logBottom) continue;
            Friends.Msg m = msgs.get(i);
            int bx = m.mine ? cx + cw - 10 - b.w : cx + 10;
            for (int j = 0; j < b.lines.size(); j++) Ui.text(g, b.lines.get(j), bx + 7, ys[i] + 4 + j * 10, m.failed ? 0xFFFFB3BD : TEXT);
            if (showTime[i]) {
                int tx = m.mine ? bx + b.w - Fx.width(b.time) : bx;
                Ui.text(g, b.time, tx, ys[i] + b.h + 2, m.failed ? RED : FAINT);
            }
        }
        g.disableScissor();
        if (n == 0) Ui.centered(g, loadingChat ? "Cargando..." : "Aún no hay mensajes. ¡Saluda!", cx + cw / 2, logTop + logH / 2 - 4, MUTED);
        else if (scrollChat > 0) Ui.centered(g, "Desplazado: baja para ver lo más nuevo", cx + cw / 2, logBottom - 10, 0xFF6D4FC7);

        // caja de mensaje
        boolean cf = chatField.isFocused();
        inputBox(g, inputX(), inputY(), inputW(), FIELD_H, cf);
        if (chatField.getText().isEmpty() && !cf) Ui.text(g, "Escribe un mensaje...", inputX() + 8, inputY() + (FIELD_H - 8) / 2, FAINT);
        btn(g, 1, "Enviar", cx + cw - 6 - 56, inputY(), 56, FIELD_H, mx, my, true, this::sendChat);
    }

    // ------------------------------------------------------------ entrada

    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        if (vy == 0) return false;
        if (mx < cx() - 4) scrollList += vy > 0 ? -18 : 18;
        else scrollChat += vy > 0 ? 20 : -20;
        return true;
    }

    @Override
    public boolean mouseClicked(double clickX, double clickY, int clickBtn) {
        double mx = clickX, my = clickY;
        if (clickBtn == 0) {
            // clic en cualquier parte de la caja = enfocarla
            boolean inAdd = Ui.inside(mx, my, listX(), bodyTop(), lw() - 26, FIELD_H);
            boolean inChat = selected != null && Ui.inside(mx, my, inputX(), inputY(), inputW(), FIELD_H);
            if (inAdd) { setFocused(addField); return true; }
            if (inChat) { setFocused(chatField); return true; }
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (h.contains(mx, my)) { h.click.run(); return true; }
            }
            setFocused(null); // clic fuera de las cajas: se quita el foco
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int code = keyCode;
        boolean shift = Keys.shift();
        if (code == 256 || (code == 258 && shift)) { close(); return true; } // Esc, Shift+Tab
        if (code == 258) { // Tab: alterna entre agregar y chat
            boolean toAdd = chatField.isFocused() || selected == null;
            setFocused(toAdd ? addField : (selected != null ? chatField : addField));
            return true;
        }
        if (code == 257 || code == 335) { // Enter
            if (addField.isFocused()) { sendRequest(); return true; }
            if (selected != null && chatField.isFocused()) { sendChat(); return true; }
        }
        if (addField.isFocused()) return addField.keyPressed(keyCode, scanCode, modifiers);
        if (selected != null && chatField.isFocused()) return chatField.keyPressed(keyCode, scanCode, modifiers);
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (addField.isFocused()) return addField.charTyped(chr, modifiers);
        if (selected != null && chatField.isFocused()) return chatField.charTyped(chr, modifiers);
        return false;
    }

    @Override
    public void close() { if (client != null) client.setScreen(parent); }
}
