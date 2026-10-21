package com.atenas.fabric.cosmetics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Modelo de una mascota: mismas piezas, mismo orden de rotaciones (Z, Y, X) y mismo UV de cajas que el ModelRenderer/ModelBox del 1.8.9,
 * construido desde pets/models.json (ver {@link PetSpec}). Una instancia por tipo de modelo.
 * Marco: x a la izquierda de la mascota, y hacia ABAJO, frente = -z, pies en y = 24 (1/16 de bloque).
 */
public final class PetModel {
    private static final class Node {
        final PetSpec.Piece piece;
        final Node parent;
        final int slot;
        final float[][][] quadPos = new float[6][4][];   // 6 caras x 4 vertices x (x,y,z) en unidades del modelo
        final float[][][] quadUv = new float[6][4][];    // 6 caras x 4 vertices x (u,v) ya normalizados
        float rx, ry, rz, px, py, pz;
        Node(PetSpec.Piece p, Node parent, int slot) { this.piece = p; this.parent = parent; this.slot = slot; }
    }

    private final PetSpec.Kind k;
    private final List<Node> nodes = new ArrayList<>();
    private final PetSpec.Pose pose = new PetSpec.Pose();

    public PetModel(PetSpec.Kind kind) {
        this.k = kind;
        Map<String, Node> byName = new HashMap<>();
        for (PetSpec.Piece p : k.pieces) {
            Node parent = p.p == null || p.p.isEmpty() ? null : byName.get(p.p);
            Node n = new Node(p, parent, PetSpec.slot(p.n));
            box(n, p.o[0], p.o[1], p.o[2], (int) p.s[0], (int) p.s[1], (int) p.s[2], p.uv[0], p.uv[1]);
            byName.put(p.n, n);
            nodes.add(n);
        }
    }

    public PetSpec.Kind kind() { return k; }

    /** Misma construccion que ModelBox (1.8.9, sin espejo): vertices, caras y UV de la hoja logica texW x texH. */
    private static void box(Node n, float x, float y, float z, int w, int h, int d, int texU, int texV) {
        float x2 = x + w, y2 = y + h, z2 = z + d;
        float[] v7 = {x, y, z}, v0 = {x2, y, z}, v1 = {x2, y2, z}, v2 = {x, y2, z};
        float[] v3 = {x, y, z2}, v4 = {x2, y, z2}, v5 = {x2, y2, z2}, v6 = {x, y2, z2};
        face(n, 0, new float[][] {v4, v0, v1, v5}, texU + d + w, texV + d, texU + d + w + d, texV + d + h);
        face(n, 1, new float[][] {v7, v3, v6, v2}, texU, texV + d, texU + d, texV + d + h);
        face(n, 2, new float[][] {v4, v3, v7, v0}, texU + d, texV, texU + d + w, texV + d);
        face(n, 3, new float[][] {v1, v2, v6, v5}, texU + d + w, texV + d, texU + d + w + w, texV);
        face(n, 4, new float[][] {v0, v7, v2, v1}, texU + d, texV + d, texU + d + w, texV + d + h);
        face(n, 5, new float[][] {v3, v4, v5, v6}, texU + d + w + d, texV + d, texU + d + w + d + w, texV + d + h);
    }

    /** Igual que TexturedQuad: u2/v1 al primer vertice, u1/v1 al segundo, u1/v2 al tercero, u2/v2 al cuarto (con el mismo margen anti-sangrado). */
    private static void face(Node n, int i, float[][] v, int u1, int v1, int u2, int v2) {
        final float f = 0.0015625f, f1 = 0.003125f, tw = PetSpec.texW, th = PetSpec.texH;
        float[][] uv = {{u2 / tw - f, v1 / th + f1}, {u1 / tw + f, v1 / th + f1}, {u1 / tw + f, v2 / th - f1}, {u2 / tw - f, v2 / th - f1}};
        for (int j = 0; j < 4; j++) { n.quadPos[i][j] = v[j]; n.quadUv[i][j] = uv[j]; }
    }

    private static final Vector3f TP = new Vector3f(), TN = new Vector3f(), E1 = new Vector3f(), E2 = new Vector3f();

    /** Pose segun el cerebro y emite todos los vertices. La matriz de la pila debe estar en el marco del modelo (pies en y = 24/16). */
    public void emit(PetBrain brain, float pt, VertexConsumer vc, MatrixStack.Entry e, int light, float alpha) {
        PetSpec.pose(k, brain, pt, pose);
        int a = Math.max(0, Math.min(255, (int) (alpha * 255f)));
        for (Node n : nodes) {
            float bx = n.piece.at[0], by = n.piece.at[1], bz = n.piece.at[2];
            if (n.slot >= 0) {
                n.rx = pose.rx[n.slot]; n.ry = pose.ry[n.slot]; n.rz = pose.rz[n.slot];
                bx += pose.dx[n.slot]; by += pose.dy[n.slot]; bz += pose.dz[n.slot];
            } else { n.rx = n.ry = n.rz = 0f; }
            n.px = bx; n.py = by; n.pz = bz;
        }
        Map<Node, Matrix4f> mats = new HashMap<>();
        for (Node n : nodes) {
            Matrix4f m = n.parent == null ? new Matrix4f() : new Matrix4f(mats.get(n.parent));
            m.translate(n.px / 16f, n.py / 16f, n.pz / 16f);
            if (n.rz != 0f) m.rotateZ(n.rz);   // mismo orden que ModelRenderer: Z, luego Y, luego X
            if (n.ry != 0f) m.rotateY(n.ry);
            if (n.rx != 0f) m.rotateX(n.rx);
            mats.put(n, m);
            for (int f = 0; f < 6; f++) {
                float[][] p = n.quadPos[f];
                // normal como TexturedQuad: (v1 - v0) x (v1 - v2)... en el marco ya transformado
                m.transformPosition(p[0][0] / 16f, p[0][1] / 16f, p[0][2] / 16f, TP); E1.set(TP);
                m.transformPosition(p[1][0] / 16f, p[1][1] / 16f, p[1][2] / 16f, TP); E2.set(TP);
                Vector3f a0 = new Vector3f(E2).sub(E1);
                m.transformPosition(p[2][0] / 16f, p[2][1] / 16f, p[2][2] / 16f, TP);
                Vector3f a1 = new Vector3f(E2).sub(TP);
                a1.cross(a0, TN);
                if (TN.lengthSquared() > 1e-12f) TN.normalize(); else TN.set(0f, 1f, 0f);
                for (int j = 0; j < 4; j++) {
                    m.transformPosition(p[j][0] / 16f, p[j][1] / 16f, p[j][2] / 16f, TP);
                    vc.vertex(e, TP.x, TP.y, TP.z).color(255, 255, 255, a).texture(n.quadUv[f][j][0], n.quadUv[f][j][1])
                        .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, TN.x, TN.y, TN.z);
                }
            }
        }
    }

    /** Hombrera (almohadilla de lana 6x2x6 con hoja logica 32x16, como PadModel del 1.8.9); la escala la pone la matriz. */
    public static void emitPad(VertexConsumer vc, MatrixStack.Entry e, int light) {
        float x = -3f, y = 0f, z = -3f; int w = 6, h = 2, d = 6, u = 0, v = 0;
        float x2 = x + w, y2 = y + h, z2 = z + d;
        float[] v7 = {x, y, z}, v0 = {x2, y, z}, v1 = {x2, y2, z}, v2 = {x, y2, z};
        float[] v3 = {x, y, z2}, v4 = {x2, y, z2}, v5 = {x2, y2, z2}, v6 = {x, y2, z2};
        float[][][] f = {{v4, v0, v1, v5}, {v7, v3, v6, v2}, {v4, v3, v7, v0}, {v1, v2, v6, v5}, {v0, v7, v2, v1}, {v3, v4, v5, v6}};
        int[][] uv = {{u + d + w, v + d, u + d + w + d, v + d + h}, {u, v + d, u + d, v + d + h}, {u + d, v, u + d + w, v + d},
            {u + d + w, v + d, u + d + w + w, v}, {u + d, v + d, u + d + w, v + d + h}, {u + d + w + d, v + d, u + d + w + d + w, v + d + h}};
        final float fi = 0.0015625f, f1 = 0.003125f, tw = 32f, th = 16f;
        for (int i = 0; i < 6; i++) {
            int u1 = uv[i][0], v1_ = uv[i][1], u2 = uv[i][2], v2_ = uv[i][3];
            float[][] t = {{u2 / tw - fi, v1_ / th + f1}, {u1 / tw + fi, v1_ / th + f1}, {u1 / tw + fi, v2_ / th - f1}, {u2 / tw - fi, v2_ / th - f1}};
            Vector3f a0 = new Vector3f(f[i][1][0] - f[i][0][0], f[i][1][1] - f[i][0][1], f[i][1][2] - f[i][0][2]);
            Vector3f a1 = new Vector3f(f[i][1][0] - f[i][2][0], f[i][1][1] - f[i][2][1], f[i][1][2] - f[i][2][2]);
            Vector3f nn = new Vector3f();
            a1.cross(a0, nn);
            if (nn.lengthSquared() > 1e-12f) nn.normalize(); else nn.set(0f, 1f, 0f);
            for (int j = 0; j < 4; j++)
                vc.vertex(e, f[i][j][0] / 16f, f[i][j][1] / 16f, f[i][j][2] / 16f).color(255, 255, 255, 255).texture(t[j][0], t[j][1])
                    .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nn.x, nn.y, nn.z);
        }
    }
}
