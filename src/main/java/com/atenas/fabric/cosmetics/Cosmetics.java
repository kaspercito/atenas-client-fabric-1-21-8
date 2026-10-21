package com.atenas.fabric.cosmetics;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import com.atenas.fabric.shared.SharedDir;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Capas de Atenas (mismos ids que el cliente 1.8.9 y el mod 1.20.1, asi lo que equipas en uno se ve en los otros).
 * Eleccion guardada en <.minecraft>/atenas/cosmetics.json. Alas, sombreros y mascotas: pendientes (ver LEEME).
 */
public final class Cosmetics {
    private Cosmetics() {}

    public static final String NS = "atenasclientfabric";
    public static final int FRAME_MS = 90;
    /** Los cosmeticos de otros jugadores solo se dibujan a menos de 40 bloques (al cuadrado). */
    public static final double MAX_DIST_SQ = 40.0 * 40.0;
    public static volatile boolean animate = true;

    public static final class Item {
        public final String id, name;
        public final int color, frames, ver;
        public final boolean glow, custom;
        /** Privado y tu nick no esta en su lista: no sale en tu menu. */
        public boolean locked;
        public boolean membrane;
        public float openMul = 1f;
        Item(String id, String name, int color, boolean glow, int frames, boolean custom, int ver) {
            this.id = id; this.name = name; this.color = color; this.glow = glow; this.frames = Math.max(1, frames); this.custom = custom; this.ver = ver;
        }
    }

    /** Entrada de capa del catalogo del servicio. */
    public static final class Def {
        public final String type, id, name; public final int color, frames, ver; public final boolean glow, priv; public final Set<String> owners;
        public Def(String type, String id, String name, int color, boolean glow, int frames, int ver, boolean priv, Set<String> owners) {
            this.type = type; this.priv = priv; this.id = id; this.name = name; this.color = color; this.glow = glow; this.frames = frames; this.ver = ver; this.owners = owners;
        }
    }

    private static final List<Item> BUILTIN = new ArrayList<>();
    private static final List<Item> CAPES = new CopyOnWriteArrayList<>();
    /** Id elegido (null = ninguna). Puede ser un id del catalogo que aun no se bajo: se conserva hasta que llegue. */
    private static volatile String selected;
    private static volatile boolean loaded;

    private static void b(String id, String name, int color, boolean glow, int frames) { BUILTIN.add(new Item(id, name, color, glow, frames, false, 0)); }

    static {
        b("atenas", "Atenas (Exclusiva)", 0xFFF5C542, true, 12);
        b("atardecer", "Atardecer Lunar", 0xFFFF7A59, false, 1);
        b("aurora", "Aurora", 0xFF35E0A8, true, 12);
        b("sakura", "Sakura", 0xFFF8A9C8, false, 1);
        b("oceano", "Oc\u00e9ano al Atardecer", 0xFF1E7FD8, false, 1);
        b("bosque", "Bosque Brumoso", 0xFF4F8F72, false, 1);
        b("galaxia", "Galaxia", 0xFF7B4DFF, true, 12);
        b("lava", "Monta\u00f1a de Lava", 0xFFFF6A1A, true, 12);
        b("hielo", "Cristal de Hielo", 0xFF7FD6FF, false, 1);
        b("llamas", "Llamas", 0xFFFF7A1A, true, 12);
        b("matrix", "Matrix", 0xFF1BFF5A, true, 12);
        b("pulso", "Pulso Synthwave", 0xFFFF2DA6, true, 12);
        b("rey", "Corona Real", 0xFFF5C542, true, 12);
        b("rayo", "Tormenta El\u00e9ctrica", 0xFFFFE23D, true, 12);
        b("calavera", "Calavera Infernal", 0xFFE8503A, true, 12);
        b("espadas", "Escudo de Guerra", 0xFF2457C5, true, 12);
        b("luna", "Luna M\u00e1gica", 0xFFF4F1DE, true, 12);
        b("corazon", "Coraz\u00f3n Latiendo", 0xFFFF4F8B, true, 12);
        b("diamante", "Diamante Radiante", 0xFF5FD8FF, true, 12);
        b("brujula", "Br\u00fajula M\u00edstica", 0xFFD4AF37, true, 12);
        b("sol", "Sol Naciente", 0xFFFFB400, true, 12);
        b("trebol", "Tr\u00e9bol Encantado", 0xFF2ECC71, true, 12);
        b("arcoiris", "Arco\u00edris", 0xFF9B7BFF, true, 12);
        b("mareas", "Mareas", 0xFF2E6FC4, true, 12);
        b("eclipse", "Eclipse Solar", 0xFFFFB84D, true, 12);
        b("tormenta", "Tormenta", 0xFF5B7CFA, true, 12);
        b("nieve", "Ventisca", 0xFFBFE3FF, true, 12);
        b("luciernagas", "Luci\u00e9rnagas", 0xFFB8FF4A, true, 12);
        b("oroliquido", "Oro L\u00edquido", 0xFFFFC83D, true, 12);
        b("cristal", "Cristal Prisma", 0xFF8FE9FF, true, 12);
        b("portal", "Portal M\u00edstico", 0xFFB04DFF, true, 12);
        b("dragon", "Drag\u00f3n", 0xFFD6202A, true, 12);        CAPES.addAll(BUILTIN);
    }


    public static final List<Item> WINGS = new ArrayList<>();
    public static final List<Item> HATS = new ArrayList<>();
    public static final List<Item> PETS = new ArrayList<>();
    private static void pet(String id, String name, int color) { PETS.add(new Item(id, name, color, false, 1, false, 0)); }
    private static void w(String id, String name, int color, boolean glow, boolean mem) { Item i = new Item(id, name, color, false, 1, false, 0); i.membrane = mem; WINGS.add(i); /* el 1.8.9 no hace brillar ninguna ala */ }
    private static void h(String id, String name, int color, boolean glow) { HATS.add(new Item(id, name, color, glow, 1, false, 0)); }
    static {
        w("doradas", "Alas Doradas", 0xFFF5C542, false, false); w("oscuras", "Alas Oscuras", 0xFF8E2DE2, false, false);
        w("angel", "Alas de \u00c1ngel", 0xFFEAF6FF, true, false); w("atenas", "Alas Atenas", 0xFF8B5CF6, false, false);
        w("fuego", "Alas de Fuego", 0xFFFF6A00, true, false); w("hielo", "Alas de Hielo", 0xFF9ADCFF, false, false);
        w("neon", "Alas de Ne\u00f3n", 0xFF22D3EE, true, false); w("esmeralda", "Alas Esmeralda", 0xFF2ECC71, false, false);
        w("ambar", "Alas \u00c1mbar", 0xFFFF7814, true, false); w("sakura", "Alas Sakura", 0xFFFFAACD, false, false);
        w("aurora", "Alas Aurora", 0xFF28E6AA, true, false); w("lunar", "Alas Lunares", 0xFF7896E6, false, false);
        w("demonio", "Alas de Demonio", 0xFFC0182A, false, false);
        w("murcielago", "Alas de Murci\u00e9lago", 0xFF7860A8, false, true); w("vampiro", "Alas de Vampiro", 0xFFAA1E34, false, true);
        w("dragon", "Alas de Drag\u00f3n", 0xFFFF8C28, false, true); w("abismo", "Alas del Abismo", 0xFF14AABE, true, true);
        w("fantasma", "Alas Fantasma", 0xFFEBFAFF, false, true); w("jade", "Alas de Jade", 0xFF5AD796, false, true);
        w("solar", "Alas Solares", 0xFFFFE06E, true, true); w("ceniza", "Alas de Ceniza", 0xFF78767E, false, true);
        w("cristal", "Alas de Cristal", 0xFFC8F5FF, false, true); w("nebulosa", "Alas Nebulosa", 0xFF9646DC, false, true);
        w("mariposa", "Alas de Mariposa", 0xFFFF9A1E, false, true); w("hada", "Alas de Hada", 0xFFFFB6E6, true, true);
        w("fenix", "Alas de F\u00e9nix", 0xFFFF7A14, true, true); w("tormenta", "Alas de Tormenta", 0xFF5078E6, true, true);
        w("cibernetico", "Alas Cibern\u00e9ticas", 0xFF00DCFF, true, true); w("esqueleto", "Alas de Esqueleto", 0xFFDDD6C4, false, true);
        pet("perro", "Perro", 0xFFCD9654); pet("husky", "Husky", 0xFF68707E); pet("dalmata", "D\u00e1lmata", 0xFFF4F4F6); pet("gato", "Gato", 0xFFE28E3E);
        pet("gatonegro", "Gato negro", 0xFF24242E); pet("zorro", "Zorro", 0xFFE87028); pet("catalina", "Catalina", 0xFFF2F0EE); pet("conejo", "Conejo", 0xFFCEBCA8); pet("panda", "Panda", 0xFFF4F4F2);
        pet("loro", "Loro", 0xFFD82626); pet("buho", "B\u00faho", 0xFF8A6848); pet("dragoncito", "Dragoncito", 0xFF42A86C);
        PETS.stream().filter(i -> i.id.equals("catalina")).forEach(i -> i.locked = true);
        h("halo", "Halo", 0xFFFFE680, true); h("corona", "Corona", 0xFFF5C542, false); h("galera", "Galera", 0xFF3A3A4E, false);
        h("gato", "Orejas de Gato", 0xFF2A2A34, false); h("cuernos", "Cuernos", 0xFFC0182A, false); h("mago", "Sombrero de Mago", 0xFF5B2FA8, false);
        h("auriculares", "Auriculares", 0xFF28283A, false); h("conejo", "Orejas de Conejo", 0xFFF2F2F7, false); h("gorra", "Gorra", 0xFF2447B8, false);
        h("pirata", "Sombrero Pirata", 0xFF17171D, false); h("vikingo", "Casco Vikingo", 0xFFA9B0BF, false); h("laurel", "Corona de Laurel", 0xFFE8B84A, false);
    }

    private static final java.util.Map<String, String> SEL = new java.util.concurrent.ConcurrentHashMap<>();
    public static List<Item> listOf(String type) { return "wings".equals(type) ? WINGS : "hat".equals(type) ? HATS : "pet".equals(type) ? PETS : CAPES; }
    public static Item find(String type, String id) { if (id == null) return null; for (Item i : listOf(type)) if (i.id.equals(id)) return i; return null; }
    public static String selectedId(String type) { ensureLoaded(); if ("cape".equals(type)) return selected; String v = SEL.get(type); return v; }
    public static void select(String type, String id) {
        ensureLoaded();
        if ("cape".equals(type)) { select(id); return; }
        if (id == null) SEL.remove(type); else SEL.put(type, id);
        save(); CosmeticSync.markDirty();
    }
    static void applyRemote(String type, String id) {
        ensureLoaded();
        if ("cape".equals(type)) { applyRemote(id); return; }
        String v = "none".equals(id) || id == null || id.isEmpty() ? null : id;
        if (v == null) SEL.remove(type); else { Item f = find(type, v); if (f != null && !f.locked) SEL.put(type, v); }
        save();
    }

    public static List<Item> capes() { return CAPES; }

    public static Item find(String id) {
        if (id == null) return null;
        for (Item i : CAPES) if (i.id.equals(id)) return i;
        return null;
    }

    public static String selectedId() { ensureLoaded(); return selected; }

    /** Eleccion tuya (desde el menu): se guarda y se sube al servicio. */
    public static void select(String id) {
        ensureLoaded();
        Item it = find(id);
        if (it != null && it.locked) return;
        selected = id;
        save();
        CosmeticSync.markDirty();
    }

    /** Eleccion que llega del servicio (por ejemplo hecha desde 1.8.9): no se vuelve a subir. */
    static void applyRemote(String id) {
        ensureLoaded();
        String v = "none".equals(id) || id == null || id.isEmpty() ? null : id;
        if ((v == null && selected == null) || (v != null && v.equals(selected))) return;
        selected = v;
        save();
    }

    /** Catalogo nuevo: reglas de privacidad sobre los cosmeticos incorporados (capas, alas, sombreros, mascotas) y capas nuevas del servicio. */
    static void applyCatalog(List<Def> defs, String me) {
        String low = me == null ? "" : me.toLowerCase(Locale.ROOT);
        java.util.Map<String, Def> byKey = new java.util.HashMap<>();
        for (Def d : defs) byKey.put(d.type + ":" + d.id, d);
        // incorporados: privado => solo los de "owners"
        for (String t : new String[] {"cape", "wings", "hat", "pet"}) {
            for (Item i : listOf(t)) {
                if (i.custom) continue;
                Def d = byKey.get(t + ":" + i.id);
                i.locked = d != null && d.priv && !(d.owners != null && d.owners.contains(low));
            }
        }
        if (byKey.get("pet:catalina") == null) { Item c = find("pet", "catalina"); if (c != null) c.locked = true; } // sin regla del servicio sigue privada
        // capas nuevas
        List<Item> next = new ArrayList<>(BUILTIN);
        for (Def d : defs) {
            if (!"cape".equals(d.type)) continue;
            boolean dup = false;
            for (Item i : next) if (i.id.equals(d.id)) { dup = true; break; }
            if (dup) continue;
            Item it = new Item(d.id, d.name, d.color, d.glow, d.frames, true, d.ver);
            it.locked = d.priv && !(d.owners != null && d.owners.contains(low));
            next.add(it);
        }
        CAPES.clear();
        CAPES.addAll(next);
        // lo que llevas puesto y ya no puedes llevar se quita y se sincroniza
        boolean dropped = false;
        for (String t : new String[] {"wings", "hat", "pet"}) {
            Item c = find(t, SEL.get(t));
            if (c != null && c.locked) { SEL.remove(t); dropped = true; }
        }
        Item cc = find(selected);
        if (cc != null && cc.locked) { selected = null; dropped = true; }
        if (dropped) { save(); CosmeticSync.markDirty(); }
    }

    // ------------------------------------------------------------------ disco

    private static File file() { return new File(SharedDir.dir(), "cosmetics.json"); }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            File f = file();
            if (f.isFile()) {
                JsonObject o = JsonParser.parseString(Files.readString(f.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has("cape") && !o.get("cape").isJsonNull()) {
                    String s = o.get("cape").getAsString();
                    selected = s.isEmpty() || "none".equals(s) ? null : s;
                }
                for (String t : new String[] {"wings", "hat", "pet"}) if (o.has(t) && !o.get(t).isJsonNull() && !"none".equals(o.get(t).getAsString())) SEL.put(t, o.get(t).getAsString());
            }
        } catch (Exception ignored) { }
    }

    private static synchronized void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("cape", selected == null ? "none" : selected);
            for (String t : new String[] {"wings", "hat", "pet"}) o.addProperty(t, SEL.containsKey(t) ? SEL.get(t) : "none");
            Files.writeString(file().toPath(), o.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) { }
    }
}
