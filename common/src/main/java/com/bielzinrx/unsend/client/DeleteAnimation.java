package com.bielzinrx.unsend.client;

import com.bielzinrx.unsend.Unsend;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class DeleteAnimation {
    public static final ResourceLocation TEX_TRASH =
        new ResourceLocation(Unsend.MOD_ID, "textures/gui/trash.png");
    public static final ResourceLocation TEX_TRASH_OPEN =
        new ResourceLocation(Unsend.MOD_ID, "textures/gui/trash_open.png");

    private static final List<Active> ACTIVE = new ArrayList<>();

    private static final float DURATION = 0.46f;
    private static final float LID_OPEN_END = 0.14f;
    private static final float LID_CLOSE_START = 0.34f;
    private static final float LID_CLOSE_DURATION = 0.10f;
    private static final float DUST_LIFE = 0.24f;

    private static final int COLOR_START = 0xD8DCE2;
    private static final int COLOR_END = 0xE0655F;

    private DeleteAnimation() {}

    public static void start(String text, float startX, float startY, float trashX, float trashY, Runnable onComplete) {
        Font font = Minecraft.getInstance().font;
        String safe = text == null ? "" : text.strip();
        int gt = safe.indexOf('>');
        if (gt >= 0 && gt < safe.length() - 1) {
            safe = safe.substring(gt + 1).strip();
        }
        if (safe.length() > 40) {
            safe = safe.substring(0, 40) + "…";
        }
        if (safe.isEmpty()) safe = "…";
        float dir = ((safe.hashCode() & 1) == 0) ? 1f : -1f;
        ACTIVE.add(new Active(safe, startX, startY, trashX, trashY, font.width(safe), dir, onComplete));
    }

    public static void render(GuiGraphics g, float delta) {
        float dt = Mth.clamp(delta, 0f, 0.1f);
        Iterator<Active> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Active a = it.next();
            a.age += dt;
            float t = Mth.clamp(a.age / DURATION, 0f, 1f);
            float moveEase = easeOutCubic(t);

            boolean lidOpen = a.age >= LID_OPEN_END * 0.5f && a.age < LID_CLOSE_START + LID_CLOSE_DURATION;
            float openPop = t < LID_OPEN_END
                ? easeOutBack(Mth.clamp(a.age / (LID_OPEN_END * 0.5f), 0f, 1f))
                : 1f;

            float squashT = Mth.clamp((a.age - LID_CLOSE_START) / LID_CLOSE_DURATION, 0f, 1f);
            float squash = Mth.sin(squashT * (float) Math.PI) * 0.16f;
            if (a.age >= LID_CLOSE_START + LID_CLOSE_DURATION) {
                squash = 0f;
            }

            if (!a.dustSpawned && a.age >= LID_CLOSE_START) {
                a.dustSpawned = true;
            }

            drawTrashBody(g, a.trashX, a.trashY, lidOpen, openPop, squash);

            if (t < 1f) {
                Font font = Minecraft.getInstance().font;
                float px = Mth.lerp(moveEase, a.sx, a.trashX + 2f);
                float py = Mth.lerp(moveEase, a.sy, a.trashY + 2f);
                float arcSpan = Math.min(28f, Math.abs(a.trashX - a.sx) * 0.35f + 10f);
                py -= Mth.sin(t * (float) Math.PI) * arcSpan;

                float scale = 1f - easeInCubic(t) * 0.7f;
                float rotation = a.dir * moveEase * 9f;
                float fade = 1f - easeInCubic(Mth.clamp((t - 0.55f) / 0.45f, 0f, 1f));
                int alpha = (int) (fade * 225f);
                int color = (alpha << 24) | (lerpColor(COLOR_START, COLOR_END, moveEase) & 0xFFFFFF);

                g.pose().pushPose();
                g.pose().translate(px, py, 0);
                g.pose().mulPose(Axis.ZP.rotationDegrees(rotation));
                g.pose().scale(scale, scale, 1f);
                g.drawString(font, a.text, -font.width(a.text) / 2, -4, color, false);
                g.pose().popPose();
            }

            if (a.dustSpawned) {
                drawDust(g, a);
            }

            if (a.age >= DURATION) {
                if (a.onComplete != null) a.onComplete.run();
                it.remove();
            }
        }
    }

    private static void drawDust(GuiGraphics g, Active a) {
        float dustAge = a.age - LID_CLOSE_START;
        if (dustAge < 0f || dustAge > DUST_LIFE) return;
        float t = dustAge / DUST_LIFE;
        float ease = easeOutCubic(t);
        int alpha = (int) ((1f - t) * 150f);
        if (alpha <= 0) return;

        float cx = a.trashX + 7f;
        float cy = a.trashY + 3f;
        float[] angles = {200f, 270f, 340f};
        for (float baseAngle : angles) {
            float rad = (float) Math.toRadians(baseAngle);
            float dist = 2f + ease * 5f;
            float mx = cx + Mth.cos(rad) * dist;
            float my = cy - Math.abs(Mth.sin(rad)) * dist * 0.7f - ease * 1.5f;
            int color = (alpha << 24) | 0x00C7CCD2;
            g.fill((int) mx, (int) my, (int) mx + 1, (int) my + 1, color);
        }
    }

    public static void drawTrashIcon(GuiGraphics g, int x, int y, boolean hovered, float hoverAnim) {
        float h = Mth.clamp(hoverAnim, 0f, 1f);
        if (hovered && h < 0.5f) h = 1f;

        g.fill(x - 1, y + 14, x + 15, y + 16, 0x40000000);

        float scale = 1f + h * 0.12f;
        g.pose().pushPose();
        g.pose().translate(x + 7f, y + 7f, 0);
        g.pose().scale(scale, scale, 1f);
        g.pose().translate(-(x + 7f), -(y + 7f), 0);
        blitTrash(g, x, y, false, 0.92f + h * 0.08f);
        g.pose().popPose();

        if (h > 0.01f) {
            int glowAlpha = (int) (h * 110f);
            g.renderOutline(x - 2, y - 2, 18, 18, (glowAlpha << 24) | 0x00FFFFFF);
        }
    }

    public static void blitTrash(GuiGraphics g, int x, int y, boolean open, float alpha) {
        ResourceLocation tex = open ? TEX_TRASH_OPEN : TEX_TRASH;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, Mth.clamp(alpha, 0f, 1f));
        g.blit(tex, x, y, 0, 0, 14, 14, 16, 16);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void drawTrashBody(GuiGraphics g, float x, float y, boolean lidOpen, float openPop, float squash) {
        float cx = x + 7f;
        float cy = y + 12f;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(1f + squash * 0.55f, 1f - squash, 1f);
        g.pose().translate(-cx, -cy, 0);

        ResourceLocation tex = lidOpen ? TEX_TRASH_OPEN : TEX_TRASH;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        float liftY = lidOpen ? (1f - openPop) * -2f : 0f;
        g.blit(tex, (int) x, (int) (y + liftY), 0, 0, 14, 14, 16, 16);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        g.pose().popPose();
    }

    public static boolean isBusy() {
        return !ACTIVE.isEmpty();
    }

    private static int lerpColor(int c1, int c2, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int r = Math.round(Mth.lerp(t, r1, r2));
        int gr = Math.round(Mth.lerp(t, g1, g2));
        int b = Math.round(Mth.lerp(t, b1, b2));
        return (r << 16) | (gr << 8) | b;
    }

    private static float easeOutCubic(float t) {
        float f = t - 1f;
        return f * f * f + 1f;
    }

    private static float easeInCubic(float t) {
        return t * t * t;
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float f = t - 1f;
        return 1f + c3 * f * f * f + c1 * f * f;
    }

    private static final class Active {
        final String text;
        final float sx, sy, trashX, trashY, dir;
        final int textWidth;
        final Runnable onComplete;
        float age;
        boolean dustSpawned;

        Active(String text, float sx, float sy, float trashX, float trashY, int textWidth, float dir, Runnable onComplete) {
            this.text = text;
            this.sx = sx;
            this.sy = sy;
            this.trashX = trashX;
            this.trashY = trashY;
            this.textWidth = textWidth;
            this.dir = dir;
            this.onComplete = onComplete;
        }
    }
}
