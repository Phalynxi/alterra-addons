package dev.phi.alterra.illuminate.look;

import dev.phi.aethryon.api.AethryonApi;
import dev.phi.aethryon.api.Funnel;

import java.util.List;

/** The funnels near the camera as the shaders take them: up to four, eight numbers each (x, y, z, radius, height (signed: the spin of a whirlpool), strength, type, top radius). */
final class FunnelData {
    final float[] a = new float[32]; final int n;
    FunnelData() {
        List<Funnel> list = AethryonApi.funnels(); n = Math.min(4, list.size());
        for (int i = 0; i < n; i++) {
            Funnel f = list.get(i);
            a[i * 8] = (float) f.x(); a[i * 8 + 1] = (float) f.y(); a[i * 8 + 2] = (float) f.z(); a[i * 8 + 3] = f.radius();
            a[i * 8 + 4] = f.height(); a[i * 8 + 5] = f.strength(); a[i * 8 + 6] = f.type().ordinal(); a[i * 8 + 7] = f.topRadius();
        }
    }
}
