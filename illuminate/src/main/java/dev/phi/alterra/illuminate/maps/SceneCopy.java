package dev.phi.alterra.illuminate.maps;


import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL45C;
import org.slf4j.Logger;

import java.nio.ByteBuffer;

/**
 * A copy of the colour and depth of everything drawn before the water (terrain, plants, entities), taken just before the translucent layer. The water shader reads it for three things:
 * how deep the water is at each pixel (colour and clarity), the real bottom seen through it, and reflections found by marching a ray through it. Two straight GPU copies per frame, nothing is drawn.
 */
public final class SceneCopy {
    private SceneCopy() {}
    private static final Logger LOG = LogUtils.getLogger();
    public static final int DEPTH_UNIT = 4, COLOR_UNIT = 5;   // texture units (the shore map is on 6, the sway mask on 7)
    private static int color, depth, w, h;
    public static volatile boolean ready;
    /** Set after the block entities; the copy is then taken when the first chunk layer after them (the water) is about to be drawn, because the game only flushes some entities (translucent ones, mods' own) after that event and before the water. */
    public static volatile boolean pending;
    private static boolean supported = true;

    /** The camera's view-projection matrix and position when the copy was taken: metals (drawn before this frame's copy exists) reflect the previous frame's copy and need them to look things up in it. */
    public static final float[] vp = new float[16]; public static double camX, camY, camZ; public static volatile boolean haveVp;
    public static void remember(org.joml.Matrix4f mv, org.joml.Matrix4f proj, net.minecraft.world.phys.Vec3 cam) {
        new org.joml.Matrix4f(proj).mul(mv).get(vp); camX = cam.x; camY = cam.y; camZ = cam.z; haveVp = true;
    }
    public static int color() { return color; }
    public static int depth() { return depth; }
    public static int width() { return w; }
    public static int height() { return h; }

    /** Call on the render thread, at the point after the entities and before the translucent blocks. */
    public static void capture() {
        if (!supported) return;
        try {
            var caps = GL.getCapabilities();
            if (!caps.OpenGL45) { supported = false; ready = false; LOG.info("Alterra Weather: no depth reflections (this graphics driver lacks OpenGL 4.5)"); return; }
            var rt = Minecraft.getInstance().getMainRenderTarget();
            int srcColor = rt.getColorTextureId(), srcDepth = rt.getDepthTextureId(), sw = rt.width, sh = rt.height;
            // copy what is really being drawn into: mods like Veil (Sable) can draw the world into their own framebuffer, and then the main target holds
            // nothing yet (the copy came out black: black ground under the water, black reflections)
            int fb = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
            if (fb != 0 && fb != rt.frameBufferId) {
                int ct = GL30C.glGetFramebufferAttachmentParameteri(GL30C.GL_DRAW_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
                int dt = GL30C.glGetFramebufferAttachmentParameteri(GL30C.GL_DRAW_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT, GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
                if (ct == GL11C.GL_TEXTURE && dt == GL11C.GL_TEXTURE) {
                    srcColor = GL30C.glGetFramebufferAttachmentParameteri(GL30C.GL_DRAW_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
                    srcDepth = GL30C.glGetFramebufferAttachmentParameteri(GL30C.GL_DRAW_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT, GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
                    sw = GL45C.glGetTextureLevelParameteri(srcColor, 0, GL11C.GL_TEXTURE_WIDTH); sh = GL45C.glGetTextureLevelParameteri(srcColor, 0, GL11C.GL_TEXTURE_HEIGHT);
                }
            }
            int cf = GL45C.glGetTextureLevelParameteri(srcColor, 0, GL11C.GL_TEXTURE_INTERNAL_FORMAT);
            int df = GL45C.glGetTextureLevelParameteri(srcDepth, 0, GL11C.GL_TEXTURE_INTERNAL_FORMAT);
            if (logged++ < 3) LOG.info("Alterra Illuminate: scene copy from framebuffer {} (main {}), colour {} format {} depth {} format {}, {}x{} (main {}x{})", fb, rt.frameBufferId, srcColor, Integer.toHexString(cf), srcDepth, Integer.toHexString(df), sw, sh, rt.width, rt.height);
            if (sw != w || sh != h || color == 0 || cf != colorFormat || df != depthFormat) make(sw, sh, cf, df);   // the copies take the source's own formats (a copy needs them to match)
            GL43C.glCopyImageSubData(srcColor, GL11C.GL_TEXTURE_2D, 0, 0, 0, 0, color, GL11C.GL_TEXTURE_2D, 0, 0, 0, 0, w, h, 1);
            GL43C.glCopyImageSubData(srcDepth, GL11C.GL_TEXTURE_2D, 0, 0, 0, 0, depth, GL11C.GL_TEXTURE_2D, 0, 0, 0, 0, w, h, 1);
            ready = true;
        } catch (Throwable t) { supported = false; ready = false; LOG.warn("Alterra Weather: no depth reflections: {}", t.toString()); }
    }

    private static int colorFormat, depthFormat, logged;
    /** Allocate like the source was allocated (same reported format, a matching data type), so the driver picks the identical format and the copy is allowed.
     *  (Storage with Minecraft's plain GL_DEPTH_COMPONENT failed: no depth copy at all, so foam and stripes all over the water.) */
    private static void alloc(int f, int width, int height) {
        boolean depthStencil = f == GL30C.GL_DEPTH_STENCIL || f == GL30C.GL_DEPTH24_STENCIL8 || f == GL30C.GL_DEPTH32F_STENCIL8;
        boolean depthOnly = !depthStencil && (f == GL11C.GL_DEPTH_COMPONENT || f == GL14C.GL_DEPTH_COMPONENT16 || f == GL14C.GL_DEPTH_COMPONENT24 || f == GL14C.GL_DEPTH_COMPONENT32 || f == GL30C.GL_DEPTH_COMPONENT32F);
        int format = depthStencil ? GL30C.GL_DEPTH_STENCIL : depthOnly ? GL11C.GL_DEPTH_COMPONENT : GL11C.GL_RGBA;
        int type = f == GL30C.GL_DEPTH32F_STENCIL8 ? GL30C.GL_FLOAT_32_UNSIGNED_INT_24_8_REV : depthStencil ? GL30C.GL_UNSIGNED_INT_24_8 : depthOnly ? GL11C.GL_FLOAT : GL11C.GL_UNSIGNED_BYTE;
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, f, width, height, 0, format, type, (ByteBuffer) null);
    }
    private static void make(int width, int height, int cf, int df) {
        if (color != 0) { GL11C.glDeleteTextures(color); GL11C.glDeleteTextures(depth); }
        w = width; h = height; colorFormat = cf; depthFormat = df;
        color = GL11C.glGenTextures(); depth = GL11C.glGenTextures();
        GlUpload.bind(GL13C.GL_TEXTURE0 + COLOR_UNIT, color); setup();
        alloc(colorFormat, w, h);
        GlUpload.bind(GL13C.GL_TEXTURE0 + DEPTH_UNIT, depth); setup();
        alloc(depthFormat, w, h);
        com.mojang.blaze3d.platform.GlStateManager._activeTexture(GL13C.GL_TEXTURE0);
    }
    private static void setup() {
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL13C.GL_CLAMP_TO_EDGE);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL13C.GL_CLAMP_TO_EDGE);
    }
}
