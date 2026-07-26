package com.bielzinrx.unsend.client;

import com.bielzinrx.unsend.client.ClientMessageIndex.ClientTrackedMessage;
import com.bielzinrx.unsend.mixin.ChatComponentAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.MessageSignature;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatHudEditor {

    private static final Pattern PREFIX = Pattern.compile("^(<[^>]+>\\s*|[^:]{1,32}:\\s*)(.*)$", Pattern.DOTALL);

    private ChatHudEditor() {}

    public static final class HudPin {
        public final int rank;
        public final int addedTime;
        public final MessageSignature signature;
        public final String fullLine;
        public final String plain;

        public HudPin(int rank, int addedTime, MessageSignature signature, String fullLine, String plain) {
            this.rank = rank;
            this.addedTime = addedTime;
            this.signature = signature;
            this.fullLine = fullLine;
            this.plain = plain == null ? "" : plain;
        }

        public boolean isValid() {
            return rank >= 0 && plain != null && !plain.isEmpty();
        }
    }

    public static final class HudOwnLine {
        public final int allIndex;
        public final GuiMessage gui;
        public final ClientTrackedMessage tracked;
        public final String plain;
        public final String fullLine;

        public HudOwnLine(int allIndex, GuiMessage gui, ClientTrackedMessage tracked,
                          String plain, String fullLine) {
            this.allIndex = allIndex;
            this.gui = gui;
            this.tracked = tracked;
            this.plain = plain;
            this.fullLine = fullLine;
        }
    }

    public static void applyEdit(long messageId, String newText) {
        ClientTrackedMessage tracked = ClientMessageIndex.get(messageId);
        if (tracked == null) return;
        applyEdit(tracked, newText, null);
    }

    public static void applyEdit(ClientTrackedMessage tracked, String newText,
                                 int forcedRank, int forcedAddedTime, MessageSignature forcedSig) {
        HudPin pin = null;
        if (forcedRank >= 0 || forcedAddedTime != Integer.MIN_VALUE || forcedSig != null) {
            String plain = tracked != null && tracked.plainText != null ? tracked.plainText : "";
            pin = new HudPin(forcedRank, forcedAddedTime, forcedSig, null, plain);
        }
        applyEdit(tracked, newText, pin);
    }

    public static void applyEdit(ClientTrackedMessage tracked, String newText, HudPin pin) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null || tracked == null || tracked.deleting) {
            return;
        }

        ChatComponent chat = mc.gui.getChat();
        ChatComponentAccessor acc = (ChatComponentAccessor) chat;
        List<GuiMessage> all = acc.unsend$getAllMessages();
        if (all == null) return;

        String safe = newText == null ? "" : newText;
        if (safe.isBlank()) return;

        String matchPlain = pin != null && pin.plain != null && !pin.plain.isEmpty()
            ? pin.plain.trim()
            : (tracked.plainText == null ? "" : tracked.plainText.trim());

        int idx = findBestMatchIndex(all, tracked, matchPlain, pin);
        if (idx < 0) return;

        GuiMessage m = all.get(idx);
        String badge = Component.translatable("unsend.badge.edited").getString();

        MutableComponent updated = rebuildLine(m.content(), matchPlain, safe);
        updated.append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY));
        updated.append(Component.literal(badge).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));

        GuiMessage replacement = new GuiMessage(m.addedTime(), updated, m.headerSignature(), m.tag());
        all.set(idx, replacement);
        tracked.displayContent = updated;
        tracked.plainText = ClientMessageIndex.stripEditedBadge(safe);
        tracked.edited = true;
        tracked.signature = m.headerSignature();
        tracked.addedTime = m.addedTime();

        acc.unsend$refreshTrimmedMessage();
    }

    public static HudPin capturePin(ClientTrackedMessage tracked) {
        if (tracked == null) return new HudPin(-1, Integer.MIN_VALUE, null, null, "");
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.gui == null) {
            int fallback = ClientMessageIndex.rankAmongSamePlain(tracked);
            return new HudPin(fallback, tracked.addedTime, tracked.signature, null, tracked.plainText);
        }

        ChatComponentAccessor acc = (ChatComponentAccessor) mc.gui.getChat();
        List<GuiMessage> all = acc.unsend$getAllMessages();
        if (all == null || all.isEmpty()) {
            int fallback = ClientMessageIndex.rankAmongSamePlain(tracked);
            return new HudPin(fallback, tracked.addedTime, tracked.signature, null, tracked.plainText);
        }

        String plain = tracked.plainText == null ? "" : tracked.plainText.trim();
        String selfName = mc.player.getGameProfile().getName();
        UUID self = mc.player.getUUID();

        List<Integer> ownSame = listOwnSamePlainIndices(all, plain, self, selfName);
        if (ownSame.isEmpty()) {
            int fallback = ClientMessageIndex.rankAmongSamePlain(tracked);
            return new HudPin(fallback, tracked.addedTime, tracked.signature, null, plain);
        }

        int bestRank = -1;
        int bestScore = -1;
        for (int r = 0; r < ownSame.size(); r++) {
            GuiMessage g = all.get(ownSame.get(r));
            int score = scoreGuiAgainstTracked(g, tracked, self, selfName);
            if (score > bestScore) {
                bestScore = score;
                bestRank = r;
            }
        }

        if (bestScore <= 0) {
            int byId = ClientMessageIndex.rankAmongSamePlain(tracked);
            if (byId >= 0 && byId < ownSame.size()) bestRank = byId;
        }
        if (bestRank < 0) bestRank = 0;

        GuiMessage g = all.get(ownSame.get(bestRank));
        return new HudPin(bestRank, g.addedTime(), g.headerSignature(), fullOf(g), plain);
    }

    public static List<HudOwnLine> listOwnHudLines() {
        List<HudOwnLine> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.gui == null) return out;

        ChatComponentAccessor acc = (ChatComponentAccessor) mc.gui.getChat();
        List<GuiMessage> all = acc.unsend$getAllMessages();
        if (all == null) return out;

        UUID self = mc.player.getUUID();
        String selfName = mc.player.getGameProfile().getName();

        for (int i = 0; i < all.size(); i++) {
            GuiMessage g = all.get(i);
            String full = fullOf(g);
            String plain = ClientMessageIndex.extractOwnPlain(full, selfName);
            if (plain == null) continue;

            ClientTrackedMessage tr = ClientMessageIndex.findOrCreateForGuiMessage(g, self, selfName);
            if (tr == null || tr.deleting || ClientMessageIndex.isTombstoned(tr)) continue;
            if (!tr.isOwnedBy(self) && ClientMessageIndex.extractOwnPlain(full, selfName) == null) continue;

            out.add(new HudOwnLine(i, g, tr, plain, full));
        }
        return out;
    }

    public static int rankOnHudAmongSamePlain(ClientTrackedMessage tracked) {
        HudPin pin = capturePin(tracked);
        return pin.rank;
    }

    private static int findBestMatchIndex(List<GuiMessage> all, ClientTrackedMessage tracked,
                                         String plain, HudPin pin) {
        Minecraft mc = Minecraft.getInstance();
        String selfName = mc != null && mc.player != null ? mc.player.getGameProfile().getName() : null;
        UUID self = mc != null && mc.player != null ? mc.player.getUUID() : null;

        int forcedRank = pin != null ? pin.rank : -1;
        int forcedTick = pin != null ? pin.addedTime : Integer.MIN_VALUE;
        MessageSignature forcedSig = pin != null ? pin.signature : null;
        String pinFull = pin != null ? pin.fullLine : null;

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
        if (tracked != null && tracked.signature != null) {
            int only = -1;
            int hits = 0;
            for (int i = 0; i < all.size(); i++) {
                if (tracked.signature.equals(all.get(i).headerSignature())) {
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

        if (plain == null || plain.isEmpty()) return -1;

        List<Integer> ownSame = listOwnSamePlainIndices(all, plain, self, selfName);
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

        int rank = ClientMessageIndex.rankAmongSamePlain(tracked);
        if (rank >= 0 && rank < ownSame.size()) return ownSame.get(rank);

        if (forcedRank < 0 && forcedSig == null && (tracked == null || tracked.signature == null)) {
            return -1;
        }

        int best = ownSame.get(0);
        int bestDiff = tick == Integer.MIN_VALUE
            ? Integer.MAX_VALUE
            : Math.abs(all.get(best).addedTime() - tick);
        for (int i = 1; i < ownSame.size(); i++) {
            int ci = ownSame.get(i);
            int diff = tick == Integer.MIN_VALUE
                ? Integer.MAX_VALUE
                : Math.abs(all.get(ci).addedTime() - tick);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = ci;
            }
        }
        return best;
    }

    private static List<Integer> listOwnSamePlainIndices(List<GuiMessage> all, String plain,
                                                         UUID self, String selfName) {
        List<Integer> out = new ArrayList<>();
        if (plain == null || plain.isEmpty() || all == null) return out;
        for (int i = 0; i < all.size(); i++) {
            String full = fullOf(all.get(i));
            if (!bodyEquals(full, plain)) continue;
            if (selfName != null) {
                String own = ClientMessageIndex.extractOwnPlain(full, selfName);
                if (own == null || !plain.equals(own.trim())) {
                    if (!looksOwned(all.get(i), self, selfName)) continue;
                }
            }
            out.add(i);
        }
        return out;
    }

    private static boolean looksOwned(GuiMessage g, UUID self, String selfName) {
        if (g == null) return false;
        if (selfName != null && ClientMessageIndex.extractOwnPlain(fullOf(g), selfName) != null) {
            return true;
        }
        if (self == null) return false;
        ClientTrackedMessage t = ClientMessageIndex.findBySignature(g.headerSignature());
        if (t != null && t.isOwnedBy(self)) return true;
        t = ClientMessageIndex.findByAddedTimeAndContent(g.addedTime(), g.content());
        return t != null && t.isOwnedBy(self);
    }

    private static int scoreGuiAgainstTracked(GuiMessage g, ClientTrackedMessage tracked,
                                              UUID self, String selfName) {
        if (g == null || tracked == null) return 0;
        int score = 0;
        if (tracked.signature != null && tracked.signature.equals(g.headerSignature())) score += 1000;
        if (tracked.addedTime == g.addedTime()) score += 100;
        if (tracked.displayContent != null) {
            String want = ClientMessageIndex.stripFormatting(tracked.displayContent.getString()).trim();
            if (!want.isEmpty() && want.equals(fullOf(g))) score += 50;
        }
        ClientTrackedMessage linked = ClientMessageIndex.findBySignature(g.headerSignature());
        if (linked == null) {
            linked = ClientMessageIndex.findByAddedTimeAndContent(g.addedTime(), g.content());
        }
        if (linked != null && linked.id == tracked.id) score += 500;
        if (linked != null && isSameLine(linked, tracked)) score += 200;
        return score;
    }

    private static boolean isSameLine(ClientTrackedMessage a, ClientTrackedMessage b) {
        if (a == null || b == null) return false;
        if (a.id == b.id) return true;
        if (a.signature != null && a.signature.equals(b.signature)) return true;
        return a.addedTime == b.addedTime
            && a.plainText != null && a.plainText.equals(b.plainText)
            && ((a.id < 0) != (b.id < 0));
    }

    static String fullOf(GuiMessage m) {
        if (m == null || m.content() == null) return "";
        return ClientMessageIndex.stripFormatting(m.content().getString()).trim();
    }

    static boolean bodyEquals(String full, String plain) {
        if (full == null || plain == null || plain.isEmpty()) return false;
        if (full.equals(plain)) return true;

        String line = full;
        int nl = full.lastIndexOf('\n');
        if (nl >= 0 && nl + 1 < full.length()) {
            line = full.substring(nl + 1).trim();
        }

        int gt = line.lastIndexOf('>');
        if (gt >= 0 && gt + 1 < line.length()) {
            String body = line.substring(gt + 1).trim();
            if (body.startsWith(":")) body = body.substring(1).trim();
            int badge = body.lastIndexOf(" (");
            if (badge > 0) body = body.substring(0, badge).trim();
            return body.equals(plain);
        }

        int colon = line.indexOf(':');
        if (colon > 0 && line.indexOf('<') < 0) {
            return line.substring(colon + 1).trim().equals(plain);
        }
        return false;
    }

    private static MutableComponent rebuildLine(Component original, String oldPlain, String newPlain) {
        String stripped = ClientMessageIndex.stripFormatting(original == null ? "" : original.getString());
        stripped = ClientMessageIndex.stripEditedBadge(stripped);

        int nl = stripped.indexOf('\n');
        if (nl >= 0 && stripped.contains("↳")) {
            String cite = stripped.substring(0, nl);
            String bodyLine = ClientMessageIndex.stripEditedBadge(stripped.substring(nl + 1).trim());
            MutableComponent out = Component.literal(cite)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
            out.append(Component.literal("\n"));
            Matcher bm = PREFIX.matcher(bodyLine);
            if (bm.matches()) {
                out.append(Component.literal(bm.group(1) + newPlain));
            } else if (oldPlain != null && !oldPlain.isEmpty() && bodyLine.contains(oldPlain)) {
                int idx = bodyLine.lastIndexOf(oldPlain);
                out.append(Component.literal(
                    bodyLine.substring(0, idx) + newPlain + bodyLine.substring(idx + oldPlain.length())));
            } else {
                out.append(Component.literal(newPlain));
            }
            return out;
        }

        Matcher m = PREFIX.matcher(stripped);
        if (m.matches()) {
            String prefix = m.group(1);
            return Component.literal(prefix + newPlain);
        }

        if (oldPlain != null && oldPlain.length() >= 1 && stripped.contains(oldPlain)) {
            int idx = stripped.lastIndexOf(oldPlain);
            String head = stripped.substring(0, idx);
            String tail = stripped.substring(idx + oldPlain.length());
            return Component.literal(head + newPlain + ClientMessageIndex.stripEditedBadge(tail));
        }

        return Component.literal(newPlain);
    }
}
