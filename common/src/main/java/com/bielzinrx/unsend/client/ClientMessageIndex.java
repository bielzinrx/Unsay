package com.bielzinrx.unsend.client;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ClientMessageIndex {
    private static final Deque<PendingRegistration> PENDING = new ArrayDeque<>();
    private static final Map<Long, ClientTrackedMessage> BY_ID = new ConcurrentHashMap<>();
    /** Server/local ids that were unsent — never re-track or edit. */
    private static final Set<Long> TOMBSTONE_IDS = ConcurrentHashMap.newKeySet();
    /** Chat-line identity: sender|addedTime|plainText. */
    private static final Set<String> TOMBSTONE_LINES = ConcurrentHashMap.newKeySet();
    private static final Set<MessageSignature> TOMBSTONE_SIGS = ConcurrentHashMap.newKeySet();
    private static final long PENDING_TTL_MS = 8000L;

    private static final AtomicLong LOCAL_IDS = new AtomicLong(-1L);

    private ClientMessageIndex() {}

    public static void clear() {
        PENDING.clear();
        BY_ID.clear();
        TOMBSTONE_IDS.clear();
        TOMBSTONE_LINES.clear();
        TOMBSTONE_SIGS.clear();
        UnsendComposer.clear();
    }

    public static boolean isTombstoned(long id) {
        return TOMBSTONE_IDS.contains(id);
    }

    public static boolean isTombstoned(ClientTrackedMessage m) {
        if (m == null) return false;
        if (TOMBSTONE_IDS.contains(m.id)) return true;
        if (m.signature != null && TOMBSTONE_SIGS.contains(m.signature)) return true;
        return TOMBSTONE_LINES.contains(lineKey(m.sender, m.addedTime, m.plainText));
    }

    /** Remember a deleted line forever (this session) and purge only true twins. */
    public static void tombstone(ClientTrackedMessage snap) {
        if (snap == null) return;
        TOMBSTONE_IDS.add(snap.id);

        if (snap.signature != null && !hudStillHasSignature(snap.signature)) {
            TOMBSTONE_SIGS.add(snap.signature);
        }
        String key = lineKey(snap.sender, snap.addedTime, snap.plainText);
        if (!hudStillHasLine(snap.sender, snap.addedTime, snap.plainText)) {
            TOMBSTONE_LINES.add(key);
        }

        List<Long> drop = new ArrayList<>();
        for (ClientTrackedMessage o : BY_ID.values()) {
            if (o.id == snap.id) {
                drop.add(o.id);
                continue;
            }
            if (snap.signature != null && snap.signature.equals(o.signature)) {
                TOMBSTONE_IDS.add(o.id);
                drop.add(o.id);
                continue;
            }
            if (snap.sender != null && snap.sender.equals(o.sender)
                && snap.addedTime == o.addedTime
                && snap.plainText != null && snap.plainText.equals(o.plainText)
                && (snap.id < 0) != (o.id < 0)) {
                TOMBSTONE_IDS.add(o.id);
                drop.add(o.id);
            }
        }
        for (long id : drop) {
            BY_ID.remove(id);
        }
    }

    /** Clear a signature ban (visible line still on the HUD). */
    public static void clearSignatureTombstone(MessageSignature signature) {
        if (signature != null) TOMBSTONE_SIGS.remove(signature);
    }

    /** Mark id only — does not touch BY_ID (safe before HUD wipe). */
    public static void tombstoneId(long id) {
        TOMBSTONE_IDS.add(id);
    }

    private static String lineKey(UUID sender, int addedTime, String plain) {
        String body = plain == null ? "" : stripEditedBadge(plain);
        return String.valueOf(sender) + '|' + addedTime + '|' + body;
    }

    private static boolean hudStillHasSignature(MessageSignature signature) {
        if (signature == null) return false;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gui == null) return false;
            if (!(mc.gui.getChat() instanceof com.bielzinrx.unsend.mixin.ChatComponentAccessor acc)) {
                return false;
            }
            List<GuiMessage> all = acc.unsend$getAllMessages();
            if (all == null) return false;
            for (GuiMessage m : all) {
                if (signature.equals(m.headerSignature())) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean hudStillHasLine(UUID sender, int addedTime, String plain) {
        String want = plain == null ? "" : stripEditedBadge(plain);
        if (want.isEmpty()) return false;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gui == null || mc.player == null) return false;
            if (!(mc.gui.getChat() instanceof com.bielzinrx.unsend.mixin.ChatComponentAccessor acc)) {
                return false;
            }
            List<GuiMessage> all = acc.unsend$getAllMessages();
            if (all == null) return false;
            String selfName = mc.player.getGameProfile().getName();
            for (GuiMessage m : all) {
                if (m.addedTime() != addedTime) continue;
                String full = stripFormatting(m.content().getString());
                String own = extractOwnPlain(full, selfName);
                if (own != null && want.equals(stripEditedBadge(own))) return true;
                if (full.endsWith(want) || full.contains("> " + want) || full.contains(": " + want)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void reviveVisibleLine(GuiMessage msg, UUID self, String ownPlain) {
        if (msg == null) return;
        if (msg.headerSignature() != null) {
            TOMBSTONE_SIGS.remove(msg.headerSignature());
        }
        if (self != null && ownPlain != null) {
            TOMBSTONE_LINES.remove(lineKey(self, msg.addedTime(), ownPlain));
        }
    }

    private static ClientTrackedMessage reviveTracked(ClientTrackedMessage t) {
        if (t == null) return null;
        TOMBSTONE_IDS.remove(t.id);
        if (t.signature != null) TOMBSTONE_SIGS.remove(t.signature);
        TOMBSTONE_LINES.remove(lineKey(t.sender, t.addedTime, t.plainText));
        t.deleting = false;
        return t;
    }

    public static void onRegisterPacket(long messageId, UUID sender, String senderName, String plainText) {
        prunePending();
        if (isTombstoned(messageId)) return;

        ClientTrackedMessage local = findProvisional(sender, plainText);
        if (local != null) {
            ClientTrackedMessage merged = new ClientTrackedMessage(
                messageId, sender, senderName != null ? senderName : local.senderName,
                plainText, local.signature, local.addedTime, local.displayContent);
            merged.edited = local.edited;
            BY_ID.put(messageId, merged);
            BY_ID.remove(local.id);
            UnsendComposer.remapTrackedId(local.id, messageId);
            return;
        }
        if (tryBindToExistingHud(messageId, sender, senderName, plainText)) {
            return;
        }
        PENDING.addLast(new PendingRegistration(messageId, sender, senderName, plainText, System.currentTimeMillis()));
    }

    /** Oldest provisional with this exact body (FIFO). */
    private static ClientTrackedMessage findProvisional(UUID sender, String plainText) {
        if (plainText == null) return null;
        ClientTrackedMessage best = null;
        for (ClientTrackedMessage m : BY_ID.values()) {
            if (m.id >= 0 || m.deleting) continue;
            if (sender != null && !m.isOwnedBy(sender) && !sender.equals(m.sender)) continue;
            if (m.plainText == null || !plainText.equals(m.plainText)) continue;
            if (best == null || m.id > best.id) best = m;  // oldest provisional
        }
        return best;
    }

    private static boolean tryBindToExistingHud(long messageId, UUID sender, String senderName, String plainText) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gui == null) return false;
            var chat = mc.gui.getChat();
            if (!(chat instanceof com.bielzinrx.unsend.mixin.ChatComponentAccessor acc)) return false;
            List<GuiMessage> all = acc.unsend$getAllMessages();
            if (all == null) return false;
            for (int i = Math.min(all.size(), 48) - 1; i >= 0; i--) {
                GuiMessage msg = all.get(i);
                String content = stripFormatting(msg.content().getString());
                if (!matches(plainText, content)) continue;
                if (isHudLineClaimed(msg)) continue;
                BY_ID.put(messageId, new ClientTrackedMessage(
                    messageId, sender, senderName, plainText, msg.headerSignature(), msg.addedTime(), msg.content()));
                return true;
            }
            return false;
        } catch (Throwable ignored) {
        }
        return false;
    }


    private static boolean isHudLineClaimed(GuiMessage msg) {
        if (msg == null) return false;
        if (msg.headerSignature() != null) {
            for (ClientTrackedMessage m : BY_ID.values()) {
                if (m.deleting) continue;
                if (msg.headerSignature().equals(m.signature)) return true;
            }
        }
        String full = stripFormatting(msg.content().getString());
        for (ClientTrackedMessage m : BY_ID.values()) {
            if (m.deleting) continue;
            if (m.addedTime == msg.addedTime() && exactLineOrBody(m, full)) return true;
        }
        return false;
    }

    public static void onChatMessageAdded(GuiMessage guiMessage) {
        if (guiMessage == null) return;
        prunePending();

        String content = stripFormatting(guiMessage.content().getString());
        long now = System.currentTimeMillis();

        Iterator<PendingRegistration> it = PENDING.iterator();
        while (it.hasNext()) {
            PendingRegistration p = it.next();
            if (now - p.createdAtMs > PENDING_TTL_MS) {
                it.remove();
                continue;
            }
            if (isTombstoned(p.messageId)) {
                it.remove();
                continue;
            }
            if (matches(p.plainText, content)) {
                it.remove();
                if (guiMessage.headerSignature() != null && TOMBSTONE_SIGS.contains(guiMessage.headerSignature())) {
                    TOMBSTONE_IDS.add(p.messageId);
                    return;
                }
                BY_ID.put(p.messageId, new ClientTrackedMessage(
                    p.messageId, p.sender, p.senderName, p.plainText,
                    guiMessage.headerSignature(), guiMessage.addedTime(), guiMessage.content()));
                return;
            }
        }

        tryTrackOwn(guiMessage, content);
    }

    private static void tryTrackOwn(GuiMessage guiMessage, String fullContent) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String name = mc.player.getGameProfile().getName();
        String plain = extractOwnPlain(fullContent, name);
        if (plain == null) return;

        if (TOMBSTONE_LINES.contains(lineKey(mc.player.getUUID(), guiMessage.addedTime(), plain))) return;
        if (guiMessage.headerSignature() != null && TOMBSTONE_SIGS.contains(guiMessage.headerSignature())) {
            clearSignatureTombstone(guiMessage.headerSignature());
        }

        if (findBySignature(guiMessage.headerSignature()) != null) return;
        if (findByAddedTimeAndContent(guiMessage.addedTime(), guiMessage.content()) != null) return;

        long id = LOCAL_IDS.getAndDecrement();
        BY_ID.put(id, new ClientTrackedMessage(
            id, mc.player.getUUID(), name, plain,
            guiMessage.headerSignature(), guiMessage.addedTime(), guiMessage.content()));
    }

    public static String extractOwnPlain(String full, String playerName) {
        if (full == null || playerName == null || playerName.isEmpty()) return null;
        String f = full.trim();
        String angled = "<" + playerName + ">";
        int idx = f.indexOf(angled);
        if (idx >= 0) {
            String body = f.substring(idx + angled.length()).trim();

            if (body.startsWith(":")) body = body.substring(1).trim();
            return stripEditedBadge(body);
        }
        String colon = playerName + ":";
        if (f.startsWith(colon)) {
            return stripEditedBadge(f.substring(colon.length()).trim());
        }

        String lower = f.toLowerCase();
        String angledL = angled.toLowerCase();
        int i2 = lower.indexOf(angledL);
        if (i2 >= 0) {
            return stripEditedBadge(f.substring(i2 + angled.length()).trim());
        }
        return null;
    }

    /** Strip the visual "(edited)" badge from a body string. */
    public static String stripEditedBadge(String s) {
        if (s == null || s.isEmpty()) return s == null ? "" : s;
        String out = s;
        try {
            String badge = net.minecraft.network.chat.Component
                .translatable("unsend.badge.edited").getString();
            if (badge != null && !badge.isEmpty()) {
                out = out.replace(" " + badge, "").replace(badge, "");
            }
        } catch (Throwable ignored) {
        }
        out = out
            .replace(" (edited)", "")
            .replace("(edited)", "")
            .replace(" (editado)", "")
            .replace("(editado)", "")
            .replace(" (editad)", "")
            .replace("(editad)", "");
        out = out.replaceAll("(?i)\\s*\\(edit(?:ed|ado|ad)?\\)?\\s*$", "");
        return out.trim();
    }

    public static ClientTrackedMessage get(long id) {
        return BY_ID.get(id);
    }

    public static ClientTrackedMessage findBySignature(MessageSignature signature) {
        if (signature == null) return null;
        for (ClientTrackedMessage m : BY_ID.values()) {
            if (signature.equals(m.signature)) return m;
        }
        return null;
    }

    public static ClientTrackedMessage findByAddedTimeAndContent(int addedTime, Component content) {
        String text = content == null ? "" : stripFormatting(content.getString());
        for (ClientTrackedMessage m : BY_ID.values()) {
            if (m.addedTime != addedTime || m.deleting) continue;

            if (exactLineOrBody(m, text)) {
                return m;
            }
        }
        return null;
    }

    public static ClientTrackedMessage findOrCreateForGuiMessage(GuiMessage msg, UUID self, String selfName) {
        if (msg == null) return null;

        String full = stripFormatting(msg.content().getString());
        String ownPlain = selfName != null ? extractOwnPlain(full, selfName) : null;

        reviveVisibleLine(msg, self, ownPlain);

        ClientTrackedMessage t = findBySignature(msg.headerSignature());
        if (t != null) {
            if (isTombstoned(t.id) || t.deleting || isTombstoned(t)) {
                return reviveTracked(t);
            }
            return t;
        }
        t = findByAddedTimeAndContent(msg.addedTime(), msg.content());
        if (t != null) {
            if (isTombstoned(t.id) || t.deleting || isTombstoned(t)) {
                return reviveTracked(t);
            }
            return t;
        }

        if (ownPlain != null && self != null) {
            for (ClientTrackedMessage m : BY_ID.values()) {
                if (!m.isOwnedBy(self)) continue;
                if (m.addedTime == msg.addedTime() && exactLineOrBody(m, full)) {
                    if (m.deleting || isTombstoned(m.id) || isTombstoned(m)) {
                        return reviveTracked(m);
                    }
                    return m;
                }
            }
            for (ClientTrackedMessage m : BY_ID.values()) {
                if (!m.isOwnedBy(self)) continue;
                if (ownPlain.equals(m.plainText) && m.addedTime == msg.addedTime()) {
                    if (m.deleting || isTombstoned(m.id) || isTombstoned(m)) {
                        return reviveTracked(m);
                    }
                    return m;
                }
            }
            long id = LOCAL_IDS.getAndDecrement();
            ClientTrackedMessage created = new ClientTrackedMessage(
                id, self, selfName, ownPlain, msg.headerSignature(), msg.addedTime(), msg.content());
            BY_ID.put(id, created);
            return created;
        }

        ClientTrackedMessage unique = null;
        int hits = 0;
        for (ClientTrackedMessage m : BY_ID.values()) {
            if (m.deleting || m.id < 0) continue;
            if (!exactLineOrBody(m, full)) continue;
            if (m.addedTime == msg.addedTime()) return m;
            hits++;
            unique = m;
        }
        return hits == 1 ? unique : null;
    }

    private static boolean exactLineOrBody(ClientTrackedMessage m, String fullLine) {
        if (m == null || fullLine == null) return false;
        String full = stripFormatting(fullLine).trim();
        String plain = m.plainText == null ? "" : stripFormatting(m.plainText).trim();
        if (!plain.isEmpty() && full.equals(plain)) return true;
        if (m.displayContent != null) {
            String disp = stripFormatting(m.displayContent.getString()).trim();
            if (full.equals(disp)) return true;
        }

        if (!plain.isEmpty()) {
            int gt = full.lastIndexOf('>');
            if (gt >= 0 && gt + 1 < full.length()) {
                String body = full.substring(gt + 1).trim();
                if (body.startsWith(":")) body = body.substring(1).trim();

                int badge = body.lastIndexOf(" (");
                if (badge > 0) body = body.substring(0, badge).trim();
                return body.equals(plain);
            }
            int colon = full.indexOf(':');
            if (colon > 0 && colon + 1 < full.length() && full.indexOf('<') < 0) {
                String body = full.substring(colon + 1).trim();
                return body.equals(plain);
            }
        }
        return false;
    }

    public static ClientTrackedMessage findLastOwned(UUID self) {
        List<ClientTrackedMessage> owned = listOwned(self);
        return owned.isEmpty() ? null : owned.get(0);
    }

    /** Pull own lines still in the HUD into BY_ID (up to 200 most recent). */
    private static void backfillOwnedFromHud(UUID self) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.gui == null) return;
            if (!(mc.gui.getChat() instanceof com.bielzinrx.unsend.mixin.ChatComponentAccessor acc)) return;
            List<GuiMessage> all = acc.unsend$getAllMessages();
            if (all == null || all.isEmpty()) return;
            String selfName = mc.player.getGameProfile().getName();
            int n = Math.min(all.size(), 200);
            for (int i = 0; i < n; i++) {
                findOrCreateForGuiMessage(all.get(i), self, selfName);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Own messages newest → oldest (index 0 = most recent). */
    public static List<ClientTrackedMessage> listOwned(UUID self) {
        List<ClientTrackedMessage> owned = new ArrayList<>();
        if (self == null) return owned;
        backfillOwnedFromHud(self);
        for (ClientTrackedMessage m : BY_ID.values()) {
            if (m.isOwnedBy(self) && !m.deleting && !isTombstoned(m)) owned.add(m);
        }
        owned.sort((a, b) -> {
            int byTime = Integer.compare(b.addedTime, a.addedTime);
            if (byTime != 0) return byTime;
            if (a.id >= 0 && b.id >= 0) return Long.compare(b.id, a.id);
            if (a.id < 0 && b.id < 0) return Long.compare(a.id, b.id);
            if (a.id >= 0) return -1;
            return 1;
        });
        List<ClientTrackedMessage> deduped = new ArrayList<>();
        for (ClientTrackedMessage m : owned) {
            boolean twin = false;
            for (ClientTrackedMessage kept : deduped) {
                if (isSameChatLine(kept, m)) {
                    twin = true;
                    break;
                }
            }
            if (!twin) deduped.add(m);
        }
        return deduped;
    }

    /** Same HUD line: equal id/signature, or provisional+server twin of the same tick. */
    private static boolean isSameChatLine(ClientTrackedMessage a, ClientTrackedMessage b) {
        if (a == null || b == null) return false;
        if (a.id == b.id) return true;
        if (a.signature != null && a.signature.equals(b.signature)) return true;
        if (a.sender == null || !a.sender.equals(b.sender)) return false;
        if (a.plainText == null || !a.plainText.equals(b.plainText)) return false;
        if ((a.id < 0) != (b.id < 0) && a.addedTime == b.addedTime) return true;
        return false;
    }

    /** Rank among own messages with the same plain text, newest-first (0 = bottom/newest). */
    public static int rankAmongSamePlain(ClientTrackedMessage tracked) {
        if (tracked == null) return -1;
        List<ClientTrackedMessage> ordered = listSamePlainNewestFirst(tracked.sender, tracked.plainText);
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).id == tracked.id) return i;
            if (isSameChatLine(ordered.get(i), tracked)) return i;
        }
        return -1;
    }

    /** Newest-first list of distinct own lines with this plain text (for rank / edit target). */
    public static List<ClientTrackedMessage> listSamePlainNewestFirst(UUID sender, String plain) {
        List<ClientTrackedMessage> same = new ArrayList<>();
        if (plain == null) return same;
        for (ClientTrackedMessage m : BY_ID.values()) {
            if (m == null || m.deleting || isTombstoned(m)) continue;
            if (sender != null && !sender.equals(m.sender)) continue;
            if (!plain.equals(m.plainText)) continue;
            same.add(m);
        }
        same.sort((a, b) -> {
            int byTime = Integer.compare(b.addedTime, a.addedTime);
            if (byTime != 0) return byTime;
            if (a.id >= 0 && b.id >= 0) return Long.compare(b.id, a.id);
            if (a.id < 0 && b.id < 0) return Long.compare(a.id, b.id);
            return a.id >= 0 ? -1 : 1;
        });
        List<ClientTrackedMessage> deduped = new ArrayList<>();
        for (ClientTrackedMessage m : same) {
            boolean twin = false;
            for (ClientTrackedMessage k : deduped) {
                if (isSameChatLine(k, m)) {
                    twin = true;
                    break;
                }
            }
            if (!twin) deduped.add(m);
        }
        return deduped;
    }

    public static ClientTrackedMessage findBySamePlainRank(UUID sender, String plain, int rank) {
        List<ClientTrackedMessage> ordered = listSamePlainNewestFirst(sender, plain);
        if (rank < 0 || rank >= ordered.size()) return null;
        return ordered.get(rank);
    }

    /**
     * Locate a tracked row for a remote delete when the server id was never bound.
     * Prefers a unique plain match for the sender; if several twins exist, returns null
     * (caller falls back to HUD wipe of one line).
     */
    public static ClientTrackedMessage findBestForRemote(UUID sender, String plain) {
        if (plain == null || plain.isBlank()) return null;
        String want = stripEditedBadge(plain.trim());
        List<ClientTrackedMessage> hits = new ArrayList<>();
        for (ClientTrackedMessage m : BY_ID.values()) {
            if (m == null || m.deleting || isTombstoned(m)) continue;
            if (sender != null && !sender.equals(m.sender)) continue;
            String p = m.plainText == null ? "" : stripEditedBadge(m.plainText);
            if (want.equals(p)) hits.add(m);
        }
        if (hits.isEmpty()) return null;
        if (hits.size() == 1) return hits.get(0);
        // Prefer a server-id row (newest id first)
        hits.sort((a, b) -> {
            if (a.id >= 0 && b.id >= 0) return Long.compare(b.id, a.id);
            if (a.id >= 0) return -1;
            if (b.id >= 0) return 1;
            return Integer.compare(b.addedTime, a.addedTime);
        });
        return hits.get(0);
    }

    public static Iterable<ClientTrackedMessage> all() {
        return BY_ID.values();
    }

    public static void remove(long id) {
        BY_ID.remove(id);
    }

    public static void applyEdit(long id, String newText) {
        applyEdit(id, newText, (ChatHudEditor.HudPin) null);
    }

    /**
     * @param forcedRank newest-first rank among same plain ({@code -1} = compute)
     * @param forcedAddedTime tick hint ({@code Integer.MIN_VALUE} = ignore)
     * @param forcedSig signature hint ({@code null} = ignore)
     */
    public static void applyEdit(long id, String newText, int forcedRank, int forcedAddedTime,
                                 MessageSignature forcedSig) {
        ChatHudEditor.HudPin pin = null;
        if (forcedRank >= 0 || forcedAddedTime != Integer.MIN_VALUE || forcedSig != null) {
            ClientTrackedMessage cur = BY_ID.get(id);
            String plain = cur != null ? cur.plainText : "";
            pin = new ChatHudEditor.HudPin(forcedRank, forcedAddedTime, forcedSig, null, plain);
        }
        applyEdit(id, newText, pin);
    }

    public static void applyEdit(long id, String newText, ChatHudEditor.HudPin pin) {
        if (isTombstoned(id)) return;
        ClientTrackedMessage m = BY_ID.get(id);

        if (m == null || m.deleting || isTombstoned(m)) {
            return;
        }
        ChatHudEditor.applyEdit(m, newText, pin);
        m = BY_ID.get(id);
        if (m != null && !m.deleting && !isTombstoned(m)) {
            m.plainText = stripEditedBadge(newText == null ? "" : newText);
            m.edited = true;
        }
    }

    public static boolean matches(String plain, String fullChatLine) {
        if (plain == null || plain.isEmpty()) return false;
        if (fullChatLine == null) return false;
        String a = stripFormatting(plain).trim();
        String b = stripFormatting(fullChatLine).trim();
        if (a.isEmpty() || b.isEmpty()) return false;
        if (b.equals(a)) return true;
        if (b.endsWith(a)) {
            int idx = b.lastIndexOf(a);
            if (idx <= 0) return true;
            char before = b.charAt(idx - 1);
            return before == ' ' || before == '>' || before == ':';
        }

        if (a.length() <= 2) {
            return b.contains("> " + a) || b.endsWith("> " + a) || b.contains(": " + a);
        }
        return b.contains(a);
    }

    public static String stripFormatting(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static void prunePending() {
        long now = System.currentTimeMillis();
        PENDING.removeIf(p -> now - p.createdAtMs > PENDING_TTL_MS);
    }

    private record PendingRegistration(long messageId, UUID sender, String senderName, String plainText, long createdAtMs) {}

    public static final class ClientTrackedMessage {
        public final long id;
        public final UUID sender;
        public final String senderName;
        public String plainText;
        public MessageSignature signature;
        public int addedTime;
        public Component displayContent;
        public boolean deleting;
        public boolean edited;

        public ClientTrackedMessage(long id, UUID sender, String senderName, String plainText,
                                    MessageSignature signature, int addedTime, Component displayContent) {
            this.id = id;
            this.sender = sender;
            this.senderName = senderName == null ? "" : senderName;
            this.plainText = plainText;
            this.signature = signature;
            this.addedTime = addedTime;
            this.displayContent = displayContent;
        }

        public boolean isOwnedBy(UUID player) {
            return player != null && player.equals(sender);
        }

        public boolean hasServerId() {
            return id >= 0;
        }
    }
}
