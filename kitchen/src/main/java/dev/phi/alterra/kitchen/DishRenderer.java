package dev.phi.alterra.kitchen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Draws a made-up dish as a flat picture (front and back, a hair apart) from the texture DishLook painted for it. */
public final class DishRenderer extends BlockEntityWithoutLevelRenderer {
    public DishRenderer() { super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels()); }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(DishLook.textureFor(stack)));
        PoseStack.Pose p = pose.last();
        float front = 0.5f + 1f / 32f, back = 0.5f - 1f / 32f;
        quad(vc, p, front, 1, overlay, light, false);
        quad(vc, p, back, -1, overlay, light, true);
    }

    private static final float[][] UV = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
    private static final float[][] FRONT = {{0, 0}, {1, 0}, {1, 1}, {0, 1}}, BACK = {{1, 0}, {0, 0}, {0, 1}, {1, 1}};   // each corner seen counter-clockwise from its own side

    private static void quad(VertexConsumer vc, PoseStack.Pose p, float z, float nz, int overlay, int light, boolean back) {
        float[][] pos = back ? BACK : FRONT;
        for (int i = 0; i < 4; i++) vc.addVertex(p, pos[i][0], pos[i][1], z).setColor(255, 255, 255, 255).setUv(UV[i][0], UV[i][1]).setOverlay(overlay).setLight(light).setNormal(p, 0, 0, nz);
    }
}
