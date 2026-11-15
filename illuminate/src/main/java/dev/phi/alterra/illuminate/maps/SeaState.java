package dev.phi.alterra.illuminate.maps;

import dev.phi.alterra.weather.water.SeaOpen;
import dev.phi.alterra.weather.water.SeaSurge;


import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;

import java.nio.ByteBuffer;

/**
 * How open the sea is, around the player: a coarse map (8-block cells, 512 blocks across) that is 0 on land and in sheltered shallows and rises towards 1 over deep open water, more so in ocean biomes than in rivers, swamps and bays.
 * The water shader reads it (with linear filtering, so it is smooth) to make the waves larger and longer far from the shore; boats read the same numbers. Built from the loaded chunks only.
 */
public final class SeaState {
    private static ByteBuffer upload;   // reused between rebuilds
    private SeaState() {}
    public static final int SIZE = 64, CELL = 8, UNIT = 3;
    private static int tex;
    private static final byte[] data = new byte[SIZE * SIZE], rg = new byte[SIZE * SIZE * 2];
    private static boolean surged;
    public static volatile int originX, originZ;
    public static volatile boolean ready;

    public static int texture() { return tex; }

    /** Rebuild around the player (render thread). */
    public static void update(ClientLevel level, BlockPos centre) {
        int ox = Math.floorDiv(centre.getX(), CELL) * CELL - SIZE * CELL / 2, oz = Math.floorDiv(centre.getZ(), CELL) * CELL - SIZE * CELL / 2;
        float[] v = new float[SIZE * SIZE]; boolean[] known = new boolean[SIZE * SIZE];
        var pos = new BlockPos.MutableBlockPos();
        for (int cz = 0; cz < SIZE; cz++) for (int cx = 0; cx < SIZE; cx++) {
            float o = SeaOpen.at(level, ox + cx * CELL + CELL / 2, oz + cz * CELL + CELL / 2, pos);
            if (o < 0f) continue;
            known[cx + cz * SIZE] = true; v[cx + cz * SIZE] = o;
        }
        float[] out = new float[SIZE * SIZE];   // a 3x3 average over the known cells, and the unknown ones take what is around them (or a middling value)
        for (int cz = 0; cz < SIZE; cz++) for (int cx = 0; cx < SIZE; cx++) {
            float sum = 0; int n = 0;
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                int x = cx + dx, z = cz + dz;
                if (x >= 0 && z >= 0 && x < SIZE && z < SIZE && known[x + z * SIZE]) { sum += v[x + z * SIZE]; n++; }
            }
            out[cx + cz * SIZE] = n == 0 ? 0.4f : sum / n;
        }
        for (int i = 0; i < data.length; i++) data[i] = (byte) Math.round(out[i] * 255f);
        originX = ox; originZ = oz;
        upload();
    }

    /** Puts the storm surge (how far the water grid has raised the sea, from the server) next to the openness, every tick while there is any. */
    public static void refreshSurge() {
        if (!ready || (!SeaSurge.active() && !surged)) return;
        upload();
    }

    private static void upload() {
        boolean fresh = tex == 0, any = false;
        for (int cz = 0; cz < SIZE; cz++) for (int cx = 0; cx < SIZE; cx++) {
            int i = cx + cz * SIZE; float h = SeaSurge.height(originX + cx * CELL + CELL / 2.0, originZ + cz * CELL + CELL / 2.0);
            rg[i * 2] = data[i]; rg[i * 2 + 1] = (byte) (128 + Math.max(-127, Math.min(127, Math.round(h * 100f)))); any |= h != 0f;
        }
        surged = any;
        if (fresh) tex = GL11C.glGenTextures();
        GlUpload.bind(GL13C.GL_TEXTURE0 + UNIT, tex);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 1);
        GlUpload.reset();
        if (upload == null) upload = org.lwjgl.BufferUtils.createByteBuffer(rg.length);
        ByteBuffer buf = upload; buf.clear(); buf.put(rg).flip();
        if (fresh) {
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL13C.GL_CLAMP_TO_EDGE);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL13C.GL_CLAMP_TO_EDGE);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL30C.GL_RG8, SIZE, SIZE, 0, org.lwjgl.opengl.GL30C.GL_RG, GL11C.GL_UNSIGNED_BYTE, buf);
        } else GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, 0, SIZE, SIZE, org.lwjgl.opengl.GL30C.GL_RG, GL11C.GL_UNSIGNED_BYTE, buf);
        com.mojang.blaze3d.platform.GlStateManager._activeTexture(GL13C.GL_TEXTURE0);
        ready = true;
    }

    /** The openness of the sea at a point (for boats), 0 to 1. */
    public static float at(double x, double z) {
        if (!ready) return 0f;
        double fx = (x - originX) / CELL - 0.5, fz = (z - originZ) / CELL - 0.5;
        int x0 = (int) Math.floor(fx), z0 = (int) Math.floor(fz); double tx = fx - x0, tz = fz - z0;
        return (float) (lerp(lerp(cell(x0, z0), cell(x0 + 1, z0), tx), lerp(cell(x0, z0 + 1), cell(x0 + 1, z0 + 1), tx), tz));
    }
    private static double cell(int x, int z) { x = Math.max(0, Math.min(SIZE - 1, x)); z = Math.max(0, Math.min(SIZE - 1, z)); return (data[x + z * SIZE] & 255) / 255.0; }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}
