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
    private static int heldQuickDeleteKey = GLFW.GLFW_KEY_UNKNOWN;
    private static boolean bulkDeleteKeyLatched;

    private static ChatScreen selectionInputScreen;
    private static boolean selectionInputLocked;

    // Resolved by record-component order so the same code works in named,
    // Fabric intermediary and Forge SRG runtimes without a fragile mapped name.
    private static volatile java.lang.reflect.Method lineEndAccessor;
    private static volatile boolean lineEndAccessorResolved;

    private record IconHit(long messageId, int x, int y, Action action, ChatHudEditor.HudPin pin) {}
    private record MsgBand(long id, int l, int t, int r, int b, boolean own, int iconY,
                           ChatHudEditor.HudPin pin) {}
    private enum Action { REPLY, EDIT, DELETE }

    private UnsendHud() {}

    public static void clearSelection() {
        HITS.clear();
        BANDS.clear();
        hoverMsgId = Long.MIN_VALUE;
        lastHoverMsgId = Long.MIN_VALUE;
        appearAge = 0f;
        trashHoverAnim = 0f;
        heldQuickDeleteKey = GLFW.GLFW_KEY_UNKNOWN;
        bulkDeleteKeyLatched = false;
        UnsendComposer.clear();
        ClientBulkDelete.onChatClosed();
        releaseSelectionInput();
    }

    public static void onChatScreenRender(ChatScreen screen, GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        syncSelectionInput(screen);
        float dt = computeDeltaSeconds();
        DeleteAnimation.render(g, dt);
        HITS.clear();
        BANDS.clear();
        hoverMsgId = Long.MIN_VALUE;

        Minecraft mc = Minecraft.getInstance();
        if (heldQuickDeleteKey != GLFW.GLFW_KEY_UNKNOWN
            && GLFW.glfwGetKey(mc.getWindow().getWindow(), heldQuickDeleteKey) == GLFW.GLFW_RELEASE) {
            heldQuickDeleteKey = GLFW.GLFW_KEY_UNKNOWN;
        }
        pollBulkDeleteKey(screen, mc);
        ClientBulkDelete.tick();
        if (mc.player == null) return;

        String status = UnsendComposer.getStatusLabel();
        Component bulkStatus = ClientBulkDelete.status();
        int statusY = mc.getWindow().getGuiScaledHeight() - 28;
        if (!status.isEmpty()) {
            drawStatus(g, mc, Component.literal(status), statusY);
            statusY -= 14;
        }
        if (bulkStatus != null && !bulkStatus.getString().isEmpty()) {
            drawStatus(g, mc, bulkStatus, statusY);
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

            GuiMessage gui = findGuiForTrimmedIndex(all, trimmed, m + scroll);
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

        int iconRowRight = msgR + ICON * 3 + GAP * 2 + 16;
        hoverMsgId = resolveHoverMessage(mouseX, mouseY, msgL, iconRowRight);

        for (BandAcc b : map.values()) {
            if (b.tracked == null || b.tracked.deleting) continue;
            boolean own = false;
            for (MsgBand mb : BANDS) {
                if (mb.id == b.tracked.id) {
                    own = mb.own;
                    break;
                }
            }
            boolean idle = !ClientActionState.isBusy() && !ClientBulkDelete.isConfirming();
            boolean canDelete = idle && !b.tracked.deleting && !b.tracked.pendingEdit && (own || op);
            boolean canEdit = idle && !b.tracked.deleting && !b.tracked.pendingEdit && own;

            int top = b.top;
            int bot = b.bot;
            if (ClientBulkDelete.isSelected(b.tracked.id)) {
                drawSelectedHighlight(g, msgL, top, msgR, bot);
            }
            int iconY = Mth.clamp((top + bot) / 2 - ICON / 2, 2, mc.getWindow().getGuiScaledHeight() - ICON - 4);

            // Resolve exactly one hovered message before rendering. Adjacent chat bands can
            // share/overlap a boundary after GUI scaling; rendering inside the loop made both
            // reply bars alternate on that pixel and restart the appear animation every frame.
            boolean hover = b.tracked.id == hoverMsgId;
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

    private static long resolveHoverMessage(int mouseX, int mouseY, int rowLeft, int rowRight) {
        if (mouseX < rowLeft || mouseX > rowRight || BANDS.isEmpty()) {
            return Long.MIN_VALUE;
        }

        long chosen = Long.MIN_VALUE;
        int bestDistance = Integer.MAX_VALUE;
        boolean chosenWasPrevious = false;
        for (MsgBand band : BANDS) {
            // Half-open vertical ranges ensure an exact shared boundary belongs to only one row.
            // If rounding creates a real overlap, choose the closest row centre and preserve the
            // previous row on an exact tie so the animation cannot oscillate frame-to-frame.
            if (mouseY < band.t || mouseY >= band.b) continue;
            int distance = Math.abs(mouseY * 2 - (band.t + band.b));
            boolean previous = band.id == lastHoverMsgId;
            if (distance < bestDistance || (distance == bestDistance && previous && !chosenWasPrevious)) {
                chosen = band.id;
                bestDistance = distance;
                chosenWasPrevious = previous;
            }
        }
        return chosen;
    }

    public static boolean onChatScreenClick(ChatScreen screen, double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!ClientBulkDelete.isRunning() && UnsayClientConfig.get().selectionModifierDown(mc)) {
            for (MsgBand band : BANDS) {
                if (mouseX < band.l || mouseX > band.r || mouseY < band.t || mouseY > band.b) continue;
                ClientTrackedMessage tracked = ClientMessageIndex.get(band.id);
                if (tracked != null && canDelete(tracked, mc) && tracked.id != 0) {
                    ClientBulkDelete.toggle(tracked, band.pin,
                        (band.l + band.r) * 0.5f, (band.t + band.b) * 0.5f);
                    syncSelectionInput(screen);
                }
                return true;
            }
        }

        for (IconHit h : HITS) {
            if (!hit(mouseX, mouseY, h.x, h.y)) continue;
            ClientTrackedMessage t = ClientMessageIndex.get(h.messageId);
            if (t == null || t.deleting) return true;
            return switch (h.action) {
                case REPLY -> { UnsendComposer.beginReply(t); yield true; }
                case EDIT -> {
                    if (t.deleting) yield true;
                    UnsendComposer.beginEdit(t, screen, h.pin);
                    yield true;
                }
                case DELETE -> deleteOne(t, h.x, h.y, h.pin);
            };
        }
        // While messages are selected the chat field is intentionally inactive. Clicking
        // outside a row must not focus it again or leak the click into vanilla ChatScreen.
        return ClientBulkDelete.isSelectionMode();
    }

    public static boolean onKeyPressed(ChatScreen screen, int keyCode) {
        Minecraft mc = Minecraft.getInstance();
        boolean shift = isShiftDown(mc);
        boolean ctrl = isCtrlDown(mc);
        boolean alt = isAltDown(mc);
        UnsayClientConfig config = UnsayClientConfig.get();

        // The physical Delete key is always authoritative while a bulk selection exists.
        // This avoids layout/config/modifier mismatches (especially right after Ctrl-click).
        boolean selectionDeleteKey = isDedicatedDeleteKey(keyCode)
            || matchesSelectionDelete(config, keyCode, shift, ctrl, alt);

        if (ClientBulkDelete.isConfirming()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                ClientBulkDelete.cancelConfirmation();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                ClientBulkDelete.confirm();
                return true;
            }
            if (selectionDeleteKey) {
                return activateBulkDeleteKey();
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (ClientBulkDelete.hasSelection()) {
                ClientBulkDelete.clearSelection();
                syncSelectionInput(screen);
                return true;
            }
            if (UnsendComposer.isEditing() || UnsendComposer.isReplying()) {
                UnsendComposer.cancelWithEsc(screen);
                return true;
            }
        }

        if (!ClientBulkDelete.isRunning() && config.matchesSelectAll(keyCode, shift, ctrl, alt)) {
            boolean selected = selectAllVisible(mc);
            syncSelectionInput(screen);
            return selected;
        }
        if (ClientBulkDelete.hasSelection() && selectionDeleteKey
            && (isDedicatedDeleteKey(keyCode) || isChatInputEmpty(screen))) {
            return activateBulkDeleteKey();
        }

        // Selection temporarily owns the keyboard. No letters, history keys or Enter are
        // forwarded to the hidden chat input until deletion or Esc finishes the selection.
        if (ClientBulkDelete.isSelectionMode()) return true;

        boolean editing = UnsendComposer.isEditing();
        if (keyCode == GLFW.GLFW_KEY_UP) {
            if (shift || editing) return UnsendComposer.tryNavigateOwnHistory(screen, +1);
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            if (editing || shift) return UnsendComposer.tryNavigateOwnHistory(screen, -1);
            return false;
        }

        boolean quickOldest = config.matchesOldest(keyCode, shift, ctrl, alt);
        boolean quickNewest = config.matchesNewest(keyCode, shift, ctrl, alt);
        if (quickOldest || quickNewest) {
            if (heldQuickDeleteKey == keyCode) return true;
            heldQuickDeleteKey = keyCode;
            return tryQuickDelete(screen, quickOldest);
        }
        return false;
    }

    /**
     * Raw GLFW edge fallback for Linux/window-manager combinations where ChatScreen#keyPressed
     * does not receive the dedicated Delete key. A latch prevents key-repeat from submitting
     * the same destructive action more than once before the key is physically released.
     */
    private static void pollBulkDeleteKey(ChatScreen screen, Minecraft mc) {
        if (mc == null || mc.getWindow() == null) return;
        long window = mc.getWindow().getWindow();
        boolean deleteDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_DELETE) == GLFW.GLFW_PRESS;
        boolean keypadDeleteDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_KP_DECIMAL) == GLFW.GLFW_PRESS;
        boolean backspaceDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_BACKSPACE) == GLFW.GLFW_PRESS;
        boolean down = deleteDown || keypadDeleteDown || backspaceDown;

        if (!down) {
            bulkDeleteKeyLatched = false;
            return;
        }
        if (bulkDeleteKeyLatched) return;
        if (!ClientBulkDelete.hasSelection() && !ClientBulkDelete.isConfirming()) return;

        // Dedicated Delete remains authoritative even if Ctrl is still held after Ctrl-click.
        activateBulkDeleteKey();
    }

    private static boolean activateBulkDeleteKey() {
        if (bulkDeleteKeyLatched) return true;

        boolean handled = false;
        if (ClientBulkDelete.isConfirming()) {
            ClientBulkDelete.confirm();
            handled = true;
        } else if (ClientBulkDelete.hasSelection()) {
            handled = ClientBulkDelete.requestSelectedDelete();
        }

        // Latch only after the action really started. A stale/failed request must not swallow
        // the next Delete press while leaving the selection untouched.
        if (handled) bulkDeleteKeyLatched = true;
        return handled;
    }

    private static boolean matchesSelectionDelete(UnsayClientConfig config, int keyCode,
                                                  boolean shift, boolean ctrl, boolean alt) {
        int normalizedKey = keyCode == GLFW.GLFW_KEY_KP_DECIMAL
            ? GLFW.GLFW_KEY_DELETE : keyCode;
        if (config.matchesDeleteSelection(normalizedKey, shift, ctrl, alt)) return true;

        // Ctrl/Shift/Alt may still be physically held after Ctrl-click selection. Ignore only
        // the configured selection modifier, while preserving every other configured modifier.
        String selectionModifier = config.selectionModifier == null
            ? "CTRL" : config.selectionModifier.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (selectionModifier) {
            case "SHIFT" -> shift && config.matchesDeleteSelection(normalizedKey, false, ctrl, alt);
            case "ALT" -> alt && config.matchesDeleteSelection(normalizedKey, shift, ctrl, false);
            case "NONE" -> false;
            default -> ctrl && config.matchesDeleteSelection(normalizedKey, shift, false, alt);
        };
    }

    private static boolean isDedicatedDeleteKey(int keyCode) {
        // Both common keyboard labels are accepted. Delete remains the advertised control,
        // while Backspace prevents layout/compact-keyboard differences from blocking removal.
        return keyCode == GLFW.GLFW_KEY_DELETE
            || keyCode == GLFW.GLFW_KEY_KP_DECIMAL
            || keyCode == GLFW.GLFW_KEY_BACKSPACE;
    }

    private static boolean tryQuickDelete(ChatScreen screen, boolean oldestFirst) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || ClientActionState.isBusy()) return false;

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

            List<ClientTrackedMessage> owned = ClientMessageIndex.listOwned(mc.player.getUUID());
            if (owned.isEmpty()) return false;
            ClientTrackedMessage last = oldestFirst ? owned.get(owned.size() - 1) : owned.get(0);
            if (last == null || last.deleting) return false;
            if (!canDelete(last, mc)) return false;
            ClientDelete.deleteTracked(last, Float.NaN, Float.NaN, ChatHudEditor.capturePin(last));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean canDelete(ClientTrackedMessage t, Minecraft mc) {
        if (t == null || t.deleting || t.pendingEdit || ClientActionState.isBusy() || mc.player == null) return false;
        if (t.isOwnedBy(mc.player.getUUID())) return true;
        return mc.player.hasPermissions(2);
    }

    public static boolean onHandleChatInput(String message) {
        if (ClientBulkDelete.isSelectionMode()) return true;
        return UnsendComposer.tryHandleSend(message);
    }

    public static void onDeleteBroadcast(long messageId) {
        onDeleteBroadcast(messageId, null, null);
    }

    public static void onDeleteBroadcast(long messageId, java.util.UUID sender, String plainText) {
        ClientDelete.applyRemoteDelete(messageId, sender, plainText);
    }

    private static boolean isChatInputEmpty(ChatScreen screen) {
        try {
            var input = ChatScreenAccess.getInput(screen);
            return input != null && (input.getValue() == null || input.getValue().isEmpty())
                && !UnsendComposer.isEditing() && !UnsendComposer.isReplying();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean selectAllVisible(Minecraft mc) {
        if (mc == null || mc.player == null || mc.gui == null || ClientActionState.isBusy()) {
            return false;
        }

        // Clear rows left tombstoned by an interrupted deletion before collecting history.
        // This also repairs ghost rows created by older 0.1.5b test builds in the same session.
        ChatHudRemover.purgeTombstonedRows();

        // Ctrl+Shift+A means every deletable message currently loaded in the chat history,
        // not only the rows visible before the player scrolls. Bind each exact GuiMessage so
        // identical same-tick lines remain independent and can never turn into ghost rows.
        ChatComponent chat = mc.gui.getChat();
        if (!(chat instanceof ChatComponentAccessor acc)) return false;
        List<GuiMessage> all = acc.unsend$getAllMessages();
        if (all == null || all.isEmpty()) return false;

        int before = ClientBulkDelete.selectedCount();
        UUID self = mc.player.getUUID();
        String selfName = mc.player.getGameProfile().getName();
        Map<String, Integer> sameTextRanks = new LinkedHashMap<>();

        for (GuiMessage gui : all) {
            if (ClientBulkDelete.selectedCount() >= UnsayClientConfig.get().bulkLimit()) break;
            ClientTrackedMessage tracked = ClientMessageIndex.findOrCreateForGuiMessage(gui, self, selfName);
            if (tracked == null || tracked.id == 0L || !canDelete(tracked, mc)) continue;

            String full = ClientMessageIndex.stripFormatting(gui.content().getString()).trim();
            String plain = tracked.plainText == null ? "" :
                ClientMessageIndex.stripEditedBadge(tracked.plainText).trim();
            if (plain.isEmpty()) continue;

            String rankKey = String.valueOf(tracked.sender) + "|" + plain;
            int rank = sameTextRanks.getOrDefault(rankKey, 0);
            sameTextRanks.put(rankKey, rank + 1);

            ChatHudEditor.HudPin pin = new ChatHudEditor.HudPin(
                rank, gui.addedTime(), gui.signature(), full, plain, gui);

            float x = Float.NaN;
            float y = Float.NaN;
            for (MsgBand band : BANDS) {
                if (band.id == tracked.id && band.pin != null && band.pin.guiRef == gui) {
                    x = (band.l + band.r) * 0.5f;
                    y = (band.t + band.b) * 0.5f;
                    break;
                }
            }
            ClientBulkDelete.select(tracked, pin, x, y);
        }
        return ClientBulkDelete.selectedCount() > before || ClientBulkDelete.hasSelection();
    }

    private static void syncSelectionInput(ChatScreen screen) {
        if (screen == null) return;
        Object input;
        try {
            input = ChatScreenAccess.getInput(screen);
        } catch (Throwable ignored) {
            return;
        }
        if (input == null) return;

        boolean shouldLock = ClientBulkDelete.isSelectionMode();
        if (shouldLock) {
            selectionInputScreen = screen;
            selectionInputLocked = true;
            setInputEnabled(input, false);
            return;
        }

        if (selectionInputLocked) {
            setInputEnabled(input, true);
            selectionInputLocked = false;
            selectionInputScreen = null;
        }
    }

    private static void releaseSelectionInput() {
        ChatScreen screen = selectionInputScreen;
        if (screen != null) {
            try {
                Object input = ChatScreenAccess.getInput(screen);
                if (input != null) setInputEnabled(input, true);
            } catch (Throwable ignored) {
            }
        }
        selectionInputLocked = false;
        selectionInputScreen = null;
    }

    /**
     * AbstractWidget exposes only the generic public booleans used for visibility and input.
     * Reflection keeps this helper mapping-agnostic across Fabric intermediary and Forge SRG,
     * while the actual chat text remains untouched and returns exactly as the player left it.
     */
    private static void setInputEnabled(Object input, boolean enabled) {
        try {
            for (java.lang.reflect.Field field : input.getClass().getFields()) {
                int modifiers = field.getModifiers();
                if (field.getType() != boolean.class
                    || java.lang.reflect.Modifier.isStatic(modifiers)) continue;
                field.setBoolean(input, enabled);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void drawStatus(GuiGraphics g, Minecraft mc, Component text, int y) {
        String plain = text == null ? "" : text.getString();
        if (plain.isEmpty()) return;
        int width = mc.font.width(text);
        g.fill(2, y - 2, 2 + width + 6, y + 10, 0xAA07111F);
        g.drawString(mc.font, text, 4, y, 0xFFE5E7EB, false);
    }

    private static void drawSelectedHighlight(GuiGraphics g, int left, int top, int right, int bot) {
        g.fill(left, top, right + 2, bot, 0x333B82F6);
        g.fill(left, top, left + 3, bot, 0xDD60A5FA);
        g.renderOutline(left, top, Math.max(1, right - left + 2), Math.max(1, bot - top), 0xAA93C5FD);
    }

    private static void drawBulkProgress(GuiGraphics g, Minecraft mc) {
        if (!ClientBulkDelete.isRunning()) return;
        int width = Math.min(180, mc.getWindow().getGuiScaledWidth() - 12);
        int x = 4;
        int y = mc.getWindow().getGuiScaledHeight() - 48;
        g.fill(x, y, x + width, y + 5, 0xAA111827);
        int fill = Math.round((width - 2) * ClientBulkDelete.progress());
        if (fill > 0) g.fill(x + 1, y + 1, x + 1 + fill, y + 4, 0xFF60A5FA);
        g.renderOutline(x, y, width, 5, 0xAA93C5FD);
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
                plain,
                b.gui
            );
        }
        return ChatHudEditor.capturePin(b.tracked);
    }

    /**
     * trimmedMessages is produced from allMessages in the same newest-to-oldest entry order.
     * Every entry starts with the line whose endOfEntry flag is true. Counting those boundaries
     * gives the exact parent GuiMessage even when many messages have identical text and tick.
     */
    private static GuiMessage findGuiForTrimmedIndex(List<GuiMessage> all,
                                                      List<GuiMessage.Line> trimmed,
                                                      int trimmedIndex) {
        if (all == null || trimmed == null || trimmedIndex < 0 || trimmedIndex >= trimmed.size()) {
            return null;
        }
        int entryIndex = -1;
        for (int i = 0; i <= trimmedIndex; i++) {
            if (i == 0 || isEndOfEntry(trimmed.get(i))) entryIndex++;
        }
        return entryIndex >= 0 && entryIndex < all.size() ? all.get(entryIndex) : null;
    }

    private static boolean isEndOfEntry(GuiMessage.Line line) {
        if (line == null) return true;
        try {
            java.lang.reflect.Method accessor = lineEndAccessor;
            if (!lineEndAccessorResolved) {
                synchronized (UnsendHud.class) {
                    if (!lineEndAccessorResolved) {
                        java.lang.reflect.RecordComponent[] components = line.getClass().getRecordComponents();
                        if (components != null && components.length > 0) {
                            // endOfEntry is the final component of GuiMessage.Line in both 1.19.2 and 1.20.1.
                            accessor = components[components.length - 1].getAccessor();
                            lineEndAccessor = accessor;
                        }
                        lineEndAccessorResolved = true;
                    } else {
                        accessor = lineEndAccessor;
                    }
                }
            }
            return accessor == null || Boolean.TRUE.equals(accessor.invoke(line));
        } catch (Throwable ignored) {
            // Safe fallback: treat the line as its own entry rather than ever merging two messages.
            return true;
        }
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
        if (mc == null || mc.getWindow() == null) return false;
        long w = mc.getWindow().getWindow();
        return InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_SHIFT)
            || InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private static boolean isCtrlDown(Minecraft mc) {
        if (mc == null || mc.getWindow() == null) return false;
        long w = mc.getWindow().getWindow();
        return InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_CONTROL)
            || InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private static boolean isAltDown(Minecraft mc) {
        if (mc == null || mc.getWindow() == null) return false;
        long w = mc.getWindow().getWindow();
        return InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_ALT)
            || InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_ALT);
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
