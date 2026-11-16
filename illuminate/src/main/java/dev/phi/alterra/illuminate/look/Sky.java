package dev.phi.alterra.illuminate.look;

import dev.phi.prismlib.api.PrismApi;
import dev.phi.prismlib.api.StormCell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.Comparator;
import java.util.List;

/** What the sky above the camera is doing, with regional storm cells counted (what the world's providers say). */
final class Sky {
    private Sky() {}
    /** The rain over the camera: vanilla's level, or the downpour under a storm cell. */
    static float rain(ClientLevel level) {
        var c = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return PrismApi.rainAt(level, c.x, c.z);
    }
    /** The (up to) four nearest storm cells as the shader takes them: x and z relative to the camera, radius, level; drifted on by the partial tick. */
    static final float[] CELLS = new float[16];
    static int cells(ClientLevel level, float pt) {
        var c = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        List<StormCell> list = new java.util.ArrayList<>(PrismApi.storm().cells(level, c.x, c.z, 3000));
        list.sort(Comparator.comparingDouble(s -> Math.hypot(s.x() - c.x, s.z() - c.z) - s.radius()));
        java.util.Arrays.fill(CELLS, 0f); int n = Math.min(4, list.size());
        for (int i = 0; i < n; i++) {
            StormCell s = list.get(i);
            CELLS[i * 4] = (float) (s.x() + s.vx() * pt - c.x); CELLS[i * 4 + 1] = (float) (s.z() + s.vz() * pt - c.z); CELLS[i * 4 + 2] = s.radius(); CELLS[i * 4 + 3] = s.level();
        }
        return n;
    }
}
