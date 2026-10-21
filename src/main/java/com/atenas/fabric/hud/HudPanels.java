package com.atenas.fabric.hud;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.atenas.fabric.net.TeamSync;
import com.atenas.fabric.shared.Config;
import com.atenas.fabric.shared.Waypoint;
import com.atenas.fabric.shared.WaypointStore;
import com.atenas.fabric.voice.VoiceClient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Los paneles del HUD, uno por modulo como en 1.8.9 (cada uno se mueve y escala por separado en el Editor HUD y tiene
 * Escala, Fondo, Sombra de texto, Barra de acento y Color). Se dibujan en coordenadas locales (0,0).
 */
public final class HudPanels {
    private HudPanels() {}

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] DIRS = {"S", "SO", "O", "NO", "N", "NE", "E", "SE"};
    private static final String[] ARROWS = {"\u2191", "\u2197", "\u2192", "\u2198", "\u2193", "\u2199", "\u2190", "\u2196"};
    private static final String[] ROMAN = {"", "", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    public static abstract class Panel {
        public final String id, name; public final int ax, ay; public final boolean right;
        Panel(String id, String name, int ax, int ay, boolean right) { this.id = id; this.name = name; this.ax = ax; this.ay = ay; this.right = right; }
        public boolean enabled() { return Modules.on(id); }
        public abstract int width(MinecraftClient mc);
        public abstract int height(MinecraftClient mc);
        public abstract void draw(DrawContext g, MinecraftClient mc, boolean editing);
    }

    /** Fondo + brillo + barra de acento (igual que HudModule.box de 1.8.9). */
    static void box(DrawContext g, String id, int w, int h) {
        if (!Settings.b(id, "bg")) return;
        g.fill(0, 0, w, h, 0xB0090812);
        g.fill(0, 0, w, 1, 0x30FFFFFF);
        if (Settings.b(id, "accent")) {
            g.fillGradient(0, 0, 2, h, 0xFFA78BFA, 0xFFEC4899);
            g.fill(2, h - 1, Math.max(3, Math.min(w, 14)), h, 0xFF22D3EE);
        }
    }

    private static void txt(DrawContext g, MinecraftClient mc, String id, String s, int x, int y) {
        g.drawText(mc.textRenderer, s, x, y, Settings.color(id, "color"), Settings.b(id, "shadow"));
    }

    private static abstract class TextPanel extends Panel {
        TextPanel(String id, String name, int ax, int ay, boolean right) { super(id, name, ax, ay, right); }
        abstract String text(MinecraftClient mc);
        public int width(MinecraftClient mc) { return mc.textRenderer.getWidth(text(mc)) + 12; }
        public int height(MinecraftClient mc) { return 15; }
        public void draw(DrawContext g, MinecraftClient mc, boolean editing) {
            String t = text(mc);
            box(g, id, mc.textRenderer.getWidth(t) + 12, 15);
            txt(g, mc, id, t, 7, 4);
        }
    }

    private static String arrow(MinecraftClient mc, double dx, double dz) {
        double target = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = MathHelper.wrapDegrees(target - mc.player.getYaw());
        return ARROWS[((int) Math.round(rel / 45.0) + 8) % 8];
    }

    private static final List<Panel> ALL = new ArrayList<>();
    public static List<Panel> all() { return ALL; }
    public static Panel get(String id) { for (Panel p : ALL) if (p.id.equals(id)) return p; return null; }

    static {
        ALL.add(new TextPanel("fps", "FPS", 4, 4, false) {
            String text(MinecraftClient mc) { return "FPS: " + mc.getCurrentFps(); }
        });
        ALL.add(new TextPanel("cps", "CPS", 4, 22, false) {
            String text(MinecraftClient mc) { return "CPS: " + HudExtras.leftCps() + (Settings.b("cps", "right") ? " | " + HudExtras.rightCps() : ""); }
        });
        ALL.add(new TextPanel("ping", "Ping", 4, 40, false) {
            String text(MinecraftClient mc) {
                int ping = 0;
                if (mc.getNetworkHandler() != null && mc.player != null) {
                    PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
                    if (e != null) ping = e.getLatency();
                }
                return "Ping: " + ping + " ms";
            }
        });
        ALL.add(new Panel("coords", "Coordenadas", 4, 58, false) {
            private String l1(MinecraftClient mc) {
                if (mc.player == null) return "X: 0  Y: 0  Z: 0";
                return "X: " + (int) Math.floor(mc.player.getX()) + "  Y: " + (int) Math.floor(mc.player.getY()) + "  Z: " + (int) Math.floor(mc.player.getZ());
            }
            private String l2(MinecraftClient mc) {
                if (mc.player == null) return "Norte";
                switch (mc.player.getHorizontalFacing()) {
                    case SOUTH: return "Sur";
                    case EAST: return "Este";
                    case WEST: return "Oeste";
                    default: return "Norte";
                }
            }
            public int width(MinecraftClient mc) { return Math.max(mc.textRenderer.getWidth(l1(mc)), mc.textRenderer.getWidth(l2(mc))) + 10; }
            public int height(MinecraftClient mc) { return 26; }
            public void draw(DrawContext g, MinecraftClient mc, boolean editing) {
                box(g, id, width(mc), 26);
                txt(g, mc, id, l1(mc), 5, 4);
                txt(g, mc, id, l2(mc), 5, 14);
            }
        });
        ALL.add(new TextPanel("combo", "Combo", 4, 76, false) {
            String text(MinecraftClient mc) { return Extras.comboText(); }
        });
        ALL.add(new TextPanel("reach", "Alcance", 4, 94, false) {
            String text(MinecraftClient mc) { return Extras.reachText(); }
        });
        ALL.add(new TextPanel("direction", "Direcci\u00f3n", 4, 112, false) {
            String text(MinecraftClient mc) {
                if (mc.player == null) return "";
                float yaw = MathHelper.wrapDegrees(mc.player.getYaw());
                int i = (int) Math.floor((yaw + 180f + 22.5f) / 45f) & 7;
                return DIRS[(i + 4) & 7] + "  " + (int) (yaw + 180f) + "\u00b0";
            }
        });
        ALL.add(new TextPanel("clock", "Reloj", 4, 130, false) {
            String text(MinecraftClient mc) { return LocalTime.now().format(CLOCK); }
        });
        ALL.add(new TextPanel("memory", "Memoria", 0, 22, true) {
            String text(MinecraftClient mc) {
                Runtime r = Runtime.getRuntime();
                return "RAM: " + (r.totalMemory() - r.freeMemory()) / 1048576L + "/" + r.maxMemory() / 1048576L + " MB";
            }
        });
        ALL.add(new TextPanel("speed", "Velocidad", 0, 40, true) {
            String text(MinecraftClient mc) { return String.format(Locale.US, "%.1f b/s", HudExtras.speed()); }
        });
        ALL.add(new Panel("keystrokes", "Teclas", 4, 150, false) {
            public int width(MinecraftClient mc) { return 64; }
            public int height(MinecraftClient mc) { return Settings.b("keystrokes", "space") ? 78 : 64; }
            private String name(net.minecraft.client.option.KeyBinding k) {
                String s = k.getBoundKeyLocalizedText().getString();
                return s.length() > 3 ? s.substring(0, 3) : s;
            }
            public void draw(DrawContext g, MinecraftClient mc, boolean editing) {
                String id = this.id;
                boolean bg = Settings.b(id, "bg");
                boolean w = mc.options.forwardKey.isPressed(), a = mc.options.leftKey.isPressed(), s = mc.options.backKey.isPressed(), d = mc.options.rightKey.isPressed();
                boolean lmb = mc.options.attackKey.isPressed(), rmb = mc.options.useKey.isPressed();
                boolean showSpace = Settings.b(id, "space"), space = showSpace && mc.options.jumpKey.isPressed();
                boolean showCps = Settings.b(id, "cps");
                key(g, mc, bg, 22, 0, 20, 20, name(mc.options.forwardKey), w, null);
                key(g, mc, bg, 0, 22, 20, 20, name(mc.options.leftKey), a, null);
                key(g, mc, bg, 22, 22, 20, 20, name(mc.options.backKey), s, null);
                key(g, mc, bg, 44, 22, 20, 20, name(mc.options.rightKey), d, null);
                key(g, mc, bg, 0, 44, 31, 20, "LMB", lmb, showCps ? HudExtras.leftCps() + " CPS" : null);
                key(g, mc, bg, 33, 44, 31, 20, "RMB", rmb, showCps ? HudExtras.rightCps() + " CPS" : null);
                if (showSpace) {
                    g.fill(0, 66, 64, 78, space ? 0xC8A78BFA : (bg ? 0xA0090812 : 0x00000000));
                    g.fill(22, 71, 42, 72, space ? 0xFF0F0D1D : Settings.color(id, "color"));
                }
            }
            private void key(DrawContext g, MinecraftClient mc, boolean bg, int x, int y, int w, int h, String label, boolean pressed, String sub) {
                g.fill(x, y, x + w, y + h, pressed ? 0xC8A78BFA : (bg ? 0xA0090812 : 0x00000000));
                if (bg) {
                    g.fill(x, y, x + w, y + 1, pressed ? 0xFFFFFFFF : 0x30FFFFFF);
                    if (!pressed) g.fill(x, y + h - 1, x + w, y + h, 0x60A78BFA);
                }
                int tc = pressed ? 0xFF0F0D1D : Settings.color(id, "color");
                boolean sh = Settings.b(id, "shadow") && !pressed;
                int lw = mc.textRenderer.getWidth(label);
                if (sub == null) {
                    g.drawText(mc.textRenderer, label, x + (w - lw) / 2, y + (h - 8) / 2, tc, sh);
                } else {
                    g.drawText(mc.textRenderer, label, x + (w - lw) / 2, y + 3, tc, sh);
                    g.drawText(mc.textRenderer, sub, x + (w - mc.textRenderer.getWidth(sub)) / 2, y + 11, tc, sh);
                }
            }
        });
        ALL.add(new ArmorPanel());
        ALL.add(new PotionsPanel());
        ALL.add(new TeamPanel());
        ALL.add(new Panel("voice", "Voz", 4, 250, false) {
            private List<String> lines() {
                List<String> l = new ArrayList<>();
                String v = VoiceClient.connected()
                    ? (VoiceClient.muted() ? "Voz: silenciado" : VoiceClient.talking() ? "Voz: hablando" : "Voz: V para hablar")
                    : "Voz: " + VoiceClient.status();
                if (VoiceClient.deafened()) v += " (sin audio)";
                l.add(v);
                String s = VoiceClient.lastSpeaker();
                if (!s.isEmpty()) l.add("\u266A " + s);
                return l;
            }
            public int width(MinecraftClient mc) { int w = 20; for (String s : lines()) w = Math.max(w, mc.textRenderer.getWidth(s) + 12); return w; }
            public int height(MinecraftClient mc) { return lines().size() * 11 + 4; }
            public void draw(DrawContext g, MinecraftClient mc, boolean editing) {
                List<String> l = lines();
                box(g, id, width(mc), l.size() * 11 + 4);
                for (int i = 0; i < l.size(); i++) txt(g, mc, id, l.get(i), 7, 3 + i * 11);
            }
        });
        ALL.add(new WaypointListPanel());
    }

    // ------------------------------------------------------------------ armadura

    private static final class ArmorPanel extends Panel {
        private ItemStack[] demo;
        private final List<ItemStack> items = new ArrayList<>();
        private final String[] labels = new String[8];
        private final int[] colors = new int[8];
        private final boolean[] warn = new boolean[8];
        private int cW = 26, cH = 22, cell = 20;
        private boolean hor;
        private ItemStack arrowStack;

        ArmorPanel() { super("armor", "Armadura", 0, 120, true); }

        private static ItemStack damaged(ItemStack s, int dmg) { s.setDamage(dmg); return s; }

        private void ensureDemo() {
            if (demo != null) return;
            demo = new ItemStack[] {damaged(new ItemStack(Items.DIAMOND_SWORD), 120), new ItemStack(Items.DIAMOND_HELMET),
                damaged(new ItemStack(Items.DIAMOND_CHESTPLATE), 60), damaged(new ItemStack(Items.DIAMOND_LEGGINGS), 150), damaged(new ItemStack(Items.DIAMOND_BOOTS), 200)};
        }

        private void refresh(MinecraftClient mc, boolean editing) {
            items.clear();
            boolean held = Settings.b(id, "held"), fixed = Settings.b(id, "fixed"), arrows = Settings.b(id, "arrows");
            if (editing) ensureDemo();
            if (held) items.add(mc.player != null ? mc.player.getMainHandStack() : ItemStack.EMPTY);
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            for (EquipmentSlot s : slots) items.add(mc.player != null ? mc.player.getEquippedStack(s) : ItemStack.EMPTY);
            if (editing) {
                int off = held ? 0 : 1;
                for (int i = 0; i < items.size(); i++) if (items.get(i).isEmpty()) items.set(i, demo[Math.min(demo.length - 1, i + off)]);
            }
            if (!fixed) items.removeIf(ItemStack::isEmpty);
            if (arrows && mc.player != null && mc.player.getMainHandStack().getItem() instanceof BowItem) {
                int n = 0;
                for (int i = 0; i < mc.player.getInventory().size(); i++) { ItemStack st = mc.player.getInventory().getStack(i); if (st.isOf(Items.ARROW)) n += st.getCount(); }
                if (n > 0) items.add(new ItemStack(Items.ARROW, n));
            } else if (arrows && editing) {
                items.add(new ItemStack(Items.ARROW, 42));
            }
            int textMode = Settings.c(id, "text"), n = Math.min(items.size(), labels.length), maxW = 0;
            for (int i = 0; i < n; i++) {
                ItemStack s = items.get(i);
                labels[i] = null; warn[i] = false; colors[i] = Settings.color(id, "color");
                if (s.isEmpty()) continue;
                if (s.isDamageable()) {
                    int max = Math.max(1, s.getMaxDamage()), left = max - s.getDamage();
                    labels[i] = textMode == 0 ? String.valueOf(left) : textMode == 1 ? (left * 100 / max) + "%" : null;
                    float frac = Math.max(0f, (float) left / max);
                    colors[i] = java.awt.Color.HSBtoRGB(frac * 0.33f, 0.9f, 1f) | 0xFF000000;
                    warn[i] = Settings.b(id, "warn") && frac <= 0.10f;
                } else if (textMode != 2 && s.getCount() > 1) {
                    labels[i] = "x" + s.getCount();
                }
                if (labels[i] != null) maxW = Math.max(maxW, mc.textRenderer.getWidth(labels[i]));
            }
            hor = Settings.c(id, "layout") == 1;
            cell = Math.max(20, maxW + 4);
            int count = Math.max(1, items.size());
            if (hor) { cW = count * cell + 4; cH = 22 + (maxW > 0 ? 10 : 0); }
            else { cW = maxW > 0 ? Math.max(34, 25 + maxW + 6) : 26; cH = count * 18 + 4; }
        }

        public int width(MinecraftClient mc) { refresh(mc, mc.currentScreen instanceof HudEditorScreen); return cW; }
        public int height(MinecraftClient mc) { refresh(mc, mc.currentScreen instanceof HudEditorScreen); return cH; }

        public void draw(DrawContext g, MinecraftClient mc, boolean editing) {
            refresh(mc, editing);
            box(g, id, cW, cH);
            int count = Math.min(items.size(), labels.length);
            boolean bar = Settings.b(id, "bar"), flash = (System.currentTimeMillis() / 300L) % 2L == 0L, sh = Settings.b(id, "shadow");
            for (int i = 0; i < count; i++) {
                ItemStack s = items.get(i);
                if (s.isEmpty()) continue;
                int ix = hor ? 2 + i * cell + (cell - 16) / 2 : 5, iy = hor ? 3 : 2 + i * 18;
                g.drawItem(s, ix, iy);
                if (bar && s.isDamageable()) g.drawStackOverlay(mc.textRenderer, s, ix, iy, "");
            }
            for (int i = 0; i < count; i++) {
                String t = labels[i];
                if (t == null || items.get(i).isEmpty()) continue;
                int c = warn[i] && flash ? 0xFFFFFFFF : colors[i];
                if (hor) g.drawText(mc.textRenderer, t, 2 + i * cell + (cell - mc.textRenderer.getWidth(t)) / 2, 21, c, sh);
                else g.drawText(mc.textRenderer, t, 25, 2 + i * 18 + 4, c, sh);
            }
        }
    }

    // ------------------------------------------------------------------ pociones

    private static final class PotionsPanel extends Panel {
        private static final int ROW = 20;
        private final List<StatusEffectInstance> list = new ArrayList<>();
        private int cW = 60, cH = 4 + ROW;
        private StatusEffectInstance[] demo;

        PotionsPanel() { super("potions", "Pociones", 0, 4, true); }

        private static String level(int amp) { int l = amp + 1; return l <= 1 ? "" : " " + (l < ROMAN.length ? ROMAN[l] : String.valueOf(l)); }
        private static String name(StatusEffectInstance e) { return e.getEffectType().value().getName().getString() + level(e.getAmplifier()); }
        private static String time(StatusEffectInstance e) {
            if (e.isInfinite()) return "\u221E";
            int sec = e.getDuration() / 20;
            return (sec / 60) + ":" + String.format(Locale.ROOT, "%02d", sec % 60);
        }

        private void refresh(MinecraftClient mc, boolean editing) {
            list.clear();
            if (mc.player != null) list.addAll(mc.player.getStatusEffects());
            if (list.isEmpty() && editing) {
                if (demo == null) demo = new StatusEffectInstance[] {new StatusEffectInstance(StatusEffects.SPEED, 1830, 1), new StatusEffectInstance(StatusEffects.STRENGTH, 190, 0), new StatusEffectInstance(StatusEffects.REGENERATION, 620, 2)};
                for (StatusEffectInstance d : demo) list.add(d);
            }
            if (Settings.b(id, "sort") && list.size() > 1) list.sort(Comparator.comparingInt(StatusEffectInstance::getDuration));
            boolean compact = Settings.c(id, "style") == 1, icons = Settings.b(id, "icons");
            int w = 60;
            for (StatusEffectInstance e : list) {
                int tw = mc.textRenderer.getWidth(time(e));
                if (!compact) tw = Math.max(tw, mc.textRenderer.getWidth(name(e)));
                w = Math.max(w, (icons ? 25 : 7) + tw + 8);
            }
            cW = w; cH = 4 + Math.max(1, list.size()) * ROW;
        }

        public int width(MinecraftClient mc) { refresh(mc, mc.currentScreen instanceof HudEditorScreen); return cW; }
        public int height(MinecraftClient mc) { refresh(mc, mc.currentScreen instanceof HudEditorScreen); return cH; }

        public void draw(DrawContext g, MinecraftClient mc, boolean editing) {
            refresh(mc, editing);
            if (list.isEmpty()) return;
            box(g, id, cW, cH);
            boolean compact = Settings.c(id, "style") == 1, icons = Settings.b(id, "icons"), tint = Settings.b(id, "tint"), blink = Settings.b(id, "blink");
            boolean flash = (System.currentTimeMillis() / 300L) % 2L == 0L, sh = Settings.b(id, "shadow");
            int base = Settings.color(id, "color"), tx = icons ? 25 : 6;
            for (int i = 0; i < list.size(); i++) {
                StatusEffectInstance e = list.get(i);
                int y = 2 + i * ROW, left = e.getDuration();
                if (icons) {
                    try {
                        String full = e.getEffectType().getIdAsString();
                        String path = full.substring(full.indexOf(':') + 1);
                        g.drawGuiTexture(RenderPipelines.GUI_TEXTURED, Identifier.of("minecraft", "mob_effect/" + path), 4, y, 18, 18);
                    } catch (Throwable ignored) { }
                }
                int tc = base;
                if (tint) tc = left <= 200 ? 0xFFFF5555 : left <= 600 ? 0xFFFBBF24 : base;
                if (blink && left <= 200 && !e.isInfinite() && flash) tc = 0xFFFFFFFF;
                if (compact) {
                    g.drawText(mc.textRenderer, time(e), tx, y + 5, tc, sh);
                } else {
                    boolean bad = e.getEffectType().value().getCategory() == StatusEffectCategory.HARMFUL;
                    g.drawText(mc.textRenderer, name(e), tx, y + 1, bad ? 0xFFFF7777 : base, sh);
                    g.drawText(mc.textRenderer, time(e), tx, y + 10, tc, sh);
                }
            }
        }
    }

    // ------------------------------------------------------------------ equipo

    private static final class TeamPanel extends Panel {
        TeamPanel() { super("team", "Equipo", 4, 232, false); }

        private List<String> lines(MinecraftClient mc, boolean editing) {
            List<String> out = new ArrayList<>();
            if (mc.player == null) { if (editing) out.add("\u2191 Compa\u00f1ero 24m  20\u2764"); return out; }
            boolean hp = Settings.b(id, "hp"), dist = Settings.b(id, "dist");
            for (TeamView.E e : TeamView.list()) {
                double dx = e.x - mc.player.getX(), dz = e.z - mc.player.getZ();
                StringBuilder sb = new StringBuilder(arrow(mc, dx, dz)).append(' ').append(e.name);
                if (dist) sb.append(String.format(Locale.ROOT, " %.0fm", Math.sqrt(dx * dx + dz * dz + (e.y - mc.player.getY()) * (e.y - mc.player.getY()))));
                if (hp) sb.append(String.format(Locale.ROOT, "  %.0f\u2764", e.hp / 2f));
                out.add(sb.toString());
            }
            if (out.isEmpty() && Settings.b(id, "status")) {
                String err = TeamSync.error();
                out.add(!err.isEmpty() ? "Team View: sin conexion" : !TeamSync.active() ? "Team View: solo en servidores" : "Team View: nadie cerca");
            }
            if (out.isEmpty() && editing) out.add("\u2191 Compa\u00f1ero 24m  20\u2764");
            return out;
        }

        public int width(MinecraftClient mc) { int w = 60; for (String s : lines(mc, mc.currentScreen instanceof HudEditorScreen)) w = Math.max(w, mc.textRenderer.getWidth(s) + 12); return w; }
        public int height(MinecraftClient mc) { return Math.max(1, lines(mc, mc.currentScreen instanceof HudEditorScreen).size()) * 11 + 4; }

        public void draw(DrawContext g, MinecraftClient mc, boolean editing) {
            if (!Settings.b(id, "list") && !editing) return;
            List<String> l = lines(mc, editing);
            if (l.isEmpty()) return;
            box(g, id, width(mc), l.size() * 11 + 4);
            int c = Settings.color("team", "color");
            for (int i = 0; i < l.size(); i++) g.drawText(mc.textRenderer, l.get(i), 7, 3 + i * 11, TeamView.list().isEmpty() ? 0xFF9B97B8 : c, Settings.b(id, "shadow"));
        }
    }

    // ------------------------------------------------------------------ lista de waypoints (arriba al centro)

    private static final class WaypointListPanel extends Panel {
        WaypointListPanel() { super("wp", "Waypoints", 0, 4, false); }
        public boolean enabled() { return Modules.on("waypoints"); }

        private List<Waypoint> near(MinecraftClient mc) {
            List<Waypoint> list = new ArrayList<>();
            if (mc.player == null) return list;
            double px = mc.player.getX(), pz = mc.player.getZ();
            int dim = WaypointStore.currentDim();
            for (Waypoint w : WaypointStore.current()) if (w.visible && w.dim == dim) list.add(w);
            list.sort(Comparator.comparingDouble(w -> (w.x - px) * (w.x - px) + (w.z - pz) * (w.z - pz)));
            return list.subList(0, Math.min(4, list.size()));
        }
        public int width(MinecraftClient mc) { return 160; }
        public int height(MinecraftClient mc) { return Math.max(1, near(mc).size()) * 10; }
        public void draw(DrawContext g, MinecraftClient mc, boolean editing) {
            int cy = 0;
            double px = mc.player.getX(), pz = mc.player.getZ();
            for (Waypoint w : near(mc)) {
                double dx = w.x - px, dz = w.z - pz;
                String s = String.format(Locale.ROOT, "%s %s %.0fm", arrow(mc, dx, dz), w.name, Math.sqrt(dx * dx + dz * dz));
                g.drawText(mc.textRenderer, s, 80 - mc.textRenderer.getWidth(s) / 2, cy, Markers.argb(w), true);
                cy += 10;
            }
        }
    }
}
