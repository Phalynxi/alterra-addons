package dev.phi.alterra.illuminate.maps;

import dev.phi.prismlib.api.PrismApi;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL30C;

import java.nio.FloatBuffer;

/**
 * The slope of the grid water around the camera (the ripples of rain, swimmers, boats and splashes, see Prism Lib's wave field) as a texture the water shader adds to its normals:
 * x and z slope per cell, 96 by 96 cells. It sits on a texture unit above the twelve the game keeps track of, so it is bound with plain GL (and the unit that was active is put back).
 */
public final class WaveMap {
    private WaveMap() {}
    public static final int SIZE = 96, UNIT = 12;
    private static int tex;
    public static volatile boolean live;
    /** camera x, z relative to the grid's corner (blocks), 1 over the grid's width, 1 when there is anything to show */
    public static final float[] INFO = new float[4];
    private static final FloatBuffer buf = BufferUtils.createFloatBuffer(SIZE * SIZE * 2);

    /** Every frame the water shader runs: refresh the texture if the grid is moving and bind it. */
    public static void upload(double camX, double camZ) {
        var f = PrismApi.waveField(); live = false;
        if (f == null || !f.active()) return;
        var g = f.grid(); if (g == null || g.n != SIZE) return;
        buf.clear();
        float k = 1f / (2f * g.cell);
        for (int j = 0; j < SIZE; j++) for (int i = 0; i < SIZE; i++) {
            if (i == 0 || j == 0 || i == SIZE - 1 || j == SIZE - 1) { buf.put(0f).put(0f); continue; }
            int c = i + j * SIZE;
            buf.put((g.eta[c + 1] - g.eta[c - 1]) * k).put((g.eta[c + SIZE] - g.eta[c - SIZE]) * k);
        }
        buf.rewind();
        int active = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + UNIT);
        boolean fresh = tex == 0; if (fresh) tex = GL11C.glGenTextures();
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, tex);
        GlUpload.reset();
        if (fresh) {
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL13C.GL_CLAMP_TO_EDGE);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL13C.GL_CLAMP_TO_EDGE);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_RG16F, SIZE, SIZE, 0, GL30C.GL_RG, GL11C.GL_FLOAT, buf);
        } else GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, 0, SIZE, SIZE, GL30C.GL_RG, GL11C.GL_FLOAT, buf);
        GL13C.glActiveTexture(active);
        INFO[0] = (float) (camX - g.ox); INFO[1] = (float) (camZ - g.oz); INFO[2] = 1f / (SIZE * g.cell); INFO[3] = 1f;
        live = true;
    }

    /** Put the texture on its unit (before the draw that uses it). */
    public static void bind() {
        if (tex == 0) return;
        int active = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + UNIT); GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, tex); GL13C.glActiveTexture(active);
    }
}
