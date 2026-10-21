package com.atenas.fabric.voice;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

import com.atenas.fabric.net.Social;

/**
 * Voice chat de proximidad entre usuarios del cliente de Atenas (sin mod en el servidor de Minecraft).
 * Audio: 16 kHz, mono, 16 bit -> mu-law 8 bit, tramas de 20 ms (320 bytes). Se manda por WebSocket al Worker, que lo reenvia
 * solo a quienes estan cerca (48 bloques, misma dimension). El volumen baja con la distancia. Pulsar para hablar.
 */
public final class VoiceClient {
    private VoiceClient() {}

    public static final float RATE = 16000f;
    public static final int FRAME = 320;
    private static final AudioFormat FMT = new AudioFormat(RATE, 16, 1, true, false);

    private static volatile WebSocket ws;
    private static volatile String status = "apagado";
    private static volatile boolean talking, muted, deafened;
    private static volatile String connectedTo = "";
    private static volatile int dim; private static volatile float px, py, pz;
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final Map<String, Speaker> SPEAKERS = new ConcurrentHashMap<>();
    private static volatile long lastHeard; private static volatile String lastSpeaker = "";

    public static String status() { return status; }
    public static boolean talking() { return talking; }
    public static boolean muted() { return muted; }
    public static boolean deafened() { return deafened; }
    public static boolean connected() { return ws != null; }
    public static String lastSpeaker() { return System.currentTimeMillis() - lastHeard < 600 ? lastSpeaker : ""; }
    public static void toggleMute() { muted = !muted; }
    public static void toggleDeafen() { deafened = !deafened; }
    public static void setTalking(boolean t) { talking = t && !muted; }

    /** Se llama cada tick con la posicion; conecta al entrar a un servidor y desconecta al salir. */
    public static void update(String me, String server, int d, double x, double y, double z) {
        dim = d; px = (float) x; py = (float) y; pz = (float) z;
        if (server == null) { stop(); return; }
        if (!server.equals(connectedTo) || ws == null) {
            if (RUNNING.compareAndSet(false, true)) start(me, server);
        }
    }

    public static void stop() {
        WebSocket w = ws; ws = null; connectedTo = "";
        if (w != null) try { w.sendClose(WebSocket.NORMAL_CLOSURE, "bye"); } catch (Exception ignored) { }
        for (Speaker s : SPEAKERS.values()) s.close();
        SPEAKERS.clear();
        status = "apagado";
    }

    private static void start(String me, String server) {
        status = "conectando...";
        try {
            URI uri = URI.create(Social.URL.replaceFirst("^http", "ws") + "/voice?server=" + URLEncoder.encode(server, StandardCharsets.UTF_8));
            Social.http().newWebSocketBuilder()
                .header("x-name", me).header("x-token", Social.token()).connectTimeout(Duration.ofSeconds(8))
                .buildAsync(uri, new WebSocket.Listener() {
                    private ByteBuffer acc = ByteBuffer.allocate(0);
                    @Override public void onOpen(WebSocket w) {
                        ws = w; connectedTo = server; status = "conectado"; RUNNING.set(false);
                        Thread t = new Thread(() -> captureLoop(w), "Atenas-Voice-Mic");
                        t.setDaemon(true); t.start();
                        w.request(1);
                    }
                    @Override public CompletionStage<?> onBinary(WebSocket w, ByteBuffer data, boolean last) {
                        ByteBuffer c = ByteBuffer.allocate(acc.remaining() + data.remaining());
                        c.put(acc); c.put(data); c.flip();
                        if (last) { play(c); acc = ByteBuffer.allocate(0); } else acc = c;
                        w.request(1);
                        return null;
                    }
                    @Override public CompletionStage<?> onClose(WebSocket w, int code, String reason) { ws = null; connectedTo = ""; status = "desconectado"; RUNNING.set(false); return null; }
                    @Override public void onError(WebSocket w, Throwable e) { ws = null; connectedTo = ""; status = "error: " + e.getMessage(); RUNNING.set(false); }
                }).exceptionally(e -> { status = "error: " + e.getMessage(); RUNNING.set(false); return null; });
        } catch (Exception e) { status = "error: " + e.getMessage(); RUNNING.set(false); }
    }

    private static ByteBuffer packet(byte[] mulaw) {
        ByteBuffer b = ByteBuffer.allocate(13 + (mulaw == null ? 0 : mulaw.length));
        b.put((byte) dim); b.putFloat(px); b.putFloat(py); b.putFloat(pz);
        if (mulaw != null) b.put(mulaw);
        b.flip();
        return b;
    }

    private static void captureLoop(WebSocket w) {
        TargetDataLine mic = null;
        long lastPos = 0;
        try {
            mic = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, FMT));
            mic.open(FMT, FRAME * 2 * 8);
            mic.start();
            byte[] pcm = new byte[FRAME * 2];
            while (ws == w) {
                int n = 0;
                while (n < pcm.length) { int r = mic.read(pcm, n, pcm.length - n); if (r <= 0) break; n += r; }
                long now = System.currentTimeMillis();
                if (talking && !muted) {
                    byte[] ul = new byte[FRAME];
                    for (int i = 0; i < FRAME; i++) ul[i] = MuLaw.encode((short) ((pcm[2 * i] & 0xFF) | (pcm[2 * i + 1] << 8)));
                    w.sendBinary(packet(ul), true).join();
                    lastPos = now;
                } else if (now - lastPos > 1000) {
                    w.sendBinary(packet(null), true).join();
                    lastPos = now;
                }
            }
        } catch (Exception e) {
            status = "microfono no disponible: " + e.getMessage();
        } finally {
            if (mic != null) { mic.stop(); mic.close(); }
        }
    }

    private static void play(ByteBuffer d) {
        if (deafened || d.remaining() < 1 + 13 + 1) return;
        int len = d.get() & 0xFF;
        if (d.remaining() < len + 13) return;
        byte[] nb = new byte[len]; d.get(nb);
        String from = new String(nb, StandardCharsets.UTF_8);
        d.get(); float x = d.getFloat(), y = d.getFloat(), z = d.getFloat();
        double dist = Math.sqrt((x - px) * (x - px) + (y - py) * (y - py) + (z - pz) * (z - pz));
        float vol = (float) Math.max(0.0, 1.0 - dist / 48.0);
        if (vol <= 0.01f) return;
        byte[] ul = new byte[d.remaining()]; d.get(ul);
        byte[] pcm = new byte[ul.length * 2];
        for (int i = 0; i < ul.length; i++) {
            int s = Math.round(MuLaw.decode(ul[i]) * vol);
            pcm[2 * i] = (byte) s; pcm[2 * i + 1] = (byte) (s >> 8);
        }
        lastHeard = System.currentTimeMillis(); lastSpeaker = from;
        SPEAKERS.computeIfAbsent(from, k -> new Speaker()).write(pcm);
    }

    private static final class Speaker {
        private SourceDataLine line;
        synchronized void write(byte[] pcm) {
            try {
                if (line == null) { line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, FMT)); line.open(FMT, FRAME * 2 * 10); line.start(); }
                if (line.available() >= pcm.length) line.write(pcm, 0, pcm.length); // si el buffer esta lleno se descarta (evita retraso acumulado)
            } catch (Exception ignored) { }
        }
        synchronized void close() { if (line != null) { line.stop(); line.close(); line = null; } }
    }
}
