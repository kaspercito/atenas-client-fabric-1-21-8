package com.atenas.fabric.gui;

import java.io.InputStream;
import java.util.List;
import java.util.Random;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Paleta y piezas de dibujo de Atenas: son las MISMAS de 1.8.9 (Theme + Draw + AtenasButton + MenuButton + CloseButton + Backdrop).
 * Violeta sobre azul noche, cian de apoyo, esquinas cortadas en diagonal y la letra propia (ver {@link Fx}).
 * Los metodos que reciben un TextRenderer lo ignoran: todo el texto de las pantallas de Atenas sale con la letra del cliente.
 */
public final class Ui {
    private Ui() {}

    public static final int BG = 0xF2090812, SIDE = 0xFF0C0A1A, PANEL = 0xFF14122A, PANEL2 = 0xFF1E1B38, HOVER = 0xFF191631, BORDER = 0xFF2B2848;
    public static final int ACCENT = 0xFFA78BFA, ACCENT_DIM = 0xFF6D4FC7, VIOLET = 0xFF8B5CF6, MAGENTA = 0xFFC026D3, PINK = 0xFFEC4899, CYAN = 0xFF22D3EE;
    public static final int GREEN = 0xFF34D399, AMBER = 0xFFFBBF24, RED = 0xFFFB7185;
    public static final int TEXT = 0xFFF1EEFC, DIM = 0xFF9B97B8, OFF = 0xFF3A3658, INK = 0xFF090812;

    public static int a(int rgb, float al) { return (Math.max(0, Math.min(255, (int) (al * 255f))) << 24) | (rgb & 0xFFFFFF); }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    // ------------------------------------------------------------ formas

    public static void rect(DrawContext g, int x, int y, int w, int h, int color) { g.fill(x, y, x + w, y + h, color); }

    public static void border(DrawContext g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** Rectangulo con las esquinas cortadas en diagonal ("cut" pixeles), igual que Draw.chamfer de 1.8.9. */
    public static void chamfer(DrawContext g, int x, int y, int w, int h, int cut, int color) {
        g.fill(x, y + cut, x + w, y + h - cut, color);
        for (int i = 0; i < cut; i++) {
            g.fill(x + cut - i, y + i, x + w - (cut - i), y + i + 1, color);
            g.fill(x + cut - i, y + h - 1 - i, x + w - (cut - i), y + h - i, color);
        }
    }

    /** Contorno de 1 px de un rectangulo con esquinas cortadas. */
    public static void chamferBorder(DrawContext g, int x, int y, int w, int h, int cut, int color) {
        g.fill(x + cut, y, x + w - cut, y + 1, color);
        g.fill(x + cut, y + h - 1, x + w - cut, y + h, color);
        g.fill(x, y + cut, x + 1, y + h - cut, color);
        g.fill(x + w - 1, y + cut, x + w, y + h - cut, color);
        for (int i = 0; i < cut; i++) {
            g.fill(x + cut - 1 - i, y + i, x + cut - i, y + i + 1, color);
            g.fill(x + w - cut + i, y + i, x + w - cut + i + 1, y + i + 1, color);
            g.fill(x + cut - 1 - i, y + h - 1 - i, x + cut - i, y + h - i, color);
            g.fill(x + w - cut + i, y + h - 1 - i, x + w - cut + i + 1, y + h - i, color);
        }
    }

    /** Degradado horizontal (a la izquierda c1, a la derecha c2). */
    public static void hgrad(DrawContext g, int x, int y, int w, int h, int c1, int c2) {
        if (w <= 0 || h <= 0) return;
        int n = Math.max(1, Math.min(w, 48));
        for (int i = 0; i < n; i++) {
            float t = n == 1 ? 0 : i / (float) (n - 1);
            int x1 = x + i * w / n, x2 = x + (i + 1) * w / n;
            g.fill(x1, y, x2, y + h, mix(c1, c2, t));
        }
    }

    /** Degradado como Draw.gradient de 1.8.9: vertical (arriba c1, abajo c2) u horizontal. */
    public static void gradient(DrawContext g, int x, int y, int w, int h, int c1, int c2, boolean vertical) {
        if (w <= 0 || h <= 0) return;
        if (vertical) g.fillGradient(x, y, x + w, y + h, c1, c2);
        else hgrad(g, x, y, w, h, c1, c2);
    }

    public static int mix(int c1, int c2, float t) {
        int r = 0;
        for (int s = 24; s >= 0; s -= 8) { int p = (c1 >>> s) & 255, q = (c2 >>> s) & 255; r |= ((int) (p + (q - p) * t) & 255) << s; }
        return r;
    }

    /** Rectangulo con las esquinas cortadas (look "tech"): igual que Draw.card. */
    public static void card(DrawContext g, int x, int y, int w, int h, int fill, int border) {
        int cut = h >= 18 && w >= 18 ? 3 : 2;
        chamfer(g, x, y, w, h, cut, fill);
        chamferBorder(g, x, y, w, h, cut, border);
    }

    /** Boton chico plano (el de las listas): cuadro con borde que se pinta del color de su accion al pasar el mouse. */
    public static void smallButton(DrawContext g, String label, int x, int y, int w, int h, boolean over, int hoverColor, boolean dim) {
        rect(g, x, y, w, h, over ? PANEL2 : PANEL);
        border(g, x, y, w, h, over ? hoverColor : BORDER);
        centered(g, label, x + w / 2, y + (h - 8) / 2, dim ? OFF : over ? hoverColor : TEXT);
    }

    /** Boton de las ventanas de Atenas (Draw.button de 1.8.9). */
    public static void button(DrawContext g, String label, int x, int y, int w, int h, boolean hover) {
        card(g, x, y, w, h, hover ? PANEL2 : PANEL, hover ? ACCENT : BORDER);
        centered(g, label, x + w / 2, y + (h - 8) / 2, hover ? ACCENT : TEXT);
    }

    /** Switch del menu (Draw.toggle de 1.8.9): encendido degradado violeta->magenta con borde claro; apagado gris. */
    public static void toggle(DrawContext g, int x, int y, boolean on) {
        if (on) {
            hgrad(g, x, y, 22, 10, VIOLET, MAGENTA);
            border(g, x, y, 22, 10, ACCENT);
        } else {
            card(g, x, y, 22, 10, OFF, 0xFF4A4670);
        }
        rect(g, on ? x + 13 : x + 1, y + 1, 8, 8, on ? TEXT : 0xFFB8B4D4);
    }

    /** Icono de ajustes: tres barras con perillas. */
    public static void sliders(DrawContext g, int x, int y, int c) {
        rect(g, x, y + 1, 11, 1, c);  rect(g, x + 6, y, 2, 3, c);
        rect(g, x, y + 5, 11, 1, c);  rect(g, x + 2, y + 4, 2, 3, c);
        rect(g, x, y + 9, 11, 1, c);  rect(g, x + 7, y + 8, 2, 3, c);
    }

    // ------------------------------------------------------------ texto (letra propia)

    public static void text(DrawContext g, String s, int x, int y, int color) { Fx.text(g, s, x, y, color); }
    public static void centered(DrawContext g, String s, int cx, int y, int color) { Fx.centered(g, s, cx, y, color); }
    public static void text(DrawContext g, TextRenderer tr, String s, int x, int y, int color) { Fx.text(g, s, x, y, color); }
    public static void centered(DrawContext g, TextRenderer tr, String s, int cx, int y, int color) { Fx.centered(g, s, cx, y, color); }
    public static String fit(TextRenderer tr, String s, int w) { return Fx.fit(s, w); }
    public static String fit(String s, int w) { return Fx.fit(s, w); }

    // ------------------------------------------------------------ logo

    private static final Identifier LOGO = Identifier.of("atenasclientfabric", "dyn/ui_logo");
    private static boolean logoTried, logoOk;

    private static boolean loadLogo() {
        try (InputStream in = Ui.class.getResourceAsStream("/assets/atenasclientfabric/textures/gui/logo.png")) {
            if (in == null) return false;
            NativeImage img = NativeImage.read(in);
            MinecraftClient.getInstance().getTextureManager().registerTexture(LOGO, new NativeImageBackedTexture(() -> "atenas ui logo", img));
            return true;
        } catch (Throwable t) { return false; }
    }

    /** Logo de Atenas a size x size (el mismo Draw.logo de 1.8.9). */
    public static void logo(DrawContext g, int x, int y, int size) {
        if (!logoTried) { logoTried = true; logoOk = loadLogo(); }
        if (logoOk) g.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, x, y, 0f, 0f, size, size, size, size, size, size);
    }

    // ------------------------------------------------------------ fondos

    /** Greca arriba y esquinas HUD (las de todas las pantallas propias). */
    public static void frame(DrawContext g, int w, int h) {
        int ga = 0x558B5CF6;
        for (int gx = 0; gx + 12 <= w; gx += 12) {
            g.fill(gx, 3, gx + 8, 4, ga); g.fill(gx, 3, gx + 1, 9, ga); g.fill(gx, 8, gx + 5, 9, ga); g.fill(gx + 4, 5, gx + 5, 8, ga);
        }
        int c = 0xAA8B5CF6;
        g.fill(8, 14, 30, 15, c); g.fill(8, 14, 9, 36, c);
        g.fill(8, h - 9, 30, h - 8, c); g.fill(8, h - 30, 9, h - 8, c);
    }

    /** Fondo sencillo (sin animacion) para las pantallas que lo usaban: azul noche con degradado violeta y greca. */
    public static void backdrop(DrawContext g, int w, int h) {
        g.fillGradient(0, 0, w, h, 0xFF04030B, 0xFF120A2E);
        g.fillGradient(0, (int) (h * 0.55f), w, h, a(0x22D3EE, 0f), a(0x8B5CF6, 0.20f));
        frame(g, w, h);
    }

    private static final int STARS = 110, ROWS = 6, COLS = 10;
    private static final float[] SX = new float[STARS], SY = new float[STARS], SV = new float[STARS], SS = new float[STARS];
    static {
        Random r = new Random(1234L);
        for (int i = 0; i < STARS; i++) { SX[i] = r.nextFloat(); SY[i] = r.nextFloat(); SV[i] = 0.2f + r.nextFloat() * 0.8f; SS[i] = r.nextFloat(); }
    }

    /**
     * Fondo animado de Atenas (Backdrop de 1.8.9): noche con horizonte violeta, rejilla de neon en perspectiva, estrellas que titilan
     * y una linea de escaneo. Lo usan el menu principal y la ventana de Amigos (cuando no hay mundo detras).
     */
    public static void animatedBackdrop(DrawContext g, int width, int height) {
        long t = System.currentTimeMillis();
        int hz = height - 90;
        g.fillGradient(0, 0, width, height, 0xFF080A14, 0xFF171C3C);
        g.fillGradient(0, hz - 40, width, hz, 0x008B5CF6, 0x268B5CF6);
        g.fill(0, hz, width, height, 0xFF070913);
        g.fill(0, hz, width, hz + 1, 0x808B5CF6);
        for (int i = 1; i <= ROWS; i++) {
            float k = (float) Math.pow((i + (t % 2000) / 2000f) / 7f, 2.0);
            int yy = hz + (int) (k * (height - hz));
            g.fill(0, yy, width, yy + 1, (((int) (60 * k) + 14) << 24) | 0x8B5CF6);
        }
        int cx = width / 2, span = Math.max(1, height - hz);
        for (int i = -COLS; i <= COLS; i++) {
            float bx = cx + i * (width / 14), ex = cx + i * (width / 3);
            for (int yy = hz; yy < height; yy += 2) {
                float p = (yy - hz) / (float) span;
                int xx = Math.round(bx + (ex - bx) * p);
                g.fill(xx, yy, xx + 1, Math.min(height, yy + 2), (((int) (10 + 40 * p)) << 24) | 0x8B5CF6);
            }
        }
        for (int i = 0; i < STARS; i++) {
            float yy = (SY[i] * hz + t * SV[i] * 0.003f) % hz;
            int al = (int) (50 + 110 * (0.5 + 0.5 * Math.sin(t * 0.002 + i)));
            int size = SS[i] > 0.8f ? 2 : 1;
            int px = (int) (SX[i] * width), py = (int) yy;
            g.fill(px, py, px + size, py + size, (al << 24) | 0xFFFFFF);
        }
        int scan = (int) ((t / 14) % (height + 80)) - 40;
        g.fillGradient(0, scan, width, scan + 14, 0x008B5CF6, 0x128B5CF6);
    }

    // ------------------------------------------------------------ botones

    /** Cualquier boton de Atenas que se dibuja a mano encima del widget invisible de vanilla. */
    public interface Paintable { void paint(DrawContext g); }

    /** Boton Atenas (AtenasButton): cristal oscuro con esquinas cortadas, borde de neon y regla que crece desde el centro al pasar el mouse. */
    public static class Btn extends ButtonWidget implements Paintable {
        public float glow;
        public Btn(int x, int y, int w, int h, net.minecraft.text.Text label, ButtonWidget.PressAction press) {
            super(x, y, w, h, label, press, DEFAULT_NARRATION_SUPPLIER);
            setAlpha(0f); // el widget de vanilla queda invisible: lo dibujamos nosotros encima (paint)
        }
        /** Desde 1.21.9 PressableWidget exige drawIcon: el boton se pinta entero en paint(), asi que aqui no hay nada que dibujar. */
        public void drawIcon(DrawContext g, int mouseX, int mouseY, float delta) { }
        public void paint(DrawContext g, TextRenderer tr) { paint(g); }
        @Override
        public void paint(DrawContext g) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean on = isHovered() && active;
            glow += ((on ? 1f : 0f) - glow) * 0.22f;
            int cut = h >= 18 ? 4 : 3;
            chamfer(g, x, y, w, h, cut, active ? 0xE60D0B1E : 0xA0090812);
            gradient(g, x + cut, y + h / 2, w - 2 * cut, h / 2 - 1, 0x00A78BFA, ((int) (0x46 * glow) << 24) | 0x8B5CF6, true);
            chamferBorder(g, x, y, w, h, cut, on ? ACCENT : (active ? 0xFF3A3466 : 0xFF241F3C));
            int bar = (int) (glow * (w - 2 * cut));
            if (bar > 0) {
                hgrad(g, x + w / 2 - bar / 2, y + h - 2, bar / 2 + 1, 2, 0x008B5CF6, VIOLET);
                hgrad(g, x + w / 2, y + h - 2, bar / 2, 2, CYAN, 0x0022D3EE);
            }
            rect(g, x + w - cut - 5, y, 5, 1, on ? CYAN : ACCENT_DIM);
            rect(g, x + cut, y + h - 1, 5, 1, on ? PINK : ACCENT_DIM);
            int tc = !active ? OFF : on ? 0xFFFFFFFF : TEXT;
            Fx.headingFit(g, getMessage().getString(), x + cut, w - 2 * cut, y + (h - 8) / 2, tc, (int) (glow * 1.5f));
        }
    }

    /** Boton del menu principal (MenuButton): titulo a la izquierda, esquinas cortadas y neon que crece al pasar el mouse. */
    public static class MenuBtn extends ButtonWidget implements Paintable {
        private final boolean secondary;
        public float glow;
        public MenuBtn(int x, int y, int w, int h, net.minecraft.text.Text label, boolean secondary, ButtonWidget.PressAction press) {
            super(x, y, w, h, label, press, DEFAULT_NARRATION_SUPPLIER);
            this.secondary = secondary;
            setAlpha(0f);
        }
        public void drawIcon(DrawContext g, int mouseX, int mouseY, float delta) { }
        @Override
        public void paint(DrawContext g) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight(), cut = 4;
            boolean on = isHovered() && active;
            glow += ((on ? 1f : 0f) - glow) * 0.22f;
            chamfer(g, x, y, w, h, cut, secondary ? 0xCC0A0C1B : 0xE60C0F20);
            hgrad(g, x + cut, y + 1, w - 2 * cut, h - 2, 0x008B5CF6, ((int) (0x40 * glow) << 24) | 0x8B5CF6);
            chamferBorder(g, x, y, w, h, cut, on ? VIOLET : 0xFF2A2E52);
            int bh = 6 + (int) (glow * (h - 12));
            rect(g, x + 3, y + (h - bh) / 2, 2, bh, on ? CYAN : (secondary ? 0xFF4A4F78 : ACCENT_DIM));
            int bar = (int) (glow * (w - 2 * cut));
            if (bar > 0) hgrad(g, x + cut, y + h - 1, bar, 1, VIOLET, 0x00EC4899);
            int ty = y + (h - 8) / 2;
            int tc = !active ? OFF : on ? 0xFFFFFFFF : TEXT;
            Fx.heading(g, getMessage().getString(), x + 14 + (int) (glow * 3), ty, tc);
        }
    }

    /** Cuadrado con una X dibujada (no texto): se pone rojo al pasar el mouse (CloseButton de 1.8.9). */
    public static class CloseBtn extends ButtonWidget implements Paintable {
        public CloseBtn(int x, int y, int size, ButtonWidget.PressAction press) {
            super(x, y, size, size, net.minecraft.text.Text.literal("X"), press, DEFAULT_NARRATION_SUPPLIER);
            setAlpha(0f);
        }
        public void drawIcon(DrawContext g, int mouseX, int mouseY, float delta) { }
        @Override
        public void paint(DrawContext g) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean on = isHovered();
            rect(g, x, y, w, h, on ? 0xFFE5384F : 0xB00D1020);
            border(g, x, y, w, h, on ? 0xFFFF8A9A : 0xFF3A3F66);
            int pad = 6, n = w - pad * 2, c = on ? 0xFFFFFFFF : DIM;
            for (int i = 0; i < n; i++) {
                rect(g, x + pad + i, y + pad + i, 1, 1, c);
                rect(g, x + pad + n - 1 - i, y + pad + i, 1, 1, c);
            }
        }
    }

    public static void paintButtons(DrawContext g, TextRenderer tr, Iterable<? extends net.minecraft.client.gui.Element> children) {
        for (net.minecraft.client.gui.Element e : children) if (e instanceof Paintable p) p.paint(g);
    }

    /** Lista de lineas ya partidas, pintadas una bajo otra. Devuelve la y siguiente. */
    public static int lines(DrawContext g, List<String> ls, int x, int y, int step, int color) {
        for (String l : ls) { text(g, l, x, y, color); y += step; }
        return y;
    }
}
