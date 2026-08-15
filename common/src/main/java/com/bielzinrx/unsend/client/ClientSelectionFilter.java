package com.bielzinrx.unsend.client;

import com.bielzinrx.unsend.client.ClientMessageIndex.ClientTrackedMessage;
import com.bielzinrx.unsend.client.ClientMessageIndex.SenderInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/**
 * Client-side scope for message selection. Permission remains server-authoritative;
 * this class only decides which messages are eligible to enter a local selection.
 */
public final class ClientSelectionFilter {
    public enum Mode { MINE, OTHERS, PLAYER, ALL }

    private static Mode mode = Mode.MINE;
    private static UUID playerUuid;
    private static String playerName = "";

    private ClientSelectionFilter() {}

    public static synchronized void reset() {
        mode = Mode.MINE;
        playerUuid = null;
        playerName = "";
    }

    public static synchronized Mode mode() {
        return mode;
    }

    public static synchronized UUID playerUuid() {
        return playerUuid;
    }

    public static synchronized String playerName() {
        return playerName;
    }

    public static synchronized boolean matches(ClientTrackedMessage tracked, Minecraft mc) {
        if (tracked == null || mc == null || mc.player == null) return false;
        UUID self = mc.player.getUUID();
        boolean op = mc.player.hasPermissions(2);
        return matchesScope(mode, playerUuid, self, op, tracked.sender);
    }

    /** Pure selection policy kept independent from Minecraft so permission boundaries are testable. */
    static boolean matchesScope(Mode requested, UUID scopedPlayer, UUID self,
                                boolean operator, UUID sender) {
        if (self == null || sender == null) return false;
        boolean own = sender.equals(self);

        // Ordinary players never gain a broader client selection scope, even if stale UI state
        // still contains an operator-only mode after a permission change.
        if (!operator) return own;

        Mode effective = requested == null ? Mode.MINE : requested;
        return switch (effective) {
            case MINE -> own;
            case OTHERS -> !own;
            case PLAYER -> scopedPlayer != null && scopedPlayer.equals(sender);
            case ALL -> true;
        };
    }

    public static synchronized boolean canSelect(ClientTrackedMessage tracked, Minecraft mc) {
        if (tracked == null || mc == null || mc.player == null) return false;
        if (!matches(tracked, mc)) return false;
        return tracked.isOwnedBy(mc.player.getUUID()) || mc.player.hasPermissions(2);
    }

    public static synchronized void setMine() {
        apply(Mode.MINE, null, "");
    }

    public static synchronized void setOthers() {
        apply(Mode.OTHERS, null, "");
    }

    public static synchronized void setAll() {
        apply(Mode.ALL, null, "");
    }

    public static synchronized void setPlayer(UUID uuid, String name) {
        if (uuid == null) {
            setMine();
            return;
        }
        apply(Mode.PLAYER, uuid, name == null ? "" : name);
    }

    private static void apply(Mode requested, UUID uuid, String name) {
        Minecraft mc = Minecraft.getInstance();
        boolean op = mc != null && mc.player != null && mc.player.hasPermissions(2);
        if (!op && requested != Mode.MINE) {
            requested = Mode.MINE;
            uuid = null;
            name = "";
        }
        mode = requested;
        playerUuid = requested == Mode.PLAYER ? uuid : null;
        playerName = requested == Mode.PLAYER ? (name == null ? "" : name) : "";
        ClientBulkDelete.revalidateSelection();
    }

    public static synchronized Component label() {
        return switch (mode) {
            case MINE -> Component.translatable("unsend.filter.mine");
            case OTHERS -> Component.translatable("unsend.filter.others");
            case ALL -> Component.translatable("unsend.filter.all");
            case PLAYER -> Component.translatable("unsend.filter.player",
                playerName == null || playerName.isBlank() ? "?" : playerName);
        };
    }

    public static List<SenderInfo> players() {
        return ClientMessageIndex.knownSenders();
    }
}
