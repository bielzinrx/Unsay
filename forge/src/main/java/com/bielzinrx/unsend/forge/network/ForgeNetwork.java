package com.bielzinrx.unsend.forge.network;

import com.bielzinrx.unsend.Unsend;
import com.bielzinrx.unsend.client.UnsendClient;
import com.bielzinrx.unsend.network.Packets;
import com.bielzinrx.unsend.server.UnsendServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class ForgeNetwork {
    private static final String PROTOCOL = "3";
    private static SimpleChannel CHANNEL;
    private static int id;

    private ForgeNetwork() {}

    public static void init() {
        id = 0;
        CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(Unsend.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

        CHANNEL.registerMessage(id++, RegisterMessage.class, RegisterMessage::encode, RegisterMessage::decode,
            RegisterMessage::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, DeleteC2S.class, DeleteC2S::encode, DeleteC2S::decode,
            DeleteC2S::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, DeleteS2C.class, DeleteS2C::encode, DeleteS2C::decode,
            DeleteS2C::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, EditC2S.class, EditC2S::encode, EditC2S::decode,
            EditC2S::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, EditS2C.class, EditS2C::encode, EditS2C::decode,
            EditS2C::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ReplyC2S.class, ReplyC2S::encode, ReplyC2S::decode,
            ReplyC2S::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void sendDeleteToServer(long messageId) {
        CHANNEL.sendToServer(new DeleteC2S(messageId));
    }

    public static void sendEditToServer(long messageId, String newText) {
        CHANNEL.sendToServer(new EditC2S(messageId, newText));
    }

    public static void sendReplyToServer(long targetId, String text, String targetName, String targetPreview) {
        CHANNEL.sendToServer(new ReplyC2S(targetId, text, targetName, targetPreview));
    }

    public static void sendRegister(ServerPlayer target, long messageId, UUID sender, String senderName, String plainText) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> target),
            new RegisterMessage(messageId, sender, senderName, plainText));
    }

    public static void sendDelete(ServerPlayer target, long messageId) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> target), new DeleteS2C(messageId));
    }

    public static void sendEdit(ServerPlayer target, long messageId, String newText) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> target), new EditS2C(messageId, newText));
    }

    public record RegisterMessage(long messageId, UUID sender, String senderName, String plainText) {
        public static void encode(RegisterMessage msg, FriendlyByteBuf buf) {
            Packets.writeRegister(buf, msg.messageId, msg.sender, msg.senderName, msg.plainText);
        }

        public static RegisterMessage decode(FriendlyByteBuf buf) {
            Packets.RegisterPayload p = Packets.readRegister(buf);
            return new RegisterMessage(p.messageId(), p.sender(), p.senderName(), p.plainText());
        }

        public static void handle(RegisterMessage msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    UnsendClient.handleRegister(msg.messageId, msg.sender, msg.senderName, msg.plainText)));
            ctx.get().setPacketHandled(true);
        }
    }

    public record DeleteC2S(long messageId) {
        public static void encode(DeleteC2S msg, FriendlyByteBuf buf) {
            Packets.writeDelete(buf, msg.messageId);
        }

        public static DeleteC2S decode(FriendlyByteBuf buf) {
            return new DeleteC2S(Packets.readDelete(buf));
        }

        public static void handle(DeleteC2S msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> {
                ServerPlayer player = c.getSender();
                if (player != null) UnsendServer.onDeleteRequest(player, msg.messageId);
            });
            c.setPacketHandled(true);
        }
    }

    public record DeleteS2C(long messageId) {
        public static void encode(DeleteS2C msg, FriendlyByteBuf buf) {
            Packets.writeDelete(buf, msg.messageId);
        }

        public static DeleteS2C decode(FriendlyByteBuf buf) {
            return new DeleteS2C(Packets.readDelete(buf));
        }

        public static void handle(DeleteS2C msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> UnsendClient.handleDelete(msg.messageId)));
            ctx.get().setPacketHandled(true);
        }
    }

    public record EditC2S(long messageId, String newText) {
        public static void encode(EditC2S msg, FriendlyByteBuf buf) {
            Packets.writeEdit(buf, msg.messageId, msg.newText);
        }

        public static EditC2S decode(FriendlyByteBuf buf) {
            Packets.EditPayload p = Packets.readEdit(buf);
            return new EditC2S(p.messageId(), p.newText());
        }

        public static void handle(EditC2S msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> {
                ServerPlayer player = c.getSender();
                if (player != null) UnsendServer.onEditRequest(player, msg.messageId, msg.newText);
            });
            c.setPacketHandled(true);
        }
    }

    public record EditS2C(long messageId, String newText) {
        public static void encode(EditS2C msg, FriendlyByteBuf buf) {
            Packets.writeEdit(buf, msg.messageId, msg.newText);
        }

        public static EditS2C decode(FriendlyByteBuf buf) {
            Packets.EditPayload p = Packets.readEdit(buf);
            return new EditS2C(p.messageId(), p.newText());
        }

        public static void handle(EditS2C msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    UnsendClient.handleEdit(msg.messageId, msg.newText)));
            ctx.get().setPacketHandled(true);
        }
    }

    public record ReplyC2S(long targetId, String text, String targetName, String targetPreview) {
        public static void encode(ReplyC2S msg, FriendlyByteBuf buf) {
            Packets.writeReply(buf, msg.targetId, msg.text, msg.targetName, msg.targetPreview);
        }

        public static ReplyC2S decode(FriendlyByteBuf buf) {
            Packets.ReplyPayload p = Packets.readReply(buf);
            return new ReplyC2S(p.targetId(), p.text(), p.targetName(), p.targetPreview());
        }

        public static void handle(ReplyC2S msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> {
                ServerPlayer player = c.getSender();
                if (player != null) {
                    UnsendServer.onReplyRequest(player, msg.targetId, msg.text, msg.targetName, msg.targetPreview);
                }
            });
            c.setPacketHandled(true);
        }
    }
}
