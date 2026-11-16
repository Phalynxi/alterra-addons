package dev.phi.alterra.illuminate.maps;

import dev.phi.prismlib.world.Clouds;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL30C;

import java.nio.FloatBuffer;
import java.util.Arrays;

/**
 * The large-scale shape of the clouds, worked out once and kept as a texture instead of being worked out again for every step of every pixel's ray: a ring of 256 by 256 cells of 24 blocks
 * in the clouds' own (drifting) space, around the camera. Per cell: how much cloud (the layered noise the server also uses for where it rains), how tall its heads are, how ragged its
 * underside is, and how low and flat or tall and heaped this region's clouds are. When the camera moves, only the columns and rows that are new are filled in; a couple of rows are redone
 * every frame so the clouds' slow change over time shows. The scene shader reads it with one texture look-up and keeps its fine noise for what is near.
 */
public final class CloudGrid {
    private CloudGrid() {}
    public static final int N = 256, UNIT = 14; public static final double CELL = 24.0;
    private static int tex; private static boolean inited;
    private static final int[] colX = new int[N], rowZ = new int[N];
    private static final FloatBuffer ROW = BufferUtils.createFloatBuffer(N * 4), COL = BufferUtils.createFloatBuffer(N * 4);
    private static int cycle;

    /** Keep the grid right around the clouds-space position (qx, qz) of the camera, for the clouds' current change over time. */
    public static void update(double qx, double qz, float evo) {
        int cx0 = (int) Math.floor(qx / CELL) - N / 2, cz0 = (int) Math.floor(qz / CELL) - N / 2;
        int active = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + UNIT);
        if (tex == 0) {
            tex = GL11C.glGenTextures(); GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, tex); GlUpload.reset();
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL11C.GL_REPEAT);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL11C.GL_REPEAT);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_RGBA16F, N, N, 0, GL11C.GL_RGBA, GL11C.GL_FLOAT, (FloatBuffer) null);
            Arrays.fill(colX, Integer.MIN_VALUE); Arrays.fill(rowZ, Integer.MIN_VALUE); inited = false;
        } else GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, tex);
        GlUpload.reset();
        // which cell each column (x) and row (z) of the ring should hold now
        int[] wantX = new int[N], wantZ = new int[N];
        for (int i = 0; i < N; i++) { wantX[i] = cx0 + Math.floorMod(i - cx0, N); wantZ[i] = cz0 + Math.floorMod(i - cz0, N); }
        int rowBudget = inited ? 12 : N, redone = 0;
        for (int i = 0; i < N; i++) if (colX[i] != wantX[i]) {   // a new column: all its rows
            COL.clear();
            for (int j = 0; j < N; j++) put(COL, wantX[i], wantZ[j], evo);
            COL.flip(); GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, i, 0, 1, N, GL11C.GL_RGBA, GL11C.GL_FLOAT, COL); colX[i] = wantX[i];
        }
        for (int j = 0; j < N && redone < rowBudget; j++) if (rowZ[j] != wantZ[j]) {   // a new row (the columns were just made right, so only the rows are left to fix)
            fillRow(j, wantX, wantZ[j], evo); rowZ[j] = wantZ[j]; redone++;
        }
        for (int k = 0; k < 2 && inited; k++) { int j = (cycle++) & (N - 1); fillRow(j, wantX, wantZ[j], evo); rowZ[j] = wantZ[j]; }   // and a few rows again every frame: the clouds change slowly
        if (!inited) { for (int j = 0; j < N; j++) rowZ[j] = wantZ[j]; inited = true; }
        GL13C.glActiveTexture(active);
    }

    private static void fillRow(int j, int[] wantX, int cz, float evo) {
        ROW.clear();
        for (int i = 0; i < N; i++) put(ROW, wantX[i], cz, evo);
        ROW.flip(); GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, j, N, 1, GL11C.GL_RGBA, GL11C.GL_FLOAT, ROW);
    }

    /** One cell's four numbers: the same layered noise as the server's cloud cover (so rain falls where the clouds are thick), and three that give each cloud its own character. */
    private static void put(FloatBuffer b, int cx, int cz, float evo) {
        double qx = (cx + 0.5) * CELL, qz = (cz + 0.5) * CELL;
        b.put(Clouds.fbm((float) (qx * 0.0065), (float) (qz * 0.0065), evo))
         .put(Clouds.fbm((float) (qx * 0.012 + 5.0), (float) (qz * 0.012 + 5.0), evo))
         .put(Clouds.noise((float) (qx * 0.025), (float) (qz * 0.025), 9.1f))
         .put(Clouds.noise((float) (qx * 0.0011), (float) (qz * 0.0011), 3.7f));
    }

    public static void bind() {
        if (tex == 0) return;
        int active = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + UNIT); GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, tex); GL13C.glActiveTexture(active);
    }
}
