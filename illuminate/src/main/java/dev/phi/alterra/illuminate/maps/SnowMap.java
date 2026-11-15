package dev.phi.alterra.illuminate.maps;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL30C;

import java.nio.ShortBuffer;

/**
 * The surface of the snow around the camera (128 by 128 columns): for each, the height of its top in eighths of a block (snow layers and snow blocks counted, the bare ground where there is none).
 * The block shader lifts or lowers the top corner of every snow layer to the average of the four columns around it, so snow lies in smooth drifts and slopes instead of steps of an eighth.
 * Rebuilt every few ticks from the loaded chunks only. Stored as height in eighths plus 512 (0 means not loaded).
 */
public final class SnowMap {
    private SnowMap() {}
    public static final int SIZE = 128, UNIT = 10;
    private static int tex; private static long builtAt = -100;
    public static volatile int originX, originZ; public static volatile boolean ready, any;
    private static final short[] data = new short[SIZE * SIZE];
    private static ShortBuffer upload;

    public static int texture() { return tex; }

    public static void update(ClientLevel level, double camX, double camZ) {
        long gt = level.getGameTime();
        if (ready && gt - builtAt < 8 && gt >= builtAt) return;
        builtAt = gt;
        int ox = (int) Math.floor(camX) - SIZE / 2, oz = (int) Math.floor(camZ) - SIZE / 2;
        boolean snow = false;
        var p = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int z = 0; z < SIZE; z++) for (int x = 0; x < SIZE; x++) {
            int wx = ox + x, wz = oz + z, units = 0;
            if (level.hasChunk(wx >> 4, wz >> 4)) {
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz);
                var st = level.getBlockState(p.set(wx, top, wz));
                if (st.is(Blocks.SNOW)) { units = top * 8 + st.getValue(SnowLayerBlock.LAYERS); snow = true; }   // a single layer does not block motion: it sits in the first air
                else {
                    var below = level.getBlockState(p.set(wx, top - 1, wz));
                    if (below.is(Blocks.SNOW)) { units = (top - 1) * 8 + below.getValue(SnowLayerBlock.LAYERS); snow = true; }
                    else { units = top * 8; if (below.is(Blocks.SNOW_BLOCK)) snow = true; }
                }
                units += 512;
            }
            data[x + z * SIZE] = (short) Math.max(0, Math.min(65535, units));
        }
        any = snow;
        boolean fresh = tex == 0;
        if (fresh) tex = GL11C.glGenTextures();
        GlUpload.bind(GL13C.GL_TEXTURE0 + UNIT, tex);
        GlUpload.reset();
        if (upload == null) upload = org.lwjgl.BufferUtils.createShortBuffer(SIZE * SIZE);
        ShortBuffer buf = upload; buf.clear(); buf.put(data).flip();
        if (fresh) {
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL13C.GL_CLAMP_TO_EDGE);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL13C.GL_CLAMP_TO_EDGE);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_R16, SIZE, SIZE, 0, GL11C.GL_RED, GL11C.GL_UNSIGNED_SHORT, buf);
        } else GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, 0, SIZE, SIZE, GL11C.GL_RED, GL11C.GL_UNSIGNED_SHORT, buf);
        GlStateManager._activeTexture(GL13C.GL_TEXTURE0);
        originX = ox; originZ = oz; ready = true;
    }
}
