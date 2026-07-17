package com.bielzinrx.unsend.mixin;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
    @Accessor("allMessages")
    List<GuiMessage> unsend$getAllMessages();

    @Accessor("trimmedMessages")
    List<GuiMessage.Line> unsend$getTrimmedMessages();

    @Accessor("chatScrollbarPos")
    int unsend$getChatScrollbarPos();

    @Invoker("getLineHeight")
    int unsend$getLineHeight();

    @Invoker("refreshTrimmedMessage")
    void unsend$refreshTrimmedMessage();
}
