package com.bielzinrx.unsend;

import com.bielzinrx.unsend.platform.Platform;
import com.bielzinrx.unsend.server.ChatMessageTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Unsend {
    public static final String MOD_ID = "unsend";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static net.minecraft.server.MinecraftServer server;

    private Unsend() {}

    public static void init() {
        LOGGER.info("[Unsend] Initializing common...");
        ChatMessageTracker.clear();
    }

    public static void setServer(net.minecraft.server.MinecraftServer s) {
        server = s;
        if (s == null) {
            ChatMessageTracker.clear();
        }
    }

    public static net.minecraft.server.MinecraftServer getServer() {
        return server;
    }

    public static void onServerStop() {
        ChatMessageTracker.clear();
        server = null;
    }
}
