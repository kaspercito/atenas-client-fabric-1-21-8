package com.atenas.fabric;

import com.atenas.fabric.gui.Faces;
import com.atenas.fabric.gui.PackInfo;
import com.atenas.fabric.gui.Ui;
import com.atenas.fabric.net.Accounts;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;

/** Menu principal de Atenas (paleta violeta, logo, rejilla). Reemplaza al TitleScreen de vanilla. Sin acceso directo a ningun servidor. */
public final class AtenasTitleScreen extends Screen {
    private boolean tight;
    private int chipX, chipY, chipW, chipH;

    public AtenasTitleScreen() {
        super(Text.literal("Atenas"));
    }

    private int top, logoS, bw, bh, gap, btnY, sh, brandH;
    private float nameScale;
    private Ui.MenuBtn friendsBtn;
    private static boolean readyAnnounced;

    private void layout() {
        tight = height < 300;
        int avail = height - 14 - (tight ? 26 : 46);
        float[][] tiers = {{64, 3f, 112, 24, 26, 5, 4}, {40, 2f, 82, 12, 22, 4, 4}, {28, 1.5f, 64, 8, 20, 3, 3}, {0, 1.5f, 32, 6, 18, 2, 3}};
        int rows = 3, total = 0;
        for (int t = 0; t < tiers.length; t++) {
            float[] v = tiers[t];
            logoS = (int) v[0]; nameScale = v[1]; brandH = (int) v[2]; int sep = (int) v[3]; bh = (int) v[4]; gap = (int) v[5]; sh = bh - (int) v[6]; bw = 250;
            total = brandH + sep + rows * bh + (rows - 1) * gap + 8 + sh;
            if (total <= avail || t == tiers.length - 1) { btnY = brandH + sep; break; }
        }
        top = Math.max(8, 14 + (avail - total) / 2);
        btnY += top;
    }

    @Override
    protected void init() {
        layout();
        int x = width / 2 - bw / 2, y = btnY;
        addDrawableChild(new Ui.MenuBtn(x, y, bw, bh, Text.literal("Un jugador"), false, b -> client.setScreen(new SelectWorldScreen(this))));
        addDrawableChild(new Ui.MenuBtn(x, y + bh + gap, bw, bh, Text.literal("Multijugador"), false, b -> { PackInfo.ensureFeatured(); client.setScreen(new MultiplayerScreen(this)); }));
        addDrawableChild(new Ui.MenuBtn(x, y + 2 * (bh + gap), bw, bh, Text.literal("Opciones"), false, b -> client.setScreen(new OptionsScreen(this, client.options))));
        int y2 = y + 3 * (bh + gap) - gap + 8, third = (bw - 12) / 3;
        friendsBtn = new Ui.MenuBtn(x, y2, third, sh, Text.literal("Amigos"), true, b -> client.setScreen(new com.atenas.fabric.gui.FriendsScreen(this)));
        addDrawableChild(friendsBtn);
        addDrawableChild(new Ui.MenuBtn(x + third + 6, y2, third, sh, Text.literal("Cosm\u00e9ticos"), true, b -> {
            com.atenas.fabric.cosmetics.CosmeticSync.refreshNow();
            client.setScreen(new com.atenas.fabric.cosmetics.CosmeticsScreen(this));
        }));
        addDrawableChild(new Ui.MenuBtn(x + 2 * (third + 6), y2, bw - 2 * (third + 6), sh, Text.literal("Cuenta"), true, b -> client.setScreen(new com.atenas.fabric.gui.AccountScreen(this))));
        addDrawableChild(new Ui.CloseBtn(width - 32, 10, 22, b -> client.scheduleStop()));
        com.atenas.fabric.net.Friends.sync(false, "");
        if (!readyAnnounced) { readyAnnounced = true; System.out.println("[Atenas] MENU_READY"); }
    }

    @Override
    public boolean mouseClicked(double clickX, double clickY, int clickBtn) {
        if (clickBtn == 0 && Ui.inside(clickX, clickY, chipX, chipY, chipW, chipH)) {
            client.setScreen(new com.atenas.fabric.gui.AccountScreen(this));
            return true;
        }
        return super.mouseClicked(clickX, clickY, clickBtn);
    }

    private static int a(int rgb, float al) { return (Math.max(0, Math.min(255, (int) (al * 255f))) << 24) | (rgb & 0xFFFFFF); }

    @Override
    public void renderBackground(DrawContext g, int mx, int my, float delta) { /* se dibuja todo en render() */ }

    @Override
    public void render(DrawContext g, int mx, int my, float delta) {
        try { draw(g, mx, my, delta); }
        catch (Throwable t) {
            System.out.println("[Atenas] el menu de Atenas fallo al dibujar, se vuelve al de Minecraft: " + t);
            t.printStackTrace();
            com.atenas.fabric.shared.Config.set("atenasmenu", false);
            client.setScreen(new net.minecraft.client.gui.screen.TitleScreen());
        }
    }

    private boolean logged;

    private void draw(DrawContext g, int mx, int my, float delta) {
        if (!logged) { logged = true; System.out.println("[Atenas] menu principal: primer dibujo " + width + "x" + height); }
        long t = System.currentTimeMillis();
        Ui.animatedBackdrop(g, width, height);
        int nameY = top;
        if (logoS > 0) {
            float pulse = (float) (0.5 + 0.5 * Math.sin(t * 0.003));
            for (int gg = 3; gg >= 1; gg--) {
                int gs = logoS + gg * 10;
                Ui.rect(g, width / 2 - gs / 2, top + logoS / 2 - gs / 2, gs, gs, ((int) ((8 + 10 * pulse) / gg) << 24) | 0x8B5CF6);
            }
            Ui.logo(g, width / 2 - logoS / 2, top, logoS);
            nameY = top + logoS + 6;
        }
        com.atenas.fabric.gui.Fx.scaled(g, "ATENAS", width / 2f, nameY, nameScale, Ui.ACCENT, true);
        int subY = nameY + (int) (8 * nameScale) + 4;
        com.atenas.fabric.gui.Fx.scaled(g, "C   L   I   E   N   T", width / 2f, subY, 1f, Ui.CYAN, true);
        Ui.rect(g, width / 2 - 60, subY + 12, 120, 1, Ui.ACCENT_DIM);
        Ui.rect(g, width / 2 - 8, subY + 11, 16, 3, Ui.VIOLET);

        if (friendsBtn != null) friendsBtn.setMessage(Text.literal(com.atenas.fabric.net.Friends.badge("Amigos")));
        super.render(g, mx, my, delta);
        Ui.paintButtons(g, textRenderer, children());

        Ui.frame(g, width, height);

        // pie: icono + pack (izquierda), atajo (centro), ficha de la cuenta (derecha)
        int fy = tight ? height - 20 : height - 22, leftX = 18;
        int chipLeft = drawAccountChip(g, mx, my);
        PackInfo.drawIcon(g, leftX, fy - 1, PackInfo.kind());
        String pack = PackInfo.packName();
        int packX = leftX + 14, verEnd = leftX;
        if (packX + com.atenas.fabric.gui.Fx.width(pack) + 8 <= chipLeft) {
            Ui.text(g, pack, packX, fy, Ui.DIM);
            verEnd = packX + com.atenas.fabric.gui.Fx.width(pack);
        }
        String fr = com.atenas.fabric.net.Friends.badge("Shift+Tab  \u00b7  Amigos y chat");
        int frW = com.atenas.fabric.gui.Fx.width(fr), frX = width / 2 - frW / 2;
        if (frX > verEnd + 14 && frX + frW + 14 < chipLeft) Ui.text(g, fr, frX, fy, Ui.DIM);
    }

    /** Ficha de la cuenta (como 1.8.9): cabeza con la skin, nick y estado; al pasar el mouse invita a cambiar de cuenta. Devuelve su borde izquierdo. */
    private int drawAccountChip(DrawContext g, int mx, int my) {
        String user = client.getSession() == null ? "" : client.getSession().getUsername();
        boolean online = com.atenas.fabric.net.Friends.usable();
        boolean off = Accounts.currentIsOffline();
        boolean over;
        if (tight) {
            chipH = 16;
            chipW = 4 + 12 + 6 + com.atenas.fabric.gui.Fx.width(user) + 8 + 5 + 8;
            chipX = width - 18 - chipW; chipY = height - 8 - chipH - 2;
            over = Ui.inside(mx, my, chipX, chipY, chipW, chipH);
            Ui.rect(g, chipX, chipY, chipW, chipH, over ? Ui.PANEL2 : 0xC00B0E1C);
            Ui.border(g, chipX, chipY, chipW, chipH, over ? Ui.VIOLET : 0xFF262A48);
            Ui.rect(g, chipX, chipY, 2, chipH, over ? Ui.ACCENT : Ui.ACCENT_DIM);
            if (!Faces.draw(g, user, chipX + 4, chipY + 2, 12, 1f, off)) {
                Ui.rect(g, chipX + 4, chipY + 2, 12, 12, Ui.VIOLET);
                if (!user.isEmpty()) Ui.centered(g, user.substring(0, 1).toUpperCase(), chipX + 10, chipY + 4, Ui.INK);
            }
            Ui.text(g, user, chipX + 22, chipY + 4, over ? Ui.ACCENT : Ui.TEXT);
            Ui.rect(g, chipX + chipW - 12, chipY + 6, 5, 5, online ? Ui.GREEN : Ui.OFF);
            return chipX;
        }
        chipH = 26;
        int textW = Math.max(com.atenas.fabric.gui.Fx.width(user), Math.max(com.atenas.fabric.gui.Fx.width("\u25cf en l\u00ednea"), com.atenas.fabric.gui.Fx.width("Cambiar de cuenta")));
        chipW = 6 + 18 + 7 + textW + 10;
        chipX = width - 18 - chipW; chipY = height - 16 - chipH;
        over = Ui.inside(mx, my, chipX, chipY, chipW, chipH);
        Ui.rect(g, chipX, chipY, chipW, chipH, over ? Ui.PANEL2 : 0xC00B0E1C);
        Ui.border(g, chipX, chipY, chipW, chipH, over ? Ui.VIOLET : 0xFF262A48);
        Ui.rect(g, chipX, chipY, 2, chipH, over ? Ui.ACCENT : Ui.ACCENT_DIM);
        Ui.rect(g, chipX + 5, chipY + 3, 20, 20, over ? Ui.ACCENT : Ui.ACCENT_DIM);
        if (!Faces.draw(g, user, chipX + 6, chipY + 4, 18, 1f, off)) {
            Ui.rect(g, chipX + 6, chipY + 4, 18, 18, Ui.VIOLET);
            if (!user.isEmpty()) Ui.centered(g, user.substring(0, 1).toUpperCase(), chipX + 15, chipY + 9, Ui.INK);
        }
        Ui.text(g, user, chipX + 31, chipY + 4, over ? Ui.ACCENT : Ui.TEXT);
        if (over) Ui.text(g, "Cambiar de cuenta", chipX + 31, chipY + 14, Ui.VIOLET);
        else Ui.text(g, online ? "\u25cf en l\u00ednea" : "\u25cb desconectado", chipX + 31, chipY + 14, online ? Ui.GREEN : Ui.DIM);
        return chipX;
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}
