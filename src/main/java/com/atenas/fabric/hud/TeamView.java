package com.atenas.fabric.hud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.atenas.fabric.net.TeamSync;
import com.atenas.fabric.shared.Config;
import com.atenas.fabric.shared.WaypointStore;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.scoreboard.AbstractTeam;

/**
 * Quien es companero (igual que TeamViewModule de 1.8.9): jugadores de tu equipo del servidor (si el equipo no es enorme),
 * los de tu lista manual y los que el servicio de Atenas devuelve (amigos y quienes te tienen en su lista), aunque esten lejos.
 */
public final class TeamView {
    private TeamView() {}

    public static final class E { public String name; public double x, y, z; public float hp = 20f, max = 20f; }

    private static final int MAX_SERVER_TEAM = 12;
    private static volatile List<E> list = Collections.emptyList();
    private static int tick;

    public static List<E> list() { return list; }
    public static void clear() { list = Collections.emptyList(); }

    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || !Config.get("team", true)) { list = Collections.emptyList(); return; }
        if (++tick % 4 != 0) return;
        Map<String, TeamSync.Mate> rem = TeamSync.mates();
        int dim = WaypointStore.currentDim();
        AbstractTeam mine = Settings.b("team", "useteams") ? mc.player.getScoreboardTeam() : null;
        boolean teamOk = mine != null && mine.getPlayerList().size() <= MAX_SERVER_TEAM;
        List<E> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AbstractClientPlayerEntity pl : mc.world.getPlayers()) {
            if (pl == mc.player) continue;
            String nm = pl.getName().getString(), key = nm.toLowerCase(Locale.ROOT);
            TeamSync.Mate r = rem.get(key);
            boolean ok = (teamOk && pl.isTeammate(mc.player)) || TeamStore.has(nm) || (r != null && r.dim == dim);
            if (!ok) continue;
            E e = new E();
            e.name = nm; e.x = pl.getX(); e.y = pl.getY(); e.z = pl.getZ(); e.hp = pl.getHealth(); e.max = pl.getMaxHealth();
            out.add(e); seen.add(key);
        }
        for (TeamSync.Mate m : rem.values()) {
            String key = m.name.toLowerCase(Locale.ROOT);
            if (seen.contains(key) || m.dim != dim) continue;
            E e = new E();
            e.name = m.name; e.x = m.x; e.y = m.y; e.z = m.z; e.hp = m.hp; e.max = m.max;
            out.add(e);
        }
        list = out;
    }
}
