package com.bielzinrx.unsend.mixin;

import com.bielzinrx.unsend.client.ClientMessageIndex;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V",
        at = @At("TAIL"))
    private void unsend$onAddMessage(Component content, MessageSignature signature, int addedTime,
                                          GuiMessageTag tag, boolean onlyRefreshFiltered, CallbackInfo ci) {
        if (onlyRefreshFiltered) return;
        ClientMessageIndex.onChatMessageAdded(new GuiMessage(addedTime, content, signature, tag));
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
        at = @At("TAIL"))
    private void unsend$onAddMessagePublic(Component content, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {

    }
}
