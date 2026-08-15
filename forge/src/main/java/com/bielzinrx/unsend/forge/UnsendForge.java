package com.bielzinrx.unsend.forge;

import com.bielzinrx.unsend.Unsend;
import com.bielzinrx.unsend.forge.network.ForgeNetwork;
import com.bielzinrx.unsend.forge.platform.ForgePlatformHelper;
import com.bielzinrx.unsend.platform.Platform;
import com.bielzinrx.unsend.server.UnsendServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Unsend.MOD_ID)
public final class UnsendForge {
    public UnsendForge(FMLJavaModLoadingContext loadingContext) {
        Platform.bootstrap(new ForgePlatformHelper());
        IEventBus modBus = loadingContext.getModEventBus();
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            Unsend.init();
            ForgeNetwork.init();
        });
    }

    private void clientSetup(FMLClientSetupEvent event) {

    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        Unsend.setServer(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        Unsend.onServerStop();
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        UnsendServer.onPlayerChat(event.getPlayer(), event.getRawText());
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UnsendServer.onPlayerJoin(player);
        }
    }
}
