package com.atenas.fabric;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * Texto con la letra del cliente (ui.ttf), igual que la pantalla de carga del 1.8.9: se pinta con Java2D en una textura (blanca, se tine al dibujar)
 * y se dibuja con el pipeline GUI_TEXTURED, el mismo del logo. NO usa el pipeline de texto de Minecraft (gui_text), que durante el arranque
 * todavia no tiene shaders ni fuente cargados. Se usa en la pantalla de carga; siempre en el hilo de render.
 */
final class TextTex {
    private TextTex() {}

    private static final class Entry { Identifier id; int w, h; }
    private static final Map<String, Entry> CACHE = new HashMap<>();
    private static final Entry NONE = new Entry();
    private static Font base;
    private static int counter;

    /**
     * Dibuja "s" centrado en cx (unidades de GUI), con la parte de arriba en y. size = alto de letra en unidades de GUI.
     * Devuelve el alto usado en unidades de GUI.
     */
    static float draw(DrawContext g, MinecraftClient mc, String s, float size, int rgb, float cx, float y, boolean bold, float alpha) {
        if (s == null || s.isEmpty()) return size;
        int scale = Math.max(1, (int) mc.getWindow().getScaleFactor());
        int px = Math.max(8, Math.round(size * scale));
        String key = s + "|" + px + "|" + bold + "|" + scale;
        Entry e = CACHE.get(key);
        if (e == null) {
            e = make(mc, s, px, bold, scale);
            CACHE.put(key, e);
        }
        if (e == NONE || e.id == null) return size;
        int gw = e.w / scale, gh = e.h / scale;
        int col = (Math.max(0, Math.min(255, (int) (alpha * 255f))) << 24) | (rgb & 0xFFFFFF);
        g.drawTexture(RenderPipelines.GUI_TEXTURED, e.id, Math.round(cx - gw / 2f), Math.round(y), 0f, 0f, gw, gh, gw, gh, gw, gh, col);
        return gh;
    }

    private static Entry make(MinecraftClient mc, String s, int px, boolean bold, int scale) {
        try {
            if (base == null) {
                try (InputStream in = TextTex.class.getResourceAsStream("/assets/atenasclientfabric/font/ui.ttf")) {
                    base = in != null ? Font.createFont(Font.TRUETYPE_FONT, in) : new Font("SansSerif", Font.PLAIN, 12);
                }
            }
            Font f = base.deriveFont(bold ? Font.BOLD : Font.PLAIN, (float) px);
            BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D pg = probe.createGraphics();
            pg.setFont(f);
            FontMetrics fm = pg.getFontMetrics();
            int tw = fm.stringWidth(s) + 8, th = fm.getHeight() + 6, asc = fm.getAscent();
            pg.dispose();
            // ancho y alto multiplos de la escala de GUI, asi 1 pixel de textura = 1 pixel de pantalla
            tw = ((tw + scale - 1) / scale) * scale;
            th = ((th + scale - 1) / scale) * scale;
            BufferedImage img = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(f);
            g2.setColor(Color.WHITE);
            g2.drawString(s, 4, 3 + asc);
            g2.dispose();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            NativeImage ni = NativeImage.read(new ByteArrayInputStream(bos.toByteArray()));
            Entry e = new Entry();
            e.id = Identifier.of("atenasclientfabric", "dyn/text_" + (counter++));
            e.w = tw; e.h = th;
            mc.getTextureManager().registerTexture(e.id, new NativeImageBackedTexture(() -> "atenas text", ni));
            return e;
        } catch (Throwable t) {
            System.out.println("[Atenas] texto de la pantalla de carga no disponible: " + t);
            return NONE;
        }
    }
}
