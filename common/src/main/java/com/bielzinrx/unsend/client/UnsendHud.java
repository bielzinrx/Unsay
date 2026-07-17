package com.bielzinrx.unsend.client;

import com.bielzinrx.unsend.Unsend;
import com.bielzinrx.unsend.client.ClientMessageIndex.ClientTrackedMessage;
import com.bielzinrx.unsend.mixin.ChatComponentAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class UnsendHud {
    private static final int ICON = 14;
    private static final int GAP = 4;
    private static final int PAD = 3;
    private static final float APPEAR_DURATION = 0.14f;
    private static final float HOVER_SPEED = 12f;

    private static final ResourceLocation TEX_REPLY =
        new ResourceLocation(Unsend.MOD_ID, "textures/gui/reply.png");
    private static final ResourceLocation TEX_EDIT =
        new ResourceLocation(Unsend.MOD_ID, "textures/gui/edit.png");

    private static final List<IconHit> HITS = new ArrayList<>();
    private static final List<MsgBand> BANDS = new ArrayList<>();

    private static long hoverMsgId = Long.MIN_VALUE;
    private static long lastHoverMsgId = Long.MIN_VALUE;
    private static float appearAge;
    private static float trashHoverAnim;
    private static long lastFrameNanos = -1L;

    private record IconHit(long messageId, int x, int y, Action action, ChatHudEditor.HudPin pin) {}
    private record MsgBand(long id, int l, int t, int r, int b, boolean own, int iconY,
                           ChatHudEditor.HudPin pin) {}
    private enum Action { REPLY, EDIT, DELETE }

    private UnsendHud() {}

    public static void clearSelection() {
    }

    public static void onChatScreenRender(ChatScreen screen, GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float dt = computeDeltaSeconds();
        DeleteAnimation.render(g, dt);
        HITS.clear();
        BANDS.clear();
        hoverMsgId = Long.MIN_VALUE;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String status = UnsendComposer.getStatusLabel();
        if (!status.isEmpty()) {
            int y = mc.getWindow().getGuiScaledHeight() - 28;
            g.fill(2, y - 2, 2 + mc.font.width(status) + 6, y + 10, 0x88000000);
            g.drawString(mc.font, status, 4, y, 0xFFE5E7EB, false);
        }

        ChatComponent chat = mc.gui.getChat();
        ChatComponentAccessor acc = (ChatComponentAccessor) chat;
        List<GuiMessage> all = acc.unsend$getAllMessages();
        List<GuiMessage.Line> trimmed = acc.unsend$getTrimmedMessages();
        if (all == null || all.isEmpty() || trimmed == null || trimmed.isEmpty()) {
            lastHoverMsgId = Long.MIN_VALUE;
            return;
        }

        boolean shift = isShiftDown(mc);
        UUID self = mc.player.getUUID();
        String selfName = mc.player.getGameProfile().getName();
        boolean op = mc.player.hasPermissions(2);

        double scale = chat.getScale();
        int lineHeight = Math.max(1, acc.unsend$getLineHeight());
        int scroll = acc.unsend$getChatScrollbarPos();
        int linesPerPage = chat.getLinesPerPage();
        int chatBottom = Mth.floor((mc.getWindow().getGuiScaledHeight() - 40) / scale);
        int chatW = Math.max(40, (int) Math.ceil(chat.getWidth() * scale));
        int msgL = 0;
        int msgR = 6 + chatW;

        Map<String, BandAcc> map = new LinkedHashMap<>();
        for (int m = 0; m + scroll < trimmed.size() && m < linesPerPage; m++) {
            GuiMessage.Line line = trimmed.get(m + scroll);
            double lineBottom = chatBottom - (double) m * lineHeight;
            int top = (int) Math.floor((lineBottom - lineHeight) * scale);
            int bot = (int) Math.ceil(lineBottom * scale);
            if (bot < 0 || top > mc.getWindow().getGuiScaledHeight()) continue;

            GuiMessage gui = findGui(all, line);
            if (gui == null) continue;

            String key = bandKey(gui);
            BandAcc accB = map.get(key);
            if (accB == null) {
                ClientTrackedMessage tr = ClientMessageIndex.findOrCreateForGuiMessage(gui, self, selfName);
                accB = new BandAcc(tr, gui, top, bot);
                map.put(key, accB);
            } else {
                accB.top = Math.min(accB.top, top);
                accB.bot = Math.max(accB.bot, bot);
            }
        }

        for (BandAcc b : map.values()) {
            if (b.tracked == null || b.tracked.deleting) continue;
            boolean own = b.tracked.isOwnedBy(self);
            if (!own) {
                String full = b.tracked.displayContent != null
                    ? ClientMessageIndex.stripFormatting(b.tracked.displayContent.getString())
                    : b.tracked.plainText;
                if (ClientMessageIndex.extractOwnPlain(full, selfName) != null) {
                    own = true;
                }
            }
            int iconY = Mth.clamp((b.top + b.bot) / 2 - ICON / 2, 2, mc.getWindow().getGuiScaledHeight() - ICON - 4);
            ChatHudEditor.HudPin pin = pinForBand(b);
            BANDS.add(new MsgBand(b.tracked.id, msgL, b.top, msgR, b.bot, own, iconY, pin));
        }

        for (BandAcc b : map.values()) {
            if (b.tracked == null || b.tracked.deleting) continue;
            boolean own = false;
            for (MsgBand mb : BANDS) {
                if (mb.id == b.tracked.id) {
                    own = mb.own;
                    break;
                }
            }
            boolean canDelete = !b.tracked.deleting && (own || op);
            boolean canEdit = !b.tracked.deleting && own;

            int top = b.top;
            int bot = b.bot;
            int iconY = Mth.clamp((top + bot) / 2 - ICON / 2, 2, mc.getWindow().getGuiScaledHeight() - ICON - 4);

            int iconRowRight = msgR + ICON * 3 + GAP * 2 + 16;
            boolean hover = mouseX >= msgL && mouseX <= iconRowRight
                && mouseY >= top && mouseY <= bot;
            if (hover) hoverMsgId = b.tracked.id;

            if (!hover) continue;

            if (hoverMsgId != lastHoverMsgId) {
                appearAge = 0f;
                trashHoverAnim = 0f;
                lastHoverMsgId = hoverMsgId;
            }
            appearAge = Math.min(APPEAR_DURATION, appearAge + dt);
            float appearT = easeOutBack(appearAge / APPEAR_DURATION);
            float appearScale = Mth.clamp(appearT, 0.45f, 1.12f);
            float appearAlpha = Mth.clamp(appearAge / (APPEAR_DURATION * 0.65f), 0f, 1f);

            if (shift) {
                drawShiftHoverHighlight(g, msgL, top, msgR, bot, appearAlpha);
            }

            int count = 1;
            if (shift && canEdit) count++;
            if (shift && canDelete) count++;
            int totalW = count * ICON + (count - 1) * GAP;
            int barX = Math.min(msgR + 6, mc.getWindow().getGuiScaledWidth() - totalW - 8);
            int barY = iconY;

            int barW = totalW + PAD * 2;
            int barH = ICON + PAD * 2;
            int drawX = barX - PAD;
            int drawY = barY - PAD;

            g.pose().pushPose();
            g.pose().translate(drawX + barW / 2f, drawY + barH / 2f, 0);
            g.pose().scale(appearScale, appearScale, 1f);
            g.pose().translate(-(drawX + barW / 2f), -(drawY + barH / 2f), 0);
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1f, 1f, 1f, appearAlpha);

            drawIconBar(g, drawX, drawY, barW, barH);

            ChatHudEditor.HudPin pin = pinForBand(b);
            int x = barX;
            int y = barY;
            placeIcon(g, mouseX, mouseY, b.tracked.id, x, y, Action.REPLY, TEX_REPLY, pin);
            x += ICON + GAP;

            if (shift && canEdit) {
                placeIcon(g, mouseX, mouseY, b.tracked.id, x, y, Action.EDIT, TEX_EDIT, pin);
                x += ICON + GAP;
            }
            if (shift && canDelete) {
                boolean hot = hit(mouseX, mouseY, x, y);
                trashHoverAnim = Mth.lerp(Mth.clamp(dt * HOVER_SPEED, 0f, 1f), trashHoverAnim, hot ? 1f : 0f);
                DeleteAnimation.drawTrashIcon(g, x, y, hot, trashHoverAnim);
                HITS.add(new IconHit(b.tracked.id, x, y, Action.DELETE, pin));
            }

            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            g.pose().popPose();
        }

        if (hoverMsgId == Long.MIN_VALUE) {
            lastHoverMsgId = Long.MIN_VALUE;
            appearAge = 0f;
            trashHoverAnim = 0f;
        }

        for (IconHit h : HITS) {
            if (hit(mouseX, mouseY, h.x, h.y)) {
                String key = switch (h.action) {
                    case REPLY -> "unsend.tooltip.reply";
                    case EDIT -> "unsend.tooltip.edit";
                    case DELETE -> "unsend.tooltip.delete";
                };
                g.renderTooltip(mc.font, Component.translatable(key), mouseX, mouseY);
                break;
            }
        }
    }

    public static boolean onChatScreenClick(ChatScreen screen, double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        for (IconHit h : HITS) {
            if (!hit(mouseX, mouseY, h.x, h.y)) continue;
            ClientTrackedMessage t = ClientMessageIndex.get(h.messageId);
            if (t == null || t.deleting) return true;
            return switch (h.action) {
                case REPLY -> { UnsendComposer.beginReply(t); yield true; }
                case EDIT -> {
                    if (t.deleting) yield true;
                    UnsendComposer.beginEdit(t, screen);
                    yield true;
                }
                case DELETE -> deleteOne(t, h.x, h.y, h.pin);
            };
        }
        return false;
    }

    public static boolean onKeyPressed(ChatScreen screen, int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (UnsendComposer.isEditing() || UnsendComposer.isReplying()) {
                UnsendComposer.cancelWithEsc(screen);
                return true;
            }
        }
        boolean shift = isShiftDown(Minecraft.getInstance());
        boolean editing = UnsendComposer.isEditing();
        if (keyCode == GLFW.GLFW_KEY_UP) {
            if (shift || editing) {
                return UnsendComposer.tryNavigateOwnHistory(screen, +1);
            }
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            if (editing || shift) {
                return UnsendComposer.tryNavigateOwnHistory(screen, -1);
            }
            return false;
        }
        if (shift && (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE)) {
            return tryQuickDelete(screen);
        }
        return false;
    }

    private static boolean tryQuickDelete(ChatScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return false;

        if (hoverMsgId != Long.MIN_VALUE) {
            ClientTrackedMessage hovered = ClientMessageIndex.get(hoverMsgId);
            if (hovered != null && !hovered.deleting && canDelete(hovered, mc)) {
                float ox = 40f;
                float oy = mc.getWindow().getGuiScaledHeight() - 80f;
                ChatHudEditor.HudPin pin = null;
                for (MsgBand b : BANDS) {
                    if (b.id == hoverMsgId) {
                        ox = (b.l + b.r) * 0.5f;
                        oy = (b.t + b.b) * 0.5f;
                        pin = b.pin;
                        break;
                    }
                }
                ClientDelete.deleteTracked(hovered, ox, oy, pin);
                return true;
            }
        }

        try {
            var input = ChatScreenAccess.getInput(screen);
            if (input == null) return false;
            String val = input.getValue();
            if (UnsendComposer.isEditing() || UnsendComposer.isReplying()) return false;
            if (val != null && !val.isEmpty()) return false;

            ClientTrackedMessage last = ClientMessageIndex.findLastOwned(mc.player.getUUID());
            if (last == null || last.deleting) return false;
            if (!canDelete(last, mc)) return false;
            ClientDelete.deleteTracked(last, Float.NaN, Float.NaN, ChatHudEditor.capturePin(last));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean canDelete(ClientTrackedMessage t, Minecraft mc) {
        if (t == null || t.deleting || mc.player == null) return false;
        if (t.isOwnedBy(mc.player.getUUID())) return true;
        return mc.player.hasPermissions(2);
    }

    public static boolean onHandleChatInput(String message) {
        return UnsendComposer.tryHandleSend(message);
    }

    public static void onDeleteBroadcast(long messageId) {
        UnsendComposer.onMessageDeleted(messageId);
        ClientDelete.applyRemoteDelete(messageId);
    }

    private static void drawShiftHoverHighlight(GuiGraphics g, int left, int top, int right, int bot, float alpha) {
        float a = Mth.clamp(alpha, 0f, 1f);
        if (a <= 0.01f) return;
        int h = Math.max(1, bot - top);
        int washA = Math.max(1, (int) (a * 22f));
        g.fill(left, top, right + 2, bot, (washA << 24) | 0x94A3B8);
        int edgeA = Math.max(1, (int) (a * 110f));
        g.fill(left, top, left + 2, bot, (edgeA << 24) | 0x60A5FA);
        int lineA = Math.max(1, (int) (a * 36f));
        int line = (lineA << 24) | 0xCBD5E1;
        g.fill(left + 2, top, right + 2, top + 1, line);
        if (h > 2) {
            g.fill(left + 2, bot - 1, right + 2, bot, line);
        }
    }

    private static void drawIconBar(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 1, y + h, x + w - 1, y + h + 2, 0x40000000);
        int fill = 0xE00B1220;
        g.fill(x + 1, y, x + w - 1, y + h, fill);
        g.fill(x, y + 1, x + w, y + h - 1, fill);
        g.renderOutline(x, y, w, h, 0x6694A3B8);
    }

    private static void placeIcon(GuiGraphics g, int mx, int my, long id, int x, int y,
                                  Action action, ResourceLocation tex, ChatHudEditor.HudPin pin) {
        boolean hot = hit(mx, my, x, y);
        if (hot) {
            g.fill(x - 1, y - 1, x + ICON + 1, y + ICON + 1, 0x553B82F6);
            g.renderOutline(x - 1, y - 1, ICON + 2, ICON + 2, 0xFF93C5FD);
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, hot ? 1f : 0.92f);
        g.blit(tex, x, y, 0, 0, ICON, ICON, 16, 16);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        HITS.add(new IconHit(id, x, y, action, pin));
    }

    private static boolean deleteOne(ClientTrackedMessage tracked, int iconX, int iconY,
                                     ChatHudEditor.HudPin pin) {
        if (tracked == null) return true;
        ClientDelete.deleteTracked(tracked, iconX, iconY, pin);
        return true;
    }

    private static ChatHudEditor.HudPin pinForBand(BandAcc b) {
        if (b == null || b.tracked == null) {
            return new ChatHudEditor.HudPin(-1, Integer.MIN_VALUE, null, null, "");
        }
        if (b.gui != null) {
            String full = ClientMessageIndex.stripFormatting(b.gui.content().getString()).trim();
            String plain = b.tracked.plainText != null
                ? ClientMessageIndex.stripEditedBadge(b.tracked.plainText)
                : "";
            int rank = 0;
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.gui != null && mc.player != null) {
                    var chat = mc.gui.getChat();
                    if (chat instanceof ChatComponentAccessor acc) {
                        List<GuiMessage> all = acc.unsend$getAllMessages();
                        String selfName = mc.player.getGameProfile().getName();
                        if (all != null) {
                            for (GuiMessage gui : all) {
                                if (gui == b.gui) break;
                                String gf = ClientMessageIndex.stripFormatting(gui.content().getString()).trim();
                                String own = ClientMessageIndex.extractOwnPlain(gf, selfName);
                                if (own != null && plain.equals(ClientMessageIndex.stripEditedBadge(own))) {
                                    rank++;
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            return new ChatHudEditor.HudPin(
                rank,
                b.gui.addedTime(),
                b.gui.signature(),
                full,
                plain
            );
        }
        return ChatHudEditor.capturePin(b.tracked);
    }

    private static GuiMessage findGui(List<GuiMessage> all, GuiMessage.Line line) {
        if (all == null || line == null) return null;
        int t = line.addedTime();
        List<GuiMessage> sameTick = new ArrayList<>();
        for (GuiMessage m : all) {
            if (m.addedTime() == t) sameTick.add(m);
        }
        if (sameTick.isEmpty()) return null;
        if (sameTick.size() == 1) return sameTick.get(0);

        String lineText = formattedToString(line.content()).trim();
        if (!lineText.isEmpty()) {
            for (GuiMessage m : sameTick) {
                String full = ClientMessageIndex.stripFormatting(m.content().getString());
                if (full.contains(lineText)) return m;
            }
        }
        for (GuiMessage m : sameTick) {
            if (m.signature() != null
                && ClientMessageIndex.findBySignature(m.signature()) != null) {
                return m;
            }
        }
        return sameTick.get(sameTick.size() - 1);
    }

    private static String formattedToString(net.minecraft.util.FormattedCharSequence seq) {
        if (seq == null) return "";
        StringBuilder sb = new StringBuilder();
        seq.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return ClientMessageIndex.stripFormatting(sb.toString());
    }

    /** One GuiMessage instance = one band (multi-line wrap still merges). */
    private static String bandKey(GuiMessage gui) {
        if (gui == null) return "null";
        return "g:" + System.identityHashCode(gui) + ":" + gui.addedTime();
    }

    private static boolean hit(double mx, double my, int x, int y) {
        return mx >= x - 1 && mx <= x + ICON + 1 && my >= y - 1 && my <= y + ICON + 1;
    }

    private static float computeDeltaSeconds() {
        long now = System.nanoTime();
        if (lastFrameNanos < 0) {
            lastFrameNanos = now;
            return 1f / 60f;
        }
        float dt = (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        return Mth.clamp(dt, 0f, 0.1f);
    }

    private static float easeOutBack(float t) {
        t = Mth.clamp(t, 0f, 1f);
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float f = t - 1f;
        return 1f + c3 * f * f * f + c1 * f * f;
    }

    private static boolean isShiftDown(Minecraft mc) {
        long w = mc.getWindow().getWindow();
        return InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_SHIFT)
            || InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private static final class BandAcc {
        final ClientTrackedMessage tracked;
        final GuiMessage gui;
        int top, bot;

        BandAcc(ClientTrackedMessage tracked, GuiMessage gui, int top, int bot) {
            this.tracked = tracked;
            this.gui = gui;
            this.top = top;
            this.bot = bot;
        }
    }
}
