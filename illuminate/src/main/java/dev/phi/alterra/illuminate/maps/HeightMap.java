package dev.phi.alterra.illuminate.maps;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL30C;

import java.nio.ShortBuffer;

/**
 * The top of everything around the camera, leaves included (128 by 128 columns, a block each): the scene pass (PostFx) asks it what stands between a point and the sun,
 * for soft sun shadows under trees and overhangs and for light shafts through gaps in the leaves. Rebuilt a few times a second, from the loaded chunks only.
 */
public final class HeightMap {
    private static final ShortBuffer UPLOAD = org.lwjgl.BufferUtils.createShortBuffer(128 * 128);   // SIZE squared; reused: a new direct buffer every rebuild is slow to free
    private HeightMap() {}
    static final int SIZE = 128;
    private static int tex; private static long builtAt = -100;
    public static volatile int originX, originZ; public static volatile boolean ready;
    private static final short[] data = new short[SIZE * SIZE];

    public static int texture() { return tex; }

    public static void update(ClientLevel level, double camX, double camZ) {
        long gt = level.getGameTime();
        if (ready && gt - builtAt < 8 && gt >= builtAt) return;
        builtAt = gt;
        int ox = (int) Math.floor(camX) - SIZE / 2, oz = (int) Math.floor(camZ) - SIZE / 2;
        for (int z = 0; z < SIZE; z++) for (int x = 0; x < SIZE; x++) {
            int wx = ox + x, wz = oz + z;
            int h = level.hasChunk(wx >> 4, wz >> 4) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz) : -64;
            data[x + z * SIZE] = (short) Math.max(0, Math.min(65535, h + 64));
        }
        boolean fresh = tex == 0;
        if (fresh) tex = GL11C.glGenTextures();
        GlUpload.bind(GL13C.GL_TEXTURE0, tex);
        GlUpload.reset();
        ShortBuffer buf = UPLOAD; buf.clear(); buf.put(data).flip();
        if (fresh) {
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL13C.GL_CLAMP_TO_EDGE);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL13C.GL_CLAMP_TO_EDGE);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_R16, SIZE, SIZE, 0, GL11C.GL_RED, GL11C.GL_UNSIGNED_SHORT, buf);
        } else GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, 0, SIZE, SIZE, GL11C.GL_RED, GL11C.GL_UNSIGNED_SHORT, buf);
        com.mojang.blaze3d.platform.GlStateManager._bindTexture(0);
        originX = ox; originZ = oz; ready = true;
    }
}
