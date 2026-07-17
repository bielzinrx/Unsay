package com.bielzinrx.unsend.mixin;

import com.bielzinrx.unsend.client.UnsendHud;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void unsend$render(PoseStack poseStack, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        UnsendHud.onChatScreenRender((ChatScreen) (Object) this, poseStack, mouseX, mouseY, partialTick);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void unsend$click(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (UnsendHud.onChatScreenClick((ChatScreen) (Object) this, mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void unsend$key(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (UnsendHud.onKeyPressed((ChatScreen) (Object) this, keyCode)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void unsend$removed(CallbackInfo ci) {
        UnsendHud.clearSelection();
    }

    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void unsend$send(String message, boolean addToRecent, CallbackInfoReturnable<Boolean> cir) {
        if (UnsendHud.onHandleChatInput(message)) {
            if (addToRecent && message != null && !message.isBlank()) {
                net.minecraft.client.Minecraft.getInstance().gui.getChat().addRecentChat(message);
            }
            cir.setReturnValue(true);
        }
    }
}
