package com.bielzinrx.unsend.client;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ClientMessageIndex {
    private static final Deque<PendingRegistration> PENDING = new ArrayDeque<>();
    private static final Map<Long, ClientTrackedMessage> BY_ID = new ConcurrentHashMap<>();
    private static final Set<Long> TOMBSTONE_IDS = ConcurrentHashMap.newKeySet();
    private static final Set<String> TOMBSTONE_LINES = ConcurrentHashMap.newKeySet();
    private static final Set<MessageSignature> TOMBSTONE_SIGS = ConcurrentHashMap.newKeySet();
    /** Identity-based: record equality would collapse identical same-tick chat rows. */
    private static final Set<GuiMessage> TOMBSTONE_GUI_REFS =
        Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private static final long PENDING_TTL_MS = 8000L;

    private static final AtomicLong LOCAL_IDS = new AtomicLong(-1L);

    private ClientMessageIndex() {}

    public static void clear() {
        PENDING.clear();
        BY_ID.clear();
        TOMBSTONE_IDS.clear();
        TOMBSTONE_LINES.clear();
        TOMBSTONE_SIGS.clear();
        TOMBSTONE_GUI_REFS.clear();
        UnsendComposer.clear();
        ClientActionState.clear();
    }

    public static boolean isTombstoned(long id) {
        return TOMBSTONE_IDS.contains(id);
    }

    public static boolean isGuiTombstoned(GuiMessage message) {
        return message != null && TOMBSTONE_GUI_REFS.contains(message);
    }

    public static void tombstoneGui(GuiMessage message) {
        if (message != null) TOMBSTONE_GUI_REFS.add(message);
    }

    public static boolean isTombstoned(ClientTrackedMessage m) {
        if (m == null) return false;
        if (TOMBSTONE_IDS.contains(m.id)) return true;
        if (m.signature != null && TOMBSTONE_SIGS.contains(m.signature)) return true;
        return TOMBSTONE_LINES.contains(lineKey(m.sender, m.addedTime, m.plainText));
    }

    public static void tombstone(ClientTrackedMessage snap) {
        if (snap == null) return;
        TOMBSTONE_IDS.add(snap.id);
        if (snap.guiRef != null) TOMBSTONE_GUI_REFS.add(snap.guiRef);

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

    public static void clearSignatureTombstone(MessageSignature signature) {
        if (signature != null) TOMBSTONE_SIGS.remove(signature);
    }

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
                if (signature.equals(m.signature())) return true;
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

    public static void onRegisterPacket(long messageId, UUID sender, String senderName, String plainText) {
        prunePending();
        if (isTombstoned(messageId)) return;

        ClientTrackedMessage local = findProvisional(sender, plainText);
        if (local != null) {
            ClientTrackedMessage merged = new ClientTrackedMessage(
                messageId, sender, senderName != null ? senderName : local.senderName,
                plainText, local.signature, local.addedTime, local.displayContent);
            merged.edited = local.edited;
            merged.guiRef = local.guiRef;
            BY_ID.put(messageId, merged);
            BY_ID.remove(local.id);
            ClientBulkDelete.remapSelectedId(local.id, messageId);
            UnsendComposer.remapTrackedId(local.id, messageId);
            rebindServerRows(sender, plainText);
            return;
        }
        if (tryBindToExistingHud(messageId, sender, senderName, plainText)) {
            rebindServerRows(sender, plainText);
            return;
        }
        PENDING.addLast(new PendingRegistration(messageId, sender, senderName, plainText, System.currentTimeMillis()));
    }

    public static void applySnapshot(List<com.bielzinrx.unsend.network.Packets.SnapshotEntry> entries) {
        if (entries == null || entries.isEmpty()) return;
        List<com.bielzinrx.unsend.network.Packets.SnapshotEntry> ordered = new ArrayList<>(entries);
        ordered.sort((a, b) -> Long.compare(a.messageId(), b.messageId()));
        for (com.bielzinrx.unsend.network.Packets.SnapshotEntry e : ordered) {
            if (e == null || isTombstoned(e.messageId())) continue;
            if (BY_ID.containsKey(e.messageId())) {
                ClientTrackedMessage existing = BY_ID.get(e.messageId());
                if (existing != null && e.edited()) existing.edited = true;
                continue;
            }
            onRegisterPacket(e.messageId(), e.sender(), e.senderName(), e.plainText());
            ClientTrackedMessage m = BY_ID.get(e.messageId());
            if (m != null && e.edited()) m.edited = true;
        }
    }

    public static void promoteToServerId(ClientTrackedMessage provisional, long serverId,
                                         UUID sender, String plainText) {
        if (provisional == null || serverId < 0) return;
        if (isTombstoned(serverId)) return;
        ClientTrackedMessage merged = new ClientTrackedMessage(
            serverId,
            sender != null ? sender : provisional.sender,
            provisional.senderName,
            plainText != null ? plainText : provisional.plainText,
            provisional.signature,
            provisional.addedTime,
            provisional.displayContent
        );
        merged.edited = provisional.edited;
        merged.guiRef = provisional.guiRef;
        BY_ID.put(serverId, merged);
        BY_ID.remove(provisional.id);
        ClientBulkDelete.remapSelectedId(provisional.id, serverId);
        UnsendComposer.remapTrackedId(provisional.id, serverId);
    }

    public static void applyRemoteEditLoose(UUID sender, String oldPlain, String newText, long serverId) {
        if (oldPlain == null || newText == null || newText.isBlank()) return;
        Minecraft mc = Minecraft.getInstance();
        String name = null;
        if (sender != null && mc.getConnection() != null) {
            var info = mc.getConnection().getPlayerInfo(sender);
            if (info != null) name = info.getProfile().getName();
        }
        long id = serverId >= 0 ? serverId : LOCAL_IDS.getAndDecrement();
        if (BY_ID.containsKey(id) || isTombstoned(id)) {
            ClientTrackedMessage t = BY_ID.get(id);
            if (t != null) {
                ChatHudEditor.HudPin pin = ChatHudEditor.capturePin(t);
                t.plainText = oldPlain;
                applyEdit(id, newText, pin);
            }
            return;
        }
        ClientTrackedMessage synthetic = new ClientTrackedMessage(
            id,
            sender != null ? sender : (mc.player != null ? mc.player.getUUID() : new UUID(0, 0)),
            name != null ? name : "?",
            oldPlain,
            null,
            Integer.MIN_VALUE,
            null
        );
        BY_ID.put(id, synthetic);
        ChatHudEditor.HudPin pin = ChatHudEditor.capturePin(synthetic);
        applyEdit(id, newText, pin);
    }

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
                ClientTrackedMessage bound = new ClientTrackedMessage(
                    messageId, sender, senderName, plainText, msg.signature(), msg.addedTime(), msg.content());
                bound.guiRef = msg;
                BY_ID.put(messageId, bound);
                return true;
            }
            return false;
        } catch (Throwable ignored) {
        }
        return false;
    }


    private static boolean isHudLineClaimed(GuiMessage msg) {
        if (msg == null) return false;
        for (ClientTrackedMessage m : BY_ID.values()) {
            if (m == null || m.deleting) continue;
            if (m.guiRef == msg) return true;
        }
        if (msg.signature() != null) {
            for (ClientTrackedMessage m : BY_ID.values()) {
                if (m == null || m.deleting) continue;
                if (msg.signature().equals(m.signature)) return true;
            }
        }
        String full = stripFormatting(msg.content().getString());
        for (ClientTrackedMessage m : BY_ID.values()) {
            if (m == null || m.deleting || m.guiRef != null) continue;
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
                if (guiMessage.signature() != null && TOMBSTONE_SIGS.contains(guiMessage.signature())) {
                    TOMBSTONE_IDS.add(p.messageId);
                    return;
                }
                ClientTrackedMessage bound = new ClientTrackedMessage(
                    p.messageId, p.sender, p.senderName, p.plainText,
                    guiMessage.signature(), guiMessage.addedTime(), guiMessage.content());
                bound.guiRef = guiMessage;
                BY_ID.put(p.messageId, bound);
                rebindServerRows(p.sender, p.plainText);
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
        if (guiMessage.signature() != null && TOMBSTONE_SIGS.contains(guiMessage.signature())) {
            clearSignatureTombstone(guiMessage.signature());
        }

        if (findByGuiReference(guiMessage) != null) return;
        if (guiMessage.signature() != null && findBySignature(guiMessage.signature()) != null) return;

        long id = LOCAL_IDS.getAndDecrement();
        ClientTrackedMessage created = new ClientTrackedMessage(
            id, mc.player.getUUID(), name, plain,
            guiMessage.signature(), guiMessage.addedTime(), guiMessage.content());
        created.guiRef = guiMessage;
        BY_ID.put(id, created);
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

    /** Returns this exact own-message row's newest-first occurrence among equal texts. */
    public static int occurrenceFromNewest(ClientTrackedMessage tracked) {
        if (tracked == null) return 0;
        String wanted = stripEditedBadge(tracked.plainText == null ? "" : tracked.plainText);
        if (wanted.isEmpty()) return 0;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null && mc.gui != null
                && mc.gui.getChat() instanceof com.bielzinrx.unsend.mixin.ChatComponentAccessor acc) {
                List<GuiMessage> all = acc.unsend$getAllMessages();
                String selfName = mc.player.getGameProfile().getName();
                int occurrence = 0;
                if (all != null) {
                    for (GuiMessage msg : all) {
                        String full = stripFormatting(msg.content().getString());
                        String own = extractOwnPlain(full, selfName);
                        if (own == null || !wanted.equals(stripEditedBadge(own))) continue;
                        if (msg == tracked.guiRef) return occurrence;
                        occurrence++;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        List<ClientTrackedMessage> same = new ArrayList<>();
        for (ClientTrackedMessage candidate : BY_ID.values()) {
            if (candidate == null || candidate.deleting) continue;
            if (tracked.sender == null || !tracked.sender.equals(candidate.sender)) continue;
            if (!wanted.equals(stripEditedBadge(candidate.plainText))) continue;
            same.add(candidate);
        }
        same.sort((a, b) -> {
            int byTick = Integer.compare(b.addedTime, a.addedTime);
            if (byTick != 0) return byTick;
            return Long.compare(b.id, a.id);
        });
        for (int i = 0; i < same.size(); i++) {
            if (same.get(i) == tracked || same.get(i).id == tracked.id) return i;
        }
        return 0;
    }

    public static ClientTrackedMessage get(long id) {
        return BY_ID.get(id);
    }

    public static ClientTrackedMessage findByGuiReference(GuiMessage gui) {
        if (gui == null) return null;
        for (ClientTrackedMessage m : BY_ID.values()) {
            if (m != null && m.guiRef == gui) return m;
        }
        return null;
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

        if (isGuiTombstoned(msg)) return null;

        ClientTrackedMessage t = findByGuiReference(msg);
        if (t != null) {
            if (isTombstoned(t.id) || t.deleting || isTombstoned(t)) return null;
            return t;
        }

        t = findBySignature(msg.signature());
        if (t != null) {
            if (t.guiRef != null && t.guiRef != msg) return null;
            if (t.guiRef == null) t.guiRef = msg;
            if (isTombstoned(t.id) || t.deleting || isTombstoned(t)) return null;
            return t;
        }

        // Only bind an old/unbound tracker when the match is unique. Identical messages
        // created in the same game tick must remain separate selectable entries.
        ClientTrackedMessage uniqueUnbound = null;
        int unboundHits = 0;
        for (ClientTrackedMessage candidate : BY_ID.values()) {
            if (candidate == null || candidate.deleting || candidate.guiRef != null) continue;
            if (candidate.addedTime != msg.addedTime()) continue;
            if (!exactLineOrBody(candidate, full)) continue;
            uniqueUnbound = candidate;
            unboundHits++;
        }
        if (unboundHits == 1 && uniqueUnbound != null) {
            uniqueUnbound.guiRef = msg;
            if (isTombstoned(uniqueUnbound.id) || isTombstoned(uniqueUnbound)) return null;
            return uniqueUnbound;
        }

        if (ownPlain != null && self != null) {
            // No exact/bindable tracker exists for this GuiMessage, therefore this is a distinct
            // row even when its text and addedTime are identical to neighboring messages.
            long id = LOCAL_IDS.getAndDecrement();
            ClientTrackedMessage created = new ClientTrackedMessage(
                id, self, selfName, ownPlain, msg.signature(), msg.addedTime(), msg.content());
            created.guiRef = msg;
            BY_ID.put(id, created);
            return created;
        }

        // Foreign rows require their authoritative registration/snapshot. Reusing a tracker by
        // text here would make visually identical messages share one selection id.
        return null;
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

    private static boolean isSameChatLine(ClientTrackedMessage a, ClientTrackedMessage b) {
        if (a == null || b == null) return false;
        if (a.id == b.id) return true;
        if (a.signature != null && a.signature.equals(b.signature)) return true;
        if (a.sender == null || !a.sender.equals(b.sender)) return false;
        if (a.plainText == null || !a.plainText.equals(b.plainText)) return false;
        if ((a.id < 0) != (b.id < 0) && a.addedTime == b.addedTime) return true;
        return false;
    }

    public static int rankAmongSamePlain(ClientTrackedMessage tracked) {
        if (tracked == null) return -1;
        List<ClientTrackedMessage> ordered = listSamePlainNewestFirst(tracked.sender, tracked.plainText);
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).id == tracked.id) return i;
            if (isSameChatLine(ordered.get(i), tracked)) return i;
        }
        return -1;
    }

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
        return hits.size() == 1 ? hits.get(0) : null;
    }

    /** Known authoritative senders currently represented by the server snapshot/index. */
    public static List<SenderInfo> knownSenders() {
        Map<UUID, String> names = new LinkedHashMap<>();
        for (ClientTrackedMessage m : BY_ID.values()) {
            if (m == null || m.id <= 0L || m.sender == null || m.deleting || isTombstoned(m)) continue;
            names.putIfAbsent(m.sender, m.senderName == null ? "" : m.senderName);
        }
        List<SenderInfo> result = new ArrayList<>();
        for (Map.Entry<UUID, String> e : names.entrySet()) {
            result.add(new SenderInfo(e.getKey(), e.getValue()));
        }
        result.sort((a, b) -> {
            int byName = a.name.compareToIgnoreCase(b.name);
            if (byName != 0) return byName;
            return a.uuid.compareTo(b.uuid);
        });
        return result;
    }

    /**
     * Rebinds equal player-chat rows by stable server id order instead of text-only guessing.
     * ChatComponent#allMessages is newest-first; server message ids are monotonically increasing,
     * so descending ids map deterministically to descending GUI rows.
     */
    public static void rebindServerRows(UUID sender, String plain) {
        if (sender == null || plain == null || plain.isBlank()) return;
        List<ClientTrackedMessage> group = serverGroup(sender, plain);
        if (group.isEmpty()) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gui == null) return;
            if (!(mc.gui.getChat() instanceof com.bielzinrx.unsend.mixin.ChatComponentAccessor acc)) return;
            List<GuiMessage> all = acc.unsend$getAllMessages();
            if (all == null || all.isEmpty()) return;

            String senderName = group.get(0).senderName;
            List<GuiMessage> rows = new ArrayList<>();
            for (GuiMessage gui : all) {
                if (matchesPlayerLine(gui, senderName, plain)) rows.add(gui);
            }
            for (ClientTrackedMessage m : group) m.guiRef = null;
            int n = Math.min(group.size(), rows.size());
            for (int i = 0; i < n; i++) group.get(i).guiRef = rows.get(i);
        } catch (Throwable ignored) {
        }
    }

    public static int serverRank(long messageId, UUID sender, String plain) {
        if (messageId <= 0L || sender == null || plain == null) return -1;
        List<ClientTrackedMessage> group = serverGroup(sender, plain);
        for (int i = 0; i < group.size(); i++) {
            if (group.get(i).id == messageId) return i;
        }
        return -1;
    }

    public static int activeServerCount(UUID sender, String plain) {
        return serverGroup(sender, plain).size();
    }

    public static boolean hasPendingRegistration(UUID sender, String plain) {
        if (sender == null || plain == null) return false;
        String want = stripEditedBadge(plain);
        for (PendingRegistration p : PENDING) {
            if (!sender.equals(p.sender)) continue;
            if (want.equals(stripEditedBadge(p.plainText))) return true;
        }
        return false;
    }

    public static boolean matchesPlayerLine(GuiMessage gui, String senderName, String plain) {
        if (gui == null || gui.content() == null || senderName == null || senderName.isBlank()
            || plain == null || plain.isBlank()) return false;
        String full = stripFormatting(gui.content().getString());
        String body = extractOwnPlain(full, senderName);
        return body != null && stripEditedBadge(plain).equals(stripEditedBadge(body));
    }

    private static List<ClientTrackedMessage> serverGroup(UUID sender, String plain) {
        String want = stripEditedBadge(plain == null ? "" : plain);
        List<ClientTrackedMessage> group = new ArrayList<>();
        for (ClientTrackedMessage m : BY_ID.values()) {
            if (m == null || m.id <= 0L || m.deleting || isTombstoned(m)) continue;
            if (!sender.equals(m.sender)) continue;
            if (!want.equals(stripEditedBadge(m.plainText))) continue;
            group.add(m);
        }
        group.sort((a, b) -> Long.compare(b.id, a.id));
        return group;
    }

    public record SenderInfo(UUID uuid, String name) {}

    public static Iterable<ClientTrackedMessage> all() {
        return BY_ID.values();
    }

    public static void remove(long id) {
        BY_ID.remove(id);
    }

    public static void applyEdit(long id, String newText) {
        applyEdit(id, newText, (ChatHudEditor.HudPin) null);
    }

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
        if (plain == null || fullChatLine == null) return false;
        String want = stripEditedBadge(stripFormatting(plain).trim());
        String full = stripFormatting(fullChatLine).trim();
        if (want.isEmpty() || full.isEmpty()) return false;
        if (full.equals(want)) return true;

        String line = full;
        int newline = line.lastIndexOf('\n');
        if (newline >= 0 && newline + 1 < line.length()) {
            line = line.substring(newline + 1).trim();
        }
        if (line.equals(want)) return true;

        int gt = line.lastIndexOf('>');
        if (gt >= 0 && gt + 1 < line.length()) {
            String body = line.substring(gt + 1).trim();
            if (body.startsWith(":")) body = body.substring(1).trim();
            return stripEditedBadge(body).equals(want);
        }

        int colon = line.indexOf(':');
        if (colon > 0 && colon + 1 < line.length() && line.indexOf('<') < 0) {
            return stripEditedBadge(line.substring(colon + 1).trim()).equals(want);
        }
        return false;
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
        /** Exact GuiMessage object currently represented by this tracker. */
        public GuiMessage guiRef;
        public boolean deleting;
        public boolean pendingEdit;
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
