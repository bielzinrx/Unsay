package com.bielzinrx.unsend.network;

import net.minecraft.resources.ResourceLocation;

public final class PacketIds {
    public static final ResourceLocation REGISTER = new ResourceLocation("unsend", "register");
    public static final ResourceLocation DELETE_C2S = new ResourceLocation("unsend", "delete_c2s");
    public static final ResourceLocation DELETE_S2C = new ResourceLocation("unsend", "delete_s2c");
    public static final ResourceLocation EDIT_C2S = new ResourceLocation("unsend", "edit_c2s");
    public static final ResourceLocation EDIT_S2C = new ResourceLocation("unsend", "edit_s2c");
    public static final ResourceLocation REPLY_C2S = new ResourceLocation("unsend", "reply_c2s");

    private PacketIds() {}
}
