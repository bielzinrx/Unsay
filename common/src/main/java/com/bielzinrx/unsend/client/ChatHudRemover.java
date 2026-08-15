package com.bielzinrx.unsend.client;

import com.bielzinrx.unsend.client.ChatHudEditor.HudPin;
import com.bielzinrx.unsend.client.ClientMessageIndex.ClientTrackedMessage;
import com.bielzinrx.unsend.mixin.ChatComponentAccessor;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.MessageSignature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ChatHudRemover {
    private ChatHudRemover() {}

    public static void removeMessage(long messageId) {
        ClientTrackedMessage tracked = ClientMessageIndex.get(messageId);
        HudPin pin = tracked != null ? ChatHudEditor.capturePin(tracked) : null;
        removeTracked(tracked, messageId, pin);
    }


    /**
     * Removes a server-identified message deterministically on every client. This is the remote
     * multiplayer path: server id order selects the exact duplicate row even when several lines
     * have identical text and no client shares GuiMessage object identity with another client.
     */
    public static boolean removeAuthoritative(long messageId, UUID sender, String plain,
                                              ClientTrackedMessage tracked) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null) return false;
        ChatComponent chat = mc.gui.getChat();
        if (!(chat instanceof ChatComponentAccessor acc)) return false;
        List<GuiMessage> all = acc.unsend$getAllMessages();
        if (all == null || all.isEmpty()) return false;

        if (sender != null && plain != null && !plain.isBlank()) {
            ClientMessageIndex.rebindServerRows(sender, plain);
        }

        // First choice: the stable id has been rebound to the exact GUI row.
        if (tracked != null && tracked.guiRef != null) {
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i) == tracked.guiRef) {
                    all.remove(i);
                    acc.unsend$refreshTrimmedMessage();
                    return true;
                }
            }
        }

        // Second choice: derive the duplicate occurrence from monotonic server ids.
        if (sender != null && plain != null && !plain.isBlank()) {
            String senderName = tracked != null ? tracked.senderName : resolveName(mc, sender);
            int rank = ClientMessageIndex.serverRank(messageId, sender, plain);
            List<Integer> candidates = playerLineIndices(all, senderName, plain);
            if (rank >= 0 && rank < candidates.size()) {
                all.remove((int) candidates.get(rank));
                acc.unsend$refreshTrimmedMessage();
                return true;
            }
            if (candidates.size() == 1) {
                all.remove((int) candidates.get(0));
                acc.unsend$refreshTrimmedMessage();
                return true;
            }
        }

        return false;
    }

    /** Removes orphaned duplicate rows after the server index has removed one message. */
    public static void reconcileAuthoritativeGroup(UUID sender, String plain) {
        Minecraft mc = Minecraft.getInstance();
        reconcileAuthoritativeGroup(sender, resolveName(mc, sender), plain);
    }

    /** Snapshot variant that also works when the deleted message author is currently offline. */
    public static void reconcileAuthoritativeGroup(UUID sender, String senderName, String plain) {
        if (sender == null || plain == null || plain.isBlank()) return;
        if (ClientMessageIndex.hasPendingRegistration(sender, plain)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null) return;
        ChatComponent chat = mc.gui.getChat();
        if (!(chat instanceof ChatComponentAccessor acc)) return;
        List<GuiMessage> all = acc.unsend$getAllMessages();
        if (all == null || all.isEmpty()) return;

        ClientMessageIndex.rebindServerRows(sender, plain);
        int expected = ClientMessageIndex.activeServerCount(sender, plain);
        String resolvedName = senderName == null || senderName.isBlank()
            ? resolveName(mc, sender) : senderName;
        List<Integer> candidates = playerLineIndices(all, resolvedName, plain);
        if (candidates.size() <= expected) return;

        Set<GuiMessage> claimed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ClientTrackedMessage m : ClientMessageIndex.all()) {
            if (m == null || m.id <= 0L || m.deleting || m.guiRef == null) continue;
            if (!sender.equals(m.sender)) continue;
            if (!ClientMessageIndex.stripEditedBadge(plain)
                .equals(ClientMessageIndex.stripEditedBadge(m.plainText))) continue;
            claimed.add(m.guiRef);
        }

        int excess = candidates.size() - expected;
        for (int i = candidates.size() - 1; i >= 0 && excess > 0; i--) {
            int idx = candidates.get(i);
            if (idx < 0 || idx >= all.size()) continue;
            GuiMessage gui = all.get(idx);
            if (claimed.contains(gui)) continue;
            all.remove(idx);
            excess--;
        }
        if (excess < candidates.size() - expected) acc.unsend$refreshTrimmedMessage();
        ClientMessageIndex.rebindServerRows(sender, plain);
    }

    private static List<Integer> playerLineIndices(List<GuiMessage> all, String senderName, String plain) {
        List<Integer> result = new ArrayList<>();
        if (all == null || senderName == null || senderName.isBlank()) return result;
        for (int i = 0; i < all.size(); i++) {
            if (ClientMessageIndex.matchesPlayerLine(all.get(i), senderName, plain)) result.add(i);
        }
        return result;
    }

    private static String resolveName(Minecraft mc, UUID sender) {
        if (mc != null && sender != null && mc.getConnection() != null) {
            var info = mc.getConnection().getPlayerInfo(sender);
            if (info != null && info.getProfile() != null) return info.getProfile().getName();
        }
        for (ClientTrackedMessage m : ClientMessageIndex.all()) {
            if (m != null && sender != null && sender.equals(m.sender)
                && m.senderName != null && !m.senderName.isBlank()) return m.senderName;
        }
        return "";
    }

    /** Removes the exact GUI row captured at selection time, even if its tracker is gone. */
    public static boolean removePinned(HudPin pin) {
        if (pin == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null) return false;
        ChatComponent chat = mc.gui.getChat();
        if (!(chat instanceof ChatComponentAccessor acc)) return false;
        List<GuiMessage> all = acc.unsend$getAllMessages();
        if (all == null || all.isEmpty()) return false;

        // Identity is authoritative. If another chat refresh replaced the GuiMessage object,
        // fall back to the captured signature/tick/full-line/rank instead of leaving a ghost row.
        int index = findExactIndex(all, null, pin, mc);
        if (index < 0 || index >= all.size()) return false;
        all.remove(index);
        acc.unsend$refreshTrimmedMessage();
        return true;
    }

    /** Final safety net for a row that was tombstoned after an interrupted older build. */
    public static void purgeTombstonedRows() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null) return;
        ChatComponent chat = mc.gui.getChat();
        if (!(chat instanceof ChatComponentAccessor acc)) return;
        List<GuiMessage> all = acc.unsend$getAllMessages();
        if (all == null || all.isEmpty()) return;
        boolean changed = all.removeIf(ClientMessageIndex::isGuiTombstoned);
        if (changed) acc.unsend$refreshTrimmedMessage();
    }

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
                    if (tracked.signature.equals(m.signature())) sigHits++;
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

    private static int findExactIndex(List<GuiMessage> all, ClientTrackedMessage tracked,
                                     HudPin pin, Minecraft mc) {
        if (all == null || all.isEmpty()) return -1;

        if (pin != null && pin.guiRef != null) {
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i) == pin.guiRef) return i;
            }
        }

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
                if (forcedSig.equals(all.get(i).signature())) {
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
                    if (tracked.signature.equals(all.get(i).signature())) return i;
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
                    if (forcedSig == null || all.get(i).signature() == null
                        || !forcedSig.equals(all.get(i).signature())) {
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
                if (tracked.signature.equals(all.get(ci).signature())) return ci;
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
            var info = mc.getConnection().getPlayerInfo(sender);
            if (info != null) selfName = info.getProfile().getName();
        }

        int hit = -1;
        int hits = 0;
        for (int i = 0; i < all.size(); i++) {
            String full = fullOf(all.get(i));
            if (!bodyEquals(full, want)) continue;
            if (selfName != null) {
                String own = ClientMessageIndex.extractOwnPlain(full, selfName);
                if (own == null || !want.equals(ClientMessageIndex.stripEditedBadge(own))) continue;
            }
            hit = i;
            hits++;
        }
        if (hits == 1 && hit >= 0) {
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
