package com.bielzinrx.unsend.client;

import com.bielzinrx.unsend.client.ChatHudEditor.HudPin;
import com.bielzinrx.unsend.client.ClientMessageIndex.ClientTrackedMessage;
import com.bielzinrx.unsend.mixin.ChatComponentAccessor;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.MessageSignature;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID; // sender filter for remote HUD wipe

/** Removes exactly one HUD entry for a tracked message. */
public final class ChatHudRemover {
    private ChatHudRemover() {}

    public static void removeMessage(long messageId) {
        ClientTrackedMessage tracked = ClientMessageIndex.get(messageId);
        HudPin pin = tracked != null ? ChatHudEditor.capturePin(tracked) : null;
        removeTracked(tracked, messageId, pin);
    }

    /** Preferred path: pin captured from the exact HUD line the user clicked. */
    public static void removeTracked(ClientTrackedMessage tracked, HudPin pin) {
        long id = tracked != null ? tracked.id : Long.MIN_VALUE;
        removeTracked(tracked, id, pin);
    }

    private static void removeTracked(ClientTrackedMessage tracked, long messageId, HudPin pin) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null) {
            if (messageId != Long.MIN_VALUE) ClientMessageIndex.remove(messageId);
            return;
        }

        ChatComponent chat = mc.gui.getChat();
        ChatComponentAccessor acc = (ChatComponentAccessor) chat;
        List<GuiMessage> all = acc.unsend$getAllMessages();

        boolean removed = false;
        if (all != null && !all.isEmpty()) {
            int idx = findExactIndex(all, tracked, pin, mc);
            if (idx >= 0 && idx < all.size()) {
                all.remove(idx);
                removed = true;
                acc.unsend$refreshTrimmedMessage();
            }
        }

        if (!removed && tracked != null && tracked.signature != null) {
            int sigHits = 0;
            if (all != null) {
                for (GuiMessage m : all) {
                    if (tracked.signature.equals(m.headerSignature())) sigHits++;
                }
            }
            if (sigHits == 1) {
                chat.deleteMessage(tracked.signature);
                acc.unsend$refreshTrimmedMessage();
                removed = true;
            }
        }

        if (messageId != Long.MIN_VALUE) {
            ClientMessageIndex.remove(messageId);
        }
    }

    /** Single index only. */
    private static int findExactIndex(List<GuiMessage> all, ClientTrackedMessage tracked,
                                     HudPin pin, Minecraft mc) {
        if (all == null || all.isEmpty()) return -1;

        MessageSignature forcedSig = pin != null ? pin.signature : null;
        int forcedRank = pin != null ? pin.rank : -1;
        int forcedTick = pin != null ? pin.addedTime : Integer.MIN_VALUE;
        String pinFull = pin != null ? pin.fullLine : null;
        String plain = pin != null && pin.plain != null && !pin.plain.isEmpty()
            ? ClientMessageIndex.stripEditedBadge(pin.plain)
            : (tracked != null && tracked.plainText != null
                ? ClientMessageIndex.stripEditedBadge(tracked.plainText) : "");

        if (forcedSig != null) {
            int only = -1;
            int hits = 0;
            for (int i = 0; i < all.size(); i++) {
                if (forcedSig.equals(all.get(i).headerSignature())) {
                    only = i;
                    hits++;
                }
            }
            if (hits == 1) return only;
        }

        if (pinFull != null && !pinFull.isEmpty()) {
            List<Integer> exact = new ArrayList<>();
            for (int i = 0; i < all.size(); i++) {
                if (pinFull.equals(fullOf(all.get(i)))) exact.add(i);
            }
            if (exact.size() == 1) return exact.get(0);
            if (exact.size() > 1 && forcedRank >= 0 && forcedRank < exact.size()) {
                return exact.get(forcedRank);
            }
        }

        if (plain.isEmpty()) {
            if (tracked != null && tracked.signature != null) {
                for (int i = 0; i < all.size(); i++) {
                    if (tracked.signature.equals(all.get(i).headerSignature())) return i;
                }
            }
            return -1;
        }

        String selfName = mc.player != null ? mc.player.getGameProfile().getName() : null;
        UUID self = mc.player != null ? mc.player.getUUID() : null;

        List<Integer> ownSame = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            String full = fullOf(all.get(i));
            if (!bodyEquals(full, plain)) continue;
            if (selfName != null) {
                String own = ClientMessageIndex.extractOwnPlain(full, selfName);
                if (own == null || !plain.equals(ClientMessageIndex.stripEditedBadge(own))) {
                    if (forcedSig == null || all.get(i).headerSignature() == null
                        || !forcedSig.equals(all.get(i).headerSignature())) {
                        if (forcedTick == Integer.MIN_VALUE
                            || all.get(i).addedTime() != forcedTick) {
                            continue;
                        }
                    }
                }
            }
            ownSame.add(i);
        }
        if (ownSame.isEmpty()) {
            for (int i = 0; i < all.size(); i++) {
                if (bodyEquals(fullOf(all.get(i)), plain)) ownSame.add(i);
            }
        }
        if (ownSame.isEmpty()) return -1;
        if (ownSame.size() == 1) return ownSame.get(0);

        if (forcedRank >= 0 && forcedRank < ownSame.size()) {
            return ownSame.get(forcedRank);
        }

        int tick = forcedTick != Integer.MIN_VALUE
            ? forcedTick
            : (tracked != null ? tracked.addedTime : Integer.MIN_VALUE);
        if (tick != Integer.MIN_VALUE) {
            int only = -1;
            int hits = 0;
            for (int ci : ownSame) {
                if (all.get(ci).addedTime() == tick) {
                    only = ci;
                    hits++;
                }
            }
            if (hits == 1) return only;
        }

        if (tracked != null && tracked.signature != null) {
            for (int ci : ownSame) {
                if (tracked.signature.equals(all.get(ci).headerSignature())) return ci;
            }
        }

        int rank = tracked != null ? ClientMessageIndex.rankAmongSamePlain(tracked) : -1;
        if (rank >= 0 && rank < ownSame.size()) return ownSame.get(rank);

        return -1;
    }

    private static String fullOf(GuiMessage m) {
        if (m == null || m.content() == null) return "";
        return ClientMessageIndex.stripFormatting(m.content().getString()).trim();
    }

    /**
     * Wipe one HUD line matching sender name body when we have no tracked id
     * (remote unsend after register race). Newest match first.
     */
    public static void removeBySenderAndPlain(UUID sender, String plain) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null || plain == null || plain.isBlank()) return;
        ChatComponent chat = mc.gui.getChat();
        if (!(chat instanceof ChatComponentAccessor acc)) return;
        List<GuiMessage> all = acc.unsend$getAllMessages();
        if (all == null || all.isEmpty()) return;

        String want = ClientMessageIndex.stripEditedBadge(plain.trim());
        String selfName = null;
        if (sender != null && mc.getConnection() != null) {
            // Best-effort: use online profile name if available
            var info = mc.getConnection().getPlayerInfo(sender);
            if (info != null) selfName = info.getProfile().getName();
        }

        int hit = -1;
        for (int i = 0; i < all.size(); i++) {
            String full = fullOf(all.get(i));
            if (!bodyEquals(full, want)) continue;
            if (selfName != null) {
                String own = ClientMessageIndex.extractOwnPlain(full, selfName);
                if (own == null || !want.equals(ClientMessageIndex.stripEditedBadge(own))) continue;
            }
            hit = i;
            break; // newest-first list
        }
        if (hit >= 0) {
            all.remove(hit);
            acc.unsend$refreshTrimmedMessage();
        }
    }

    private static boolean bodyEquals(String full, String plain) {
        if (full == null || plain == null || plain.isEmpty()) return false;
        String cleanPlain = ClientMessageIndex.stripEditedBadge(plain);
        if (full.equals(cleanPlain) || full.equals(plain)) return true;

        String line = full;
        int nl = full.lastIndexOf('\n');
        if (nl >= 0 && nl + 1 < full.length()) {
            line = full.substring(nl + 1).trim();
        }

        int gt = line.lastIndexOf('>');
        if (gt >= 0 && gt + 1 < line.length()) {
            String body = line.substring(gt + 1).trim();
            if (body.startsWith(":")) body = body.substring(1).trim();
            body = ClientMessageIndex.stripEditedBadge(body);
            return body.equals(cleanPlain);
        }

        int colon = line.indexOf(':');
        if (colon > 0 && line.indexOf('<') < 0) {
            return ClientMessageIndex.stripEditedBadge(line.substring(colon + 1).trim())
                .equals(cleanPlain);
        }
        return false;
    }
}
