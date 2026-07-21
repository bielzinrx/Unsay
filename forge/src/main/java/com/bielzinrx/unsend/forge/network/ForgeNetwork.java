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

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class ForgeNetwork {
    private static final String PROTOCOL = "5";
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
        CHANNEL.registerMessage(id++, SnapshotS2C.class, SnapshotS2C::encode, SnapshotS2C::decode,
            SnapshotS2C::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ResultS2C.class, ResultS2C::encode, ResultS2C::decode,
            ResultS2C::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendDeleteToServer(long messageId, String plainFallback) {
        CHANNEL.sendToServer(new DeleteC2S(messageId, plainFallback));
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

    public static void sendDelete(ServerPlayer target, long messageId, UUID sender, String plainText) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> target), new DeleteS2C(messageId, sender, plainText));
    }

    public static void sendEdit(ServerPlayer target, long messageId, String newText, String oldPlain, UUID sender) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> target), new EditS2C(messageId, newText, oldPlain, sender));
    }

    public static void sendSnapshot(ServerPlayer target, List<Packets.SnapshotEntry> entries) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> target), new SnapshotS2C(entries));
    }

    public static void sendResult(ServerPlayer target, boolean ok, String messageKey) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> target), new ResultS2C(ok, messageKey));
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

    public record DeleteC2S(long messageId, String plainFallback) {
        public static void encode(DeleteC2S msg, FriendlyByteBuf buf) {
            Packets.writeDeleteC2S(buf, msg.messageId, msg.plainFallback);
        }

        public static DeleteC2S decode(FriendlyByteBuf buf) {
            Packets.DeleteC2SPayload p = Packets.readDeleteC2S(buf);
            return new DeleteC2S(p.messageId(), p.plainFallback());
        }

        public static void handle(DeleteC2S msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> {
                ServerPlayer player = c.getSender();
                if (player != null) UnsendServer.onDeleteRequest(player, msg.messageId, msg.plainFallback);
            });
            c.setPacketHandled(true);
        }
    }

    public record DeleteS2C(long messageId, UUID sender, String plainText) {
        public static void encode(DeleteS2C msg, FriendlyByteBuf buf) {
            Packets.writeDeleteS2C(buf, msg.messageId, msg.sender, msg.plainText);
        }

        public static DeleteS2C decode(FriendlyByteBuf buf) {
            Packets.DeleteS2CPayload p = Packets.readDeleteS2C(buf);
            return new DeleteS2C(p.messageId(), p.sender(), p.plainText());
        }

        public static void handle(DeleteS2C msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    UnsendClient.handleDelete(msg.messageId, msg.sender, msg.plainText)));
            ctx.get().setPacketHandled(true);
        }
    }

    public record EditC2S(long messageId, String newText) {
        public static void encode(EditC2S msg, FriendlyByteBuf buf) {
            Packets.writeEditC2S(buf, msg.messageId, msg.newText);
        }

        public static EditC2S decode(FriendlyByteBuf buf) {
            Packets.EditC2SPayload p = Packets.readEditC2S(buf);
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

    public record EditS2C(long messageId, String newText, String oldPlain, UUID sender) {
        public static void encode(EditS2C msg, FriendlyByteBuf buf) {
            Packets.writeEditS2C(buf, msg.messageId, msg.newText, msg.oldPlain, msg.sender);
        }

        public static EditS2C decode(FriendlyByteBuf buf) {
            Packets.EditS2CPayload p = Packets.readEditS2C(buf);
            return new EditS2C(p.messageId(), p.newText(), p.oldPlain(), p.sender());
        }

        public static void handle(EditS2C msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    UnsendClient.handleEdit(msg.messageId, msg.newText, msg.oldPlain, msg.sender)));
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

    public record SnapshotS2C(List<Packets.SnapshotEntry> entries) {
        public static void encode(SnapshotS2C msg, FriendlyByteBuf buf) {
            Packets.writeSnapshot(buf, msg.entries);
        }

        public static SnapshotS2C decode(FriendlyByteBuf buf) {
            return new SnapshotS2C(Packets.readSnapshot(buf).entries());
        }

        public static void handle(SnapshotS2C msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    UnsendClient.handleSnapshot(new Packets.SnapshotPayload(msg.entries))));
            ctx.get().setPacketHandled(true);
        }
    }

    public record ResultS2C(boolean ok, String messageKey) {
        public static void encode(ResultS2C msg, FriendlyByteBuf buf) {
            Packets.writeResult(buf, msg.ok, msg.messageKey);
        }

        public static ResultS2C decode(FriendlyByteBuf buf) {
            Packets.ResultPayload p = Packets.readResult(buf);
            return new ResultS2C(p.ok(), p.messageKey());
        }

        public static void handle(ResultS2C msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    UnsendClient.handleResult(msg.ok, msg.messageKey)));
            ctx.get().setPacketHandled(true);
        }
    }
}
