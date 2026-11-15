package dev.phi.alterra.illuminate.maps;


import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL21C;

/** Our textures are uploaded from plain arrays, and other code (the game, Sodium, Iris, Veil) leaves pixel-store settings and buffers bound that would make such an upload read from the wrong place: set everything the way an upload from an array needs before each one. */
public final class GlUpload {
    private GlUpload() {}
    /** Put a texture on a unit for real. Minecraft keeps a cache of what is bound where, but Sodium, Veil and others bind with plain GL behind its back, so a bind it thinks it has already done may not be there:
     *  unbinding first makes the second bind always happen (and leaves the cache right). Without this our mask texture was sometimes not the one on its unit, and the whole world was taken for water. */
    public static void bind(int glUnit, int tex) {
        com.mojang.blaze3d.platform.GlStateManager._activeTexture(glUnit);
        com.mojang.blaze3d.platform.GlStateManager._bindTexture(0);
        com.mojang.blaze3d.platform.GlStateManager._bindTexture(tex);
    }

    static void reset() {
        GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 1);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SWAP_BYTES, 0);
    }
}
