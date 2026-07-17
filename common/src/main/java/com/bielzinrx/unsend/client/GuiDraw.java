package com.bielzinrx.unsend.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Thin 1.19.2 draw helpers (PoseStack era — no GuiGraphics). */
public final class GuiDraw {
    private GuiDraw() {}

    public static void fill(PoseStack pose, int x1, int y1, int x2, int y2, int argb) {
        GuiComponent.fill(pose, x1, y1, x2, y2, argb);
    }

    public static void outline(PoseStack pose, int x, int y, int w, int h, int argb) {
        fill(pose, x, y, x + w, y + 1, argb);
        fill(pose, x, y + h - 1, x + w, y + h, argb);
        fill(pose, x, y, x + 1, y + h, argb);
        fill(pose, x + w - 1, y, x + w, y + h, argb);
    }

    public static void drawString(PoseStack pose, Font font, String text, int x, int y, int argb) {
        font.draw(pose, text, x, y, argb);
    }

    public static void blit(PoseStack pose, ResourceLocation tex, int x, int y,
                            float u, float v, int w, int h, int texW, int texH, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, Mth.clamp(alpha, 0f, 1f));
        RenderSystem.setShaderTexture(0, tex);
        GuiComponent.blit(pose, x, y, 0, u, v, w, h, texW, texH);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }
}
