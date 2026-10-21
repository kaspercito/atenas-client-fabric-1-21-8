package com.atenas.fabric.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.atenas.fabric.net.Friends;

import net.minecraft.client.gui.DrawContext;

/**
 * Avisos propios de Atenas (solicitudes, mensajes, amigos en linea), los mismos de 1.8.9. Cada aviso trae la cara del jugador, entra
 * deslizandose desde la derecha con un pequeno rebote, se acomoda suave cuando otro desaparece, muestra una barrita que se vacia y sale
 * deslizandose. Arriba a la derecha, tanto en el juego como sobre cualquier pantalla.
 */
public final class Toasts {
    private Toasts() {}

    private static final int LIFE_MS = 5500, IN_MS = 380, OUT_MS = 320, W = 176, H = 34, GAP = 4, TOP = 6;

    private static final class Toast {
        final String title, text, face;
        final int accent;
        final long born = System.currentTimeMillis();
        float y = -1f; // -1 = todavia sin posicion: aparece directamente en su sitio
        Toast(String title, String text, String face, int accent) { this.title = title; this.text = text; this.face = face; this.accent = accent; }
    }

    private static final List<Toast> LIST = new ArrayList<>();
    private static long lastFrame;

    /** Desde cualquier hilo. "face" es el nick del jugador (o null si el aviso no es de nadie). */
    public static synchronized void push(String title, String text, String face, int accent) {
        if (!Friends.toasts()) return;
        if (LIST.size() >= 4) LIST.remove(0);
        LIST.add(new Toast(title, text, face, accent));
    }

    /** Compara el estado anterior con el nuevo y avisa de lo que cambio. La primera vez no se avisa de lo que ya estaba. */
    public static void onSnapshot(Friends.Snapshot prev, Friends.Snapshot next) {
        if (prev == null) return;
        Map<String, Friends.Friend> before = new HashMap<>();
        for (Friends.Friend f : prev.friends) before.put(f.key, f);
        Map<String, Boolean> prevIn = new HashMap<>(), prevOut = new HashMap<>();
        for (Friends.Req r : prev.incoming) prevIn.put(r.key, true);
        for (Friends.Req r : prev.outgoing) prevOut.put(r.key, true);
        for (Friends.Req r : next.incoming) {
            if (!prevIn.containsKey(r.key)) push("Solicitud de amistad", r.name + " quiere ser tu amigo", r.name, Ui.ACCENT);
        }
        for (Friends.Friend f : next.friends) {
            Friends.Friend p = before.get(f.key);
            if (p == null) {
                if (prevOut.containsKey(f.key)) push("Nuevo amigo", f.name + " acepto tu solicitud", f.name, 0xFF55FF99);
                continue;
            }
            if (f.unread > 0 && f.previewId > p.previewId) {
                if (!FriendsScreen.isChattingWith(f.key)) push(f.name, f.previewBody, f.name, 0xFF55CCFF);
            } else if (p.state.equals("offline") && !f.state.equals("offline")) {
                push("Amigos", f.name + (f.playing() ? " esta jugando" : " esta en linea"), f.name, 0xFF55FF99);
            }
        }
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1f, u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    /** Dibuja los avisos (hilo principal). Una vez por cuadro, con el HUD o sobre una pantalla. */
    public static synchronized void draw(DrawContext g) {
        long now = System.currentTimeMillis();
        for (int i = LIST.size() - 1; i >= 0; i--) if (now - LIST.get(i).born > LIFE_MS) LIST.remove(i);
        if (LIST.isEmpty()) { lastFrame = now; return; }
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        int sw = g.getScaledWindowWidth();
        for (int i = 0; i < LIST.size(); i++) {
            Toast t = LIST.get(i);
            float target = TOP + i * (H + GAP);
            t.y = t.y < 0f ? target : t.y + (target - t.y) * Math.min(1f, dt * 12f);

            long age = now - t.born;
            float in = Math.min(1f, age / (float) IN_MS);
            float out = Math.max(0f, (age - (LIFE_MS - OUT_MS)) / (float) OUT_MS);
            float slide = (1f - easeOutBack(in)) * (W + 12) + out * out * (W + 12);
            float alpha = Math.min(1f, age / 160f) * (1f - out * 0.6f);

            int x = sw - W - 6 + (int) slide, y = (int) t.y;
            Ui.rect(g, x, y, W, H, Ui.a(0x090812, 0.90f * alpha));
            Ui.rect(g, x, y, 2, H, Ui.a(t.accent, alpha));
            Ui.border(g, x, y, W, H, Ui.a(0x2A3558, 0.7f * alpha));

            // cara (o la inicial si todavia no llego)
            int fx = x + 7, fy = y + 5, fs = 24;
            if (t.face != null && !Faces.draw(g, t.face, fx, fy, fs, alpha, false)) {
                Ui.rect(g, fx, fy, fs, fs, Ui.a(0x2A2550, alpha));
                Fx.centered(g, t.face.substring(0, 1).toUpperCase(Locale.ROOT), fx + fs / 2, fy + 8, Ui.a(0xA78BFA, alpha));
            }
            int tx = t.face != null ? fx + fs + 7 : x + 8, tw = W - (tx - x) - 6;
            Fx.text(g, Fx.trim(t.title, tw), tx, y + 6, Ui.a(t.accent, alpha));
            Fx.text(g, Fx.trim(t.text, tw), tx, y + 18, Ui.a(0xF1EEFC, alpha));

            // barrita de tiempo que se vacia
            int bw = (int) (W * Math.max(0f, 1f - age / (float) LIFE_MS));
            Ui.rect(g, x, y + H - 2, bw, 2, Ui.a(t.accent, 0.55f * alpha));
        }
    }
}
