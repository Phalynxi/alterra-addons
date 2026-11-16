package dev.phi.alterra.illuminate.maps;

import dev.phi.prismlib.api.PrismApi;
import dev.phi.prismlib.api.WaterSurface;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL30C;

import java.nio.FloatBuffer;

/**
 * The height of the grid-simulated water of a whirlpool (see {@link WaterSurface}) as a texture: the water shader lifts and lowers the water's own surface by it (so it is regular water, deformed,
 * with all of its look) and adds its slope to the normals. One whirlpool at a time (the nearest). Bound with plain GL on a unit above the twelve the game keeps track of.
 */
public final class WhirlMap {
    private WhirlMap() {}
    public static final int UNIT = 13;
    private static int tex, size;
    /** camera x, z relative to the grid's corner, 1 over the grid's width, and the grid's size in cells (0: nothing to show) */
    public static final float[] INFO = new float[4];
    private static FloatBuffer buf;

    public static void upload(double camX, double camZ) {
        INFO[3] = 0f;
        WaterSurface best = null; double bd = Double.MAX_VALUE;
        for (WaterSurface s : PrismApi.waterSurfaces()) {
            var g = s.grid(); double d = Math.hypot(g.ox + g.n * g.cell / 2.0 - camX, g.oz + g.n * g.cell / 2.0 - camZ);
            if (d < bd) { bd = d; best = s; }
        }
        if (best == null) return;
        var g = best.grid(); int n = g.n;
        if (buf == null || buf.capacity() < n * n) buf = BufferUtils.createFloatBuffer(n * n);
        buf.clear();
        for (int i = 0; i < n * n; i++) buf.put(g.eta[i]);
        buf.rewind();
        int active = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + UNIT);
        boolean fresh = tex == 0 || size != n;
        if (tex == 0) tex = GL11C.glGenTextures();
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, tex);
        GlUpload.reset();
        if (fresh) {
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL13C.GL_CLAMP_TO_EDGE);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL13C.GL_CLAMP_TO_EDGE);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_R16F, n, n, 0, GL11C.GL_RED, GL11C.GL_FLOAT, buf); size = n;
        } else GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, 0, n, n, GL11C.GL_RED, GL11C.GL_FLOAT, buf);
        GL13C.glActiveTexture(active);
        INFO[0] = (float) (camX - g.ox); INFO[1] = (float) (camZ - g.oz); INFO[2] = 1f / (n * g.cell); INFO[3] = n;
    }

    public static int texture() { return tex; }
    public static void bind() {
        if (tex == 0) return;
        int active = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + UNIT); GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, tex); GL13C.glActiveTexture(active);
    }
}
