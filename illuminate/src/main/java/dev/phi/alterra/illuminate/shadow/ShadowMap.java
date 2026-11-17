package dev.phi.alterra.illuminate.shadow;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Real sun (and moon) shadows, the way shader packs do them: the world is drawn from the light into a depth map, and the scene pass compares every pixel with it (soft edges from
 * filtering). The casters are our own light-weight meshes (the exposed faces of solid blocks and leaves around the camera, built from the client's blocks on a background thread and
 * rebuilt when they change): Sodium's chunk meshes cannot be drawn a second time from another point of view.
 * <p>Everything is kept relative to a whole-block origin near the camera, so the numbers stay small; the map is snapped to its own texels so shadows do not shimmer as you move.
 */
public final class ShadowMap {
    private static final Logger LOG = LoggerFactory.getLogger("Alterra Illuminate");
    public static final int SIZE = 2048, UNIT = 15;
    /** Half the width of the area the map covers, in blocks. */
    public static final float EXTENT = 72f;
    private static final int RANGE_CHUNKS = 4, DEPTH_BELOW = 3, DEPTH_ABOVE = 4;   // chunk radius round the camera; sections below and above the camera's

    private ShadowMap() {}

    private static final class Section { int sx, sy, sz; int vbo; int count; long hash; long checked; boolean building; }
    private record Job(long key, int x, int y, int z, long hash, float[] verts, int count) {}

    private static final Map<Long, Section> SECTIONS = new HashMap<>();
    private static final ConcurrentLinkedQueue<Job> DONE = new ConcurrentLinkedQueue<>();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "alterra-shadow-mesher"); t.setDaemon(true); return t; });
    private static int fbo, depthTex, program, vao, uVP, uOrigin;
    private static boolean ready, failed;
    private static final Matrix4f VP = new Matrix4f(), VIEW = new Matrix4f(), PROJ = new Matrix4f();
    private static final float[] M16 = new float[16];
    public static int originX, originY, originZ;
    private static long frame, version, drawnVersion = -1;
    private static final Matrix4f DRAWN = new Matrix4f();
    private static int cursor;

    public static boolean ready() { return ready; }
    public static int texture() { return depthTex; }
    public static float[] matrix() { VP.get(M16); return M16; }

    private static final String VERT = """
            #version 150
            in vec3 pos;
            uniform mat4 u_VP;
            uniform vec3 u_Origin;
            void main() { gl_Position = u_VP * vec4(pos + u_Origin, 1.0); }
            """;
    private static final String FRAG = """
            #version 150
            void main() {}
            """;

    private static int shader(int type, String src) {
        int s = GL20C.glCreateShader(type); GL20C.glShaderSource(s, src); GL20C.glCompileShader(s);
        if (GL20C.glGetShaderi(s, GL20C.GL_COMPILE_STATUS) == 0) throw new IllegalStateException(GL20C.glGetShaderInfoLog(s));
        return s;
    }

    private static void init() {
        int v = shader(GL20C.GL_VERTEX_SHADER, VERT), f = shader(GL20C.GL_FRAGMENT_SHADER, FRAG);
        program = GL20C.glCreateProgram(); GL20C.glAttachShader(program, v); GL20C.glAttachShader(program, f);
        GL20C.glBindAttribLocation(program, 0, "pos");
        GL20C.glLinkProgram(program);
        if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == 0) throw new IllegalStateException(GL20C.glGetProgramInfoLog(program));
        uVP = GL20C.glGetUniformLocation(program, "u_VP"); uOrigin = GL20C.glGetUniformLocation(program, "u_Origin");
        vao = GL30C.glGenVertexArrays();
        depthTex = GL11C.glGenTextures(); GlStateManager._bindTexture(depthTex);
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL14C.GL_DEPTH_COMPONENT24, SIZE, SIZE, 0, GL11C.GL_DEPTH_COMPONENT, GL11C.GL_FLOAT, (java.nio.ByteBuffer) null);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR); GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL13C.GL_CLAMP_TO_BORDER); GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL13C.GL_CLAMP_TO_BORDER);
        GL11C.glTexParameterfv(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_BORDER_COLOR, new float[] {1f, 1f, 1f, 1f});
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL14C.GL_TEXTURE_COMPARE_MODE, GL30C.GL_COMPARE_REF_TO_TEXTURE);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL14C.GL_TEXTURE_COMPARE_FUNC, GL11C.GL_LEQUAL);
        GlStateManager._bindTexture(0);
        fbo = GL30C.glGenFramebuffers();
        int prev = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
        GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT, GL11C.GL_TEXTURE_2D, depthTex, 0);
        GL11C.glDrawBuffer(GL11C.GL_NONE); GL11C.glReadBuffer(GL11C.GL_NONE);
        int status = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, prev);
        if (status != GL30C.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("shadow framebuffer incomplete: " + status);
    }

    // ------------------------------------------------------------------ the casters

    private static long key(int x, int y, int z) { return ((long) (x & 0x3FFFFF) << 42) | ((long) (z & 0x3FFFFF) << 20) | (y & 0xFFFFF); }   // a unique number per section (only used as the map key)

    private static boolean casts(BlockState st) { return st.canOcclude() || st.is(BlockTags.LEAVES); }

    /** Every tick: look at a few sections near the camera, and have the ones that are new or changed built again. */
    private static void tickSections(ClientLevel level, int camX, int camY, int camZ) {
        int ccx = camX >> 4, ccz = camZ >> 4, csy = camY >> 4;
        int minSec = level.getMinSection(), maxSec = level.getMaxSection() - 1;
        int y0 = Math.max(minSec, csy - DEPTH_BELOW), y1 = Math.min(maxSec, csy + DEPTH_ABOVE);
        // drop what has gone out of range
        var it = SECTIONS.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next(); Section s0 = e.getValue();
            int sx = s0.sx, sz = s0.sz, sy = s0.sy;
            if (Math.abs(sx - ccx) > RANGE_CHUNKS + 1 || Math.abs(sz - ccz) > RANGE_CHUNKS + 1 || sy < y0 - 1 || sy > y1 + 1) {
                if (e.getValue().vbo != 0) { GL15C.glDeleteBuffers(e.getValue().vbo); version++; }
                it.remove();
            }
        }
        int side = 2 * RANGE_CHUNKS + 1, rows = y1 - y0 + 1, total = side * side * rows, budget = 3, tries = 0;
        var pos = new BlockPos.MutableBlockPos();
        while (budget > 0 && tries++ < 60) {
            cursor = (cursor + 1) % total;
            int sy = y0 + cursor % rows, sz = ccz - RANGE_CHUNKS + (cursor / rows) % side, sx = ccx - RANGE_CHUNKS + (cursor / rows) / side;
            long k = key(sx, sy, sz);
            Section sec = SECTIONS.get(k);
            if (sec != null && (sec.building || frame - sec.checked < 120)) continue;   // looked at within the last few seconds
            if (!level.hasChunk(sx, sz)) continue;
            budget--;
            // the block data of the section and its one-block border
            byte[] occ = new byte[18 * 18 * 18]; long hash = 1125899906842597L; boolean any = false;
            int bx = sx << 4, by = sy << 4, bz = sz << 4;
            for (int y = 0; y < 18; y++) for (int z = 0; z < 18; z++) for (int x = 0; x < 18; x++) {
                int wx = bx + x - 1, wy = by + y - 1, wz = bz + z - 1;
                if (!level.hasChunk(wx >> 4, wz >> 4)) continue;
                byte o = casts(level.getBlockState(pos.set(wx, wy, wz))) ? (byte) 1 : 0;
                occ[x + 18 * (z + 18 * y)] = o; hash = hash * 31 + o;
                if (o != 0 && x > 0 && x < 17 && y > 0 && y < 17 && z > 0 && z < 17) any = true;
            }
            if (sec == null) { sec = new Section(); sec.sx = sx; sec.sy = sy; sec.sz = sz; SECTIONS.put(k, sec); sec.hash = Long.MIN_VALUE; }
            sec.checked = frame;
            if (hash == sec.hash) continue;
            if (!any) { if (sec.vbo != 0) { GL15C.glDeleteBuffers(sec.vbo); sec.vbo = 0; version++; } sec.count = 0; sec.hash = hash; continue; }
            sec.building = true;
            final long fh = hash; final int fx = sx, fy = sy, fz = sz;
            WORKER.execute(() -> { try { DONE.add(mesh(k, fx, fy, fz, fh, occ)); } catch (Throwable t) { LOG.warn("Alterra Illuminate: shadow mesher failed: {}", t.toString()); DONE.add(new Job(k, fx, fy, fz, fh, null, -1)); } });
        }
        // upload what the worker finished
        Job j; int uploads = 0;
        while (uploads < 6 && (j = DONE.poll()) != null) {
            Section sec = SECTIONS.get(j.key());
            if (sec == null) continue;   // gone out of range meanwhile
            sec.building = false;
            if (j.count() < 0) { sec.hash = Long.MIN_VALUE + 1; continue; }
            uploads++; sec.hash = j.hash();
            if (j.count() == 0) { if (sec.vbo != 0) { GL15C.glDeleteBuffers(sec.vbo); sec.vbo = 0; version++; } sec.count = 0; continue; }
            if (sec.vbo == 0) sec.vbo = GL15C.glGenBuffers();
            FloatBuffer fb = org.lwjgl.BufferUtils.createFloatBuffer(j.count() * 3); fb.put(j.verts(), 0, j.count() * 3).flip();
            int prev = GL11C.glGetInteger(GL15C.GL_ARRAY_BUFFER_BINDING);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, sec.vbo); GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, fb, GL15C.GL_STATIC_DRAW);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, prev);
            sec.count = j.count(); version++;
        }
    }

    /** The exposed faces of the occupied blocks of one section (two triangles each), in section-local coordinates. */
    private static Job mesh(long k, int sx, int sy, int sz, long hash, byte[] occ) {
        float[] out = new float[4096]; int n = 0;
        for (int y = 1; y <= 16; y++) for (int z = 1; z <= 16; z++) for (int x = 1; x <= 16; x++) {
            if (occ[x + 18 * (z + 18 * y)] == 0) continue;
            float px = x - 1, py = y - 1, pz = z - 1;
            for (int f = 0; f < 6; f++) {
                int dx = f == 0 ? -1 : f == 1 ? 1 : 0, dy = f == 2 ? -1 : f == 3 ? 1 : 0, dz = f == 4 ? -1 : f == 5 ? 1 : 0;
                if (occ[(x + dx) + 18 * ((z + dz) + 18 * (y + dy))] != 0) continue;
                if (n + 18 > out.length) out = java.util.Arrays.copyOf(out, out.length * 2);
                // the four corners of the face, then two triangles
                float x0 = px + (f == 1 ? 1 : 0), x1 = px + (f == 0 ? 0 : 1), y0 = py + (f == 3 ? 1 : 0), y1 = py + (f == 2 ? 0 : 1), z0 = pz + (f == 5 ? 1 : 0), z1 = pz + (f == 4 ? 0 : 1);
                float[] c;
                if (f < 2) c = new float[] {x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1};
                else if (f < 4) c = new float[] {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1};
                else c = new float[] {x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0};
                int[] order = {0, 1, 2, 0, 2, 3};
                for (int o : order) { out[n++] = c[o * 3]; out[n++] = c[o * 3 + 1]; out[n++] = c[o * 3 + 2]; }
            }
        }
        return new Job(k, sx, sy, sz, hash, out, n / 3);
    }

    // ------------------------------------------------------------------ the map

    /**
     * Once a frame, before the scene pass: keep the casters up to date and draw them into the map as seen from the light.
     * {@code light} is the direction towards the sun or moon. Returns whether the map is ready for the scene pass.
     */
    public static boolean render(ClientLevel level, double camX, double camY, double camZ, float lx, float ly, float lz) {
        if (failed) return false;
        try {
            if (program == 0) init();
            frame++;
            int ox = (int) Math.floor(camX), oy = (int) Math.floor(camY), oz = (int) Math.floor(camZ);
            tickSections(level, ox, oy, oz);
            originX = ox; originY = oy; originZ = oz;
            Vector3f L = new Vector3f(lx, ly, lz).normalize();
            // the light's view, centred on the camera and snapped to the map's texels (so shadows do not crawl as the camera moves)
            Vector3f centre = new Vector3f((float) (camX - ox), (float) (camY - oy), (float) (camZ - oz));
            Vector3f up = Math.abs(L.z) > 0.95f ? new Vector3f(1f, 0f, 0f) : new Vector3f(0f, 0f, 1f);
            Matrix4f view = new Matrix4f().lookAt(new Vector3f(centre).fma(-160f, L), centre, up);
            Vector3f c = view.transformPosition(new Vector3f(centre));
            float texel = 2f * EXTENT / SIZE;
            c.x = (float) (Math.floor(c.x / texel) * texel); c.y = (float) (Math.floor(c.y / texel) * texel);
            Matrix4f inv = new Matrix4f(view).invert(); Vector3f snapped = inv.transformPosition(c);
            Vector3f eye = new Vector3f(snapped).fma(-160f, L);
            VIEW.identity().lookAt(eye, snapped, up);
            PROJ.identity().ortho(-EXTENT, EXTENT, -EXTENT, EXTENT, 0.1f, 340f);
            VP.set(PROJ).mul(VIEW);
            if (ready && version == drawnVersion && VP.equals(DRAWN, 1e-6f)) return true;   // nothing moved and nothing changed: the map on the card is still right

            int prevFbo = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
            int[] vp = new int[4]; GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, vp);
            boolean depthTest = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST), cull = GL11C.glIsEnabled(GL11C.GL_CULL_FACE), blend = GL11C.glIsEnabled(GL11C.GL_BLEND), poly = GL11C.glIsEnabled(GL11C.GL_POLYGON_OFFSET_FILL);
            boolean depthMask = GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK);
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
            GL11C.glViewport(0, 0, SIZE, SIZE);
            GL11C.glDepthMask(true); GL11C.glEnable(GL11C.GL_DEPTH_TEST); GL11C.glDepthFunc(GL11C.GL_LEQUAL); GL11C.glDisable(GL11C.GL_BLEND);
            GL11C.glDisable(GL11C.GL_CULL_FACE);
            GL11C.glEnable(GL11C.GL_POLYGON_OFFSET_FILL); GL11C.glPolygonOffset(2.5f, 5f);
            GL11C.glClear(GL11C.GL_DEPTH_BUFFER_BIT);
            GL20C.glUseProgram(program);
            VP.get(M16); GL20C.glUniformMatrix4fv(uVP, false, M16);
            GL30C.glBindVertexArray(vao); GL20C.glEnableVertexAttribArray(0);
            int prevBuf = GL11C.glGetInteger(GL15C.GL_ARRAY_BUFFER_BINDING);
            for (var e : SECTIONS.entrySet()) {
                Section sec = e.getValue(); if (sec.count <= 0 || sec.vbo == 0) continue;
                GL20C.glUniform3f(uOrigin, sec.sx * 16f - ox, sec.sy * 16f - oy, sec.sz * 16f - oz);
                GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, sec.vbo);
                GL20C.glVertexAttribPointer(0, 3, GL11C.GL_FLOAT, false, 12, 0L);
                GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, sec.count);
            }
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, prevBuf);
            GL20C.glDisableVertexAttribArray(0); GL30C.glBindVertexArray(0); GL20C.glUseProgram(0);
            GL11C.glDisable(GL11C.GL_POLYGON_OFFSET_FILL); if (poly) GL11C.glEnable(GL11C.GL_POLYGON_OFFSET_FILL);
            if (!depthTest) GL11C.glDisable(GL11C.GL_DEPTH_TEST);
            if (cull) GL11C.glEnable(GL11C.GL_CULL_FACE);
            if (blend) GL11C.glEnable(GL11C.GL_BLEND);
            GL11C.glDepthMask(depthMask);
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, prevFbo);
            GL11C.glViewport(vp[0], vp[1], vp[2], vp[3]);
            ready = true; drawnVersion = version; DRAWN.set(VP);
            return true;
        } catch (Throwable t) {
            failed = true; ready = false;
            LOG.error("Alterra Illuminate: the shadow map failed, shadows fall back to the height map: {}", t.toString(), t);
            return false;
        }
    }

    public static void clear() {
        for (Section s : SECTIONS.values()) if (s.vbo != 0) GL15C.glDeleteBuffers(s.vbo);
        SECTIONS.clear(); DONE.clear(); ready = false;
    }
}
