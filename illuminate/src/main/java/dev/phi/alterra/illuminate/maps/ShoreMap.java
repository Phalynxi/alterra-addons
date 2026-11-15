package dev.phi.alterra.illuminate.maps;


import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;

import java.nio.ByteBuffer;

/**
 * Where the shore foam goes: a small texture with one texel per block column around the player, holding which of the 8 neighbours of that water cell are land (one bit each: west, east, north, south, NW, NE, SW, SE).
 * The water shader reads it by world position and tints its own water with it, so the foam is part of the water: a thin straight line along the shore.
 */
public final class ShoreMap {
    private static ByteBuffer upload;   // reused between rebuilds
    private ShoreMap() {}
    public static final int SIZE = 48;
    public static final int UNIT = 6;   // texture unit (the sway mask uses 7)
    private static int tex;
    public static volatile int originX, originZ;
    public static volatile boolean ready;

    public static int texture() { return tex; }

    /** cells: SIZE*SIZE bytes, row z of column x at x + z * SIZE; origin: the world block that is cell (0, 0). Call on the render thread. */
    public static void set(byte[] cells, int ox, int oz) {
        if (tex == 0) {
            tex = GL11C.glGenTextures();
            GlUpload.bind(GL13C.GL_TEXTURE0 + UNIT, tex);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL13C.GL_CLAMP_TO_EDGE);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL13C.GL_CLAMP_TO_EDGE);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 1);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL30C.GL_R8, SIZE, SIZE, 0, GL11C.GL_RED, GL11C.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        } else {
            GlUpload.bind(GL13C.GL_TEXTURE0 + UNIT, tex);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 1);
        }
        GlUpload.reset();
        if (upload == null) upload = org.lwjgl.BufferUtils.createByteBuffer(cells.length);
        ByteBuffer buf = upload; buf.clear(); buf.put(cells).flip();
        GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, 0, SIZE, SIZE, GL11C.GL_RED, GL11C.GL_UNSIGNED_BYTE, buf);
        com.mojang.blaze3d.platform.GlStateManager._activeTexture(GL13C.GL_TEXTURE0);
        originX = ox; originZ = oz; ready = true;
    }
}
