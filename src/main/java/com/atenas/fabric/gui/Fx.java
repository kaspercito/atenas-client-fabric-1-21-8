package com.atenas.fabric.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * Letra propia del cliente (la misma AtenasFont de 1.8.9): suave, con anti-alias, para TODAS las pantallas de Atenas.
 * Lee assets/atenasclientfabric/font/ui.ttf (para cambiarla basta reemplazar ese archivo). Los glifos se pintan con Java2D en un atlas
 * a la escala de GUI actual (1 pixel de atlas = 1 pixel de pantalla, asi se ve nitida a cualquier escala) y se dibujan con el pipeline
 * GUI_TEXTURED, el mismo del logo. NO usa el pipeline de texto de Minecraft. Si algo falla, {@link #ready()} da false y se usa la letra de Minecraft.
 * Medidas en unidades de GUI; el ancho de cada letra sale de una medicion fija a 4x, asi el diseno no cambia con la escala.
 */
public final class Fx {
    private Fx() {}

    private static final float PX = 9.6f;                 // alto de letra en unidades de GUI
    private static final int REF = 4;                     // escala de referencia para medir
    private static final int FIRST = 32, LAST = 255, COLS = 16;
    private static final int DOT_FULL = LAST + 1, DOT_RING = LAST + 2, TOTAL = LAST + 3;
    private static final int CELL_W = 14, CELL_H = 16;    // celda de un glifo en unidades de GUI
    private static final int ROWS = (TOTAL - FIRST + COLS - 1) / COLS;
    private static final int[] PALETTE = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    private static final class Atlas { Identifier id; int ss, w, h, padTop, padLeft; float ascent; }

    private static final Map<Integer, Atlas> ATLASES = new HashMap<>();
    private static final float[] ADV = new float[TOTAL];  // avance de cada letra en pixeles a escala REF
    private static Font base;
    private static boolean metricsDone, failed;
    private static int counter;

    public static boolean ready() {
        if (failed) return false;
        if (!metricsDone) measure();
        return !failed;
    }

    private static Font baseFont() throws Exception {
        if (base == null) {
            try (InputStream in = Fx.class.getResourceAsStream("/assets/atenasclientfabric/font/ui.ttf")) {
                if (in == null) throw new IllegalStateException("falta ui.ttf");
                base = Font.createFont(Font.TRUETYPE_FONT, in);
            }
        }
        return base;
    }

    private static void measure() {
        metricsDone = true;
        try {
            Font f = baseFont().deriveFont(PX * REF);
            BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = probe.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setFont(f);
            FontMetrics fm = g.getFontMetrics();
            for (int c = FIRST; c <= LAST; c++) ADV[c] = fm.charWidth((char) c);
            ADV[DOT_FULL] = ADV[DOT_RING] = PX * REF * 0.5f * 1.45f;
            g.dispose();
        } catch (Throwable t) {
            System.out.println("[Atenas] fuente propia no disponible: " + t);
            failed = true;
        }
    }

    private static int guiScale() {
        try { return Math.max(1, Math.min(6, (int) Math.round(MinecraftClient.getInstance().getWindow().getScaleFactor()))); }
        catch (Throwable t) { return REF; }
    }

    private static Atlas atlas() {
        if (!ready()) return null;
        int ss = guiScale();
        Atlas a = ATLASES.get(ss);
        if (a != null) return a;
        a = build(ss);
        if (a == null) { failed = true; return null; }
        ATLASES.put(ss, a);
        return a;
    }

    private static Atlas build(int ss) {
        try {
            Font f = baseFont().deriveFont(PX * ss);
            int cw = CELL_W * ss, ch = CELL_H * ss, w = COLS * cw, h = ROWS * ch;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setFont(f);
            g.setColor(Color.WHITE);
            FontMetrics fm = g.getFontMetrics();
            Atlas a = new Atlas();
            a.ss = ss; a.w = w; a.h = h;
            a.padTop = 2 * ss;
            a.padLeft = Math.max(1, ss / 2);
            a.ascent = fm.getAscent();
            for (int c = FIRST; c <= LAST; c++) {
                int i = c - FIRST, cx = (i % COLS) * cw, cy = (i / COLS) * ch;
                g.drawString(String.valueOf((char) c), cx + a.padLeft, cy + a.padTop + a.ascent);
            }
            float d = PX * ss * 0.5f, adv = ADV[DOT_FULL] * ss / REF;
            for (int c = DOT_FULL; c <= DOT_RING; c++) {
                int i = c - FIRST, cx = (i % COLS) * cw, cy = (i / COLS) * ch;
                float ox = cx + a.padLeft + (adv - d) / 2f, oy = cy + a.padTop + a.ascent - d * 1.05f;
                if (c == DOT_FULL) g.fill(new java.awt.geom.Ellipse2D.Float(ox, oy, d, d));
                else {
                    g.setStroke(new java.awt.BasicStroke(Math.max(2f, d * 0.16f)));
                    g.draw(new java.awt.geom.Ellipse2D.Float(ox + 1.5f, oy + 1.5f, d - 3f, d - 3f));
                }
            }
            g.dispose();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            NativeImage ni = NativeImage.read(new ByteArrayInputStream(bos.toByteArray()));
            a.id = Identifier.of("atenasclientfabric", "dyn/font_" + ss + "_" + (counter++));
            MinecraftClient.getInstance().getTextureManager().registerTexture(a.id, new NativeImageBackedTexture(() -> "atenas font", ni));
            return a;
        } catch (Throwable t) {
            System.out.println("[Atenas] no se pudo armar la fuente propia: " + t);
            return null;
        }
    }

    private static int idx(char c) {
        if (c == '\u25cf') return DOT_FULL;
        if (c == '\u25cb') return DOT_RING;
        if (c == '\u2022') return DOT_FULL;
        if (c == '\u2026') return '.';
        return c < FIRST || c > LAST ? '?' : c;
    }

    // ------------------------------------------------------------ medidas

    /** Ancho en unidades de GUI (ignora los codigos de color). */
    public static float widthF(String s) {
        if (s == null) return 0f;
        if (!ready()) return MinecraftClient.getInstance().textRenderer.getWidth(stripCodes(s));
        float w = 0f;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7' && i + 1 < s.length()) { i++; continue; }
            w += ADV[idx(c)] / REF;
        }
        return w;
    }

    public static int width(String s) { return (int) Math.ceil(widthF(s)); }

    /** Recorta por ancho (sin codigos de color a medias). */
    public static String trim(String s, int w) {
        if (!ready()) return MinecraftClient.getInstance().textRenderer.trimToWidth(s, w);
        float acc = 0f;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7' && i + 1 < s.length()) { i++; continue; }
            acc += ADV[idx(c)] / REF;
            if (acc > w) return s.substring(0, i);
        }
        return s;
    }

    public static String stripCodes(String s) {
        if (s == null || s.indexOf('\u00a7') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7' && i + 1 < s.length()) { i++; continue; }
            if (c == '\u00a7') continue;
            sb.append(c);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------ dibujo

    private static void drawImpl(DrawContext g, String s, float x, float y, int argb, boolean shadow, boolean spaced) {
        if (s == null || s.isEmpty()) return;
        if ((argb >>> 24) == 0) argb |= 0xFF000000;
        Atlas a = atlas();
        if (a == null) {
            MinecraftClient mc = MinecraftClient.getInstance();
            g.drawText(mc.textRenderer, stripCodes(s), Math.round(x), Math.round(y), argb, shadow);
            return;
        }
        g.getMatrices().pushMatrix();
        g.getMatrices().scale(1f / a.ss, 1f / a.ss);
        if (shadow) emit(g, a, s, x + 0.75f, y + 0.75f, (((argb >>> 24) * 0x70 / 255) << 24), true, spaced);
        emit(g, a, s, x, y, argb, false, spaced);
        g.getMatrices().popMatrix();
    }

    private static void emit(DrawContext g, Atlas a, String s, float x, float y, int argb, boolean flat, boolean spaced) {
        int alpha = argb >>> 24;
        if (alpha == 0) return;
        int rgb = argb & 0xFFFFFF;
        int cellW = CELL_W * a.ss, cellH = CELL_H * a.ss;
        int top = Math.round((y + 7f) * a.ss - a.padTop - a.ascent);
        float cx = x * a.ss; // en pixeles de atlas
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7' && i + 1 < s.length()) {
                char k = Character.toLowerCase(s.charAt(++i));
                int p = "0123456789abcdef".indexOf(k);
                if (p >= 0 && !flat) rgb = PALETTE[p];
                else if (k == 'r' && !flat) rgb = argb & 0xFFFFFF;
                continue;
            }
            int ci = idx(c), n = ci - FIRST;
            float adv = ADV[ci] * a.ss / REF;
            if (c != ' ') {
                int u = (n % COLS) * cellW, v = (n / COLS) * cellH;
                g.drawTexture(RenderPipelines.GUI_TEXTURED, a.id, Math.round(cx - a.padLeft), top, (float) u, (float) v, cellW, cellH, cellW, cellH, a.w, a.h, (alpha << 24) | rgb);
            }
            cx += spaced ? (float) (Math.ceil(ADV[ci] / REF) + 1f) * a.ss : adv;
        }
    }

    /** Texto con la letra de Atenas y sombra suave (como Draw.text de 1.8.9). */
    public static void text(DrawContext g, String s, int x, int y, int color) { drawImpl(g, s, x, y, color, true, false); }

    public static void text(DrawContext g, String s, float x, float y, int color, boolean shadow) { drawImpl(g, s, x, y, color, shadow, false); }

    public static void centered(DrawContext g, String s, int cx, int y, int color) { text(g, s, cx - width(s) / 2, y, color); }

    /** Texto en mayusculas con las letras separadas 1 px (titulos futuristas). Los codigos de color se ignoran. */
    public static void heading(DrawContext g, String s, int x, int y, int color) {
        s = stripCodes(s).toUpperCase();
        if (ready()) { drawImpl(g, s, x, y, color, true, true); return; }
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            text(g, ch, cx, y, color);
            cx += width(ch) + 1;
        }
    }

    public static int headingWidth(String s) {
        s = stripCodes(s).toUpperCase();
        int w = 0;
        for (int i = 0; i < s.length(); i++) w += width(String.valueOf(s.charAt(i))) + 1;
        return w;
    }

    /**
     * Texto de un boton centrado en [x, x+w): si el titulo con letras separadas cabe, se usa; si no, mayusculas normales,
     * luego tal cual y, por ultimo, recortado con "...". Asi ningun texto se sale del boton.
     */
    public static void headingFit(DrawContext g, String s, int x, int w, int y, int color, int shift) {
        String clean = stripCodes(s);
        int avail = Math.max(8, w - 2);
        int tw = headingWidth(clean);
        if (tw <= avail) { heading(g, clean, x + (w - tw) / 2 + shift, y, color); return; }
        String up = clean.toUpperCase();
        if (width(up) > avail) up = clean;
        up = fit(up, avail);
        text(g, up, x + (w - width(up)) / 2, y, color);
    }

    public static void scaled(DrawContext g, String s, float x, float y, float scale, int color, boolean centered) {
        float w = width(s) * scale;
        g.getMatrices().pushMatrix();
        g.getMatrices().translate(centered ? x - w / 2f : x, y);
        g.getMatrices().scale(scale, scale);
        drawImpl(g, s, 0f, 0f, color, true, false);
        g.getMatrices().popMatrix();
    }

    /** Recorta con "..." si no cabe en w unidades. */
    public static String fit(String s, int w) {
        if (s == null) return "";
        if (width(s) <= w) return s;
        return trim(s, Math.max(0, w - width("..."))) + "...";
    }

    /** Parte el texto en lineas de w unidades; si sobran lineas, la ultima termina en "...". */
    public static List<String> wrap(String s, int w, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : (s == null ? "" : s).split(" ")) {
            String test = cur.length() == 0 ? word : cur + " " + word;
            if (cur.length() > 0 && width(test) > w) { lines.add(cur.toString()); cur = new StringBuilder(word); }
            else cur = new StringBuilder(test);
        }
        if (cur.length() > 0) lines.add(cur.toString());
        if (lines.size() <= maxLines) return lines;
        List<String> out = new ArrayList<>(lines.subList(0, maxLines));
        out.set(maxLines - 1, fit(out.get(maxLines - 1) + "...", w));
        return out;
    }
}
