package com.atenas.fabric.cosmetics;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/**
 * Mascotas cosmeticas: port del PetRender del 1.8.9. Cada jugador con mascota (y a menos de {@link Cosmetics#MAX_DIST_SQ}) tiene un
 * {@link PetBrain} que se mueve UNA vez por tick ({@link #tick}); el dibujo interpola y se hace dentro de la capa del jugador
 * ({@link CosmeticLayer}), convirtiendo la posicion de mundo de la mascota al marco del modelo del jugador. Solo cliente.
 */
public final class PetRender {
    private PetRender() {}

    private static final class Pet {
        final PetBrain brain;
        Cosmetics.Item item;
        boolean seen;
        String kind = "";
        Pet(long seed) { brain = new PetBrain(seed); }
    }

    private static final PetBrain.Owner OWNER = new PetBrain.Owner();
    private static final Map<String, Pet> PETS = new HashMap<>();
    private static final Map<String, PetModel> MODELS = new HashMap<>();

    /** El suelo para la mascota: la cara de arriba del bloque solido mas alto que este a "fromY" o mas abajo (hasta 8 bloques). */
    private static final PetBrain.Ground GROUND = new PetBrain.Ground() {
        @Override public double ground(double x, double z, double fromY) {
            World w = MinecraftClient.getInstance().world;
            if (w == null) return Double.NaN;
            int bx = MathHelper.floor(x), bz = MathHelper.floor(z), top = MathHelper.floor(fromY);
            for (int y = top; y >= top - 8 && y >= w.getBottomY(); y--) {
                BlockPos pos = new BlockPos(bx, y, bz);
                BlockState st = w.getBlockState(pos);
                VoxelShape sh = st.getCollisionShape(w, pos);
                if (!sh.isEmpty()) return y + sh.getMax(Direction.Axis.Y);
            }
            return Double.NaN;
        }

        @Override public double water(double x, double z, double y) {
            World w = MinecraftClient.getInstance().world;
            if (w == null) return Double.NaN;
            int bx = MathHelper.floor(x), bz = MathHelper.floor(z), by = MathHelper.floor(y);
            for (int dy = 0; dy >= -1; dy--) {
                int yy = by + dy;
                if (!w.getFluidState(new BlockPos(bx, yy, bz)).isIn(FluidTags.WATER)) continue;
                for (int n = 0; n < 8; n++) {
                    if (w.getFluidState(new BlockPos(bx, yy + 1, bz)).isIn(FluidTags.WATER)) yy++; else break;
                }
                return yy + 0.89;
            }
            return Double.NaN;
        }
    };

    private static Identifier tex(String id) { return Identifier.of(Cosmetics.NS, "textures/cosmetics/pets/" + id + ".png"); }

    private static PetModel modelOf(PetSpec.Kind kd) { return MODELS.computeIfAbsent(kd.name, k -> new PetModel(kd)); }

    /** Un tick de cada cerebro. Se registra en ClientTickEvents.END_CLIENT_TICK (ver AtenasFabricClient). */
    public static void tick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) { PETS.clear(); return; }
        if (mc.isPaused()) return; // un solo jugador con el menu de pausa abierto: el mundo esta congelado, la mascota tambien
        for (Pet p : PETS.values()) p.seen = false;
        for (AbstractClientPlayerEntity ep : mc.world.getPlayers()) {
            String name = ep.getName().getString();
            if (name == null || name.isEmpty()) continue;
            String petId = CosmeticSync.partOf(ep, "pet");
            Cosmetics.Item item = petId == null ? null : Cosmetics.find("pet", petId);
            if (item == null) continue;
            if (ep != mc.player && ep.squaredDistanceTo(mc.player) > Cosmetics.MAX_DIST_SQ) continue;
            PetSpec.Kind kd = PetSpec.kindFor(item.id);
            if (kd == null) continue;
            Pet pet = PETS.get(name);
            if (pet == null) { pet = new Pet(name.hashCode()); PETS.put(name, pet); }
            if (!kd.name.equals(pet.kind)) { pet.kind = kd.name; pet.brain.configure(kd.flier, kd.avoidWater, kd.hop); }
            pet.seen = true;
            pet.item = item;
            OWNER.x = ep.getX(); OWNER.y = ep.getY(); OWNER.z = ep.getZ();
            OWNER.yaw = ep.getYaw(); OWNER.bodyYaw = ep.getBodyYaw(); OWNER.pitch = ep.getPitch();
            OWNER.sneaking = ep.isSneaking(); OWNER.sprinting = ep.isSprinting();
            String pose = ep.getPose().name();
            OWNER.fallFlying = "GLIDING".equals(pose) || "FALL_FLYING".equals(pose);
            OWNER.flying = ep.getAbilities().flying;
            OWNER.swimming = ep.isSwimming();
            OWNER.inWater = ep.isTouchingWater(); OWNER.riding = ep.hasVehicle(); OWNER.climbing = ep.isClimbing(); OWNER.onGround = ep.isOnGround();
            pet.brain.tick(OWNER, GROUND);
        }
        // quien ya no esta (se fue, se quito la mascota o se alejo) pierde su mascota; al volver aparece junto a el
        for (Iterator<Pet> it = PETS.values().iterator(); it.hasNext();) if (!it.next().seen) it.remove();
    }

    /**
     * Dibuja la mascota de un jugador DENTRO de su capa (ms esta en el marco del modelo del jugador: y hacia abajo, origen 1.501 sobre los pies).
     * Se deshace ese marco para volver al mundo y se coloca la mascota exactamente como en el 1.8.9
     * (trasladar a su posicion, girar 180 - yaw, escalar (-s,-s,s), bajar 1.501).
     * ex/ey/ez = posicion interpolada del jugador, bodyYawDeg = giro de su cuerpo en este cuadro.
     */
    public static void render(MatrixStack ms, VertexConsumerProvider queue, int light, String name, double ex, double ey, double ez, float bodyYawDeg, float pt) {
        Pet pet = PETS.get(name);
        if (pet == null || pet.item == null) return;
        PetSpec.Kind kd = PetSpec.kindFor(pet.item.id);
        if (kd == null) return;
        PetBrain b = pet.brain;
        PetModel model = modelOf(kd);
        float sc = PetSpec.renderScale(kd, b, pt);
        double dx = b.ix(pt) - ex, dy = b.iy(pt) - ey, dz = b.iz(pt) - ez;
        ms.push();
        ms.translate(0.0, 1.501, 0.0);
        ms.scale(-1f, -1f, 1f);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-(180f - bodyYawDeg)));   // aqui ya estamos en el mundo, en la posicion del jugador
        ms.translate(dx, dy, dz);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - b.iyaw(pt)));
        draw(ms, queue, light, pet.item.id, kd, model, b, sc, pt);
        ms.pop();
    }

    /** Parte comun: inclinacion en el aire, hombrera, escala y dibujo. La matriz ya esta en el marco "mundo" de la mascota (pies en el origen). */
    private static void draw(MatrixStack ms, VertexConsumerProvider queue, int light, String id, PetSpec.Kind kd, PetModel model, PetBrain b, float sc, float pt) {
        float pitch = b.ipitch(pt), roll = b.iroll(pt);
        if (Math.abs(pitch) > 0.001f || Math.abs(roll) > 0.001f) {   // inclinada en el aire
            float cy = (kd.legH + kd.bodyH / 2f) / 16f * sc;
            ms.translate(0f, cy, 0f);
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.toDegrees(roll)));
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) -Math.toDegrees(pitch)));
            ms.translate(0f, -cy, 0f);
        }
        float perch = b.iperch(pt);
        if (perch > 0.5f) {   // hombrera bajo las patas
            float padS = Math.min(sc * 1.6f, 0.9f) * Math.min(1f, (perch - 0.5f) * 2f);
            ms.push();
            ms.scale(-padS, -padS, padS);
            PetModel.emitPad(queue.getBuffer(RenderLayer.getEntityCutoutNoCull(Identifier.ofVanilla("textures/block/brown_wool.png"))), ms.peek(), light);
            ms.pop();
        }
        ms.scale(-sc, -sc, sc);
        ms.translate(0f, -1.501f, 0f); // en el modelo los pies estan en y = 24 (1.5 bloques)
        final Identifier t = tex(id);
        model.emit(b, pt, queue.getBuffer(RenderLayer.getEntityCutoutNoCull(t)), ms.peek(), light, 1f);
    }

    // ------------------------------------------------------------------ vista previa del menu

    private static PetBrain previewBrain;
    private static String previewKey = "";
    private static long previewLast;
    private static final PetBrain.Ground FLAT = new PetBrain.Ground() {
        @Override public double ground(double x, double z, double fromY) { return 0.0; }
    };

    /**
     * Mascota elegida en la vista previa del menu, a un lado del jugador (igual que renderPreview del 1.8.9): se queda quieta, mirando a la
     * camara, y hace sus gestos de reposo. ms esta en el marco del modelo del jugador (que gira): se deshace el giro para que la mascota no gire.
     */
    public static void renderPreview(MatrixStack ms, VertexConsumerProvider queue, int light, String id, float bodyYawDeg) {
        PetSpec.Kind kd = PetSpec.kindFor(id);
        if (kd == null) return;
        if (previewBrain == null || !previewKey.equals(id)) {
            previewBrain = new PetBrain(id.hashCode());
            previewBrain.configure(kd.flier, kd.avoidWater, kd.hop);
            previewBrain.teleport(0.0, 0.0, 0.0, 0f);
            previewKey = id;
            previewLast = System.currentTimeMillis();
        }
        long now = System.currentTimeMillis();
        int ticks = (int) Math.min(5L, (now - previewLast) / 50L);
        if (ticks > 0) previewLast += ticks * 50L;
        for (int i = 0; i < ticks; i++) previewBrain.tick(0.0, 0.0, 0.0, 0f, false, FLAT);
        float pt = Math.min(1f, (now - previewLast) / 50f);
        PetModel model = modelOf(kd);
        float sc = PetSpec.renderScale(kd, previewBrain, pt);
        ms.push();
        ms.translate(0.0, 1.501, 0.0);
        ms.scale(-1f, -1f, 1f);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-(180f - bodyYawDeg)));   // sin el giro del jugador
        // 1.8.9: a +0.95 en su marco de GUI (derecha de la pantalla); aqui "frente a la camara" es el giro 180, por eso el lado es -x
        ms.translate(-0.95, kd.flier ? 0.55 + Math.sin(now * 0.004) * 0.06 : 0.0, 0.0);
        draw(ms, queue, light, id, kd, model, previewBrain, sc, pt);
        ms.pop();
    }
}
