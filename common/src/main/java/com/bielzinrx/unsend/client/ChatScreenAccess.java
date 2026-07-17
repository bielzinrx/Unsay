package com.bielzinrx.unsend.client;

import com.bielzinrx.unsend.mixin.ChatScreenAccessor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;

public final class ChatScreenAccess {
    private ChatScreenAccess() {}

    public static EditBox getInput(ChatScreen screen) {
        if (screen == null) return null;
        return ((ChatScreenAccessor) screen).unsend$getInput();
    }
}
