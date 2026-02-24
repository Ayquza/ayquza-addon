package com.ayquza.addon.modules;

import com.ayquza.addon.AyquzaAddon;
import com.mojang.authlib.GameProfile;


import io.netty.channel.ChannelFuture;
import net.minecraft.network.NetworkingBackend;
import net.minecraft.network.DisconnectionInfo;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AntiPacketKick;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.listener.ClientLoginPacketListener;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.network.packet.s2c.common.CookieRequestS2CPacket;
import net.minecraft.network.packet.s2c.login.*;
import net.minecraft.text.Text;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


public class CrackedKickModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> ban = sgGeneral.add(new BoolSetting.Builder()
        .name("ban")
        .description("Whether to continuously kick joining players.")
        .defaultValue(false)
        .build()
    );


    private static final HashSet<GameProfile> processingPlayers = new HashSet<>();

    public CrackedKickModule() {
        super(AyquzaAddon.CATEGORY, "cracked-kick", "Kicks everyone on a cracked server.");
    }

    @Override
    public void onActivate() {
        AntiPacketKick apk = Modules.get().get(AntiPacketKick.class);
        if (apk.isActive() && apk.logExceptions.get())
            info("Disable \"Log Exceptions\" in Anti Packet Kick if you don't want to get spammed!");
    }

    @Override
    public void onDeactivate() {
        processingPlayers.clear();
    }

    @EventHandler
    private void postTick(TickEvent.Post event) {
        if (!Utils.canUpdate()) return;
        if (mc.isInSingleplayer()) {
            warning("Not available in singleplayer!");
            toggle();
            return;
        }

        Collection<PlayerListEntry> entries = mc.getNetworkHandler().getPlayerList();
        for (PlayerListEntry entry : entries) {
            kick(this, entry);
        }

        if (!ban.get()) toggle();
    }

    public static void kick(CrackedKickModule module, PlayerListEntry entry) {
        MinecraftClient mc = MinecraftClient.getInstance();
        GameProfile profile = entry.getProfile();

        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (profile.equals(mc.player.getGameProfile())) return;
        if (processingPlayers.contains(profile)) return;

        processingPlayers.add(profile);

        InetSocketAddress address = (InetSocketAddress) mc.getNetworkHandler().getConnection().getAddress();
        ClientConnection connection = new ClientConnection(NetworkSide.CLIENTBOUND);

        CompletableFuture.runAsync(() -> {
            ChannelFuture future = ClientConnection.connect(address, NetworkingBackend.remote(mc.options.shouldUseNativeTransport()), connection);
            future.awaitUninterruptibly(5000, TimeUnit.MILLISECONDS);

            if (!future.isSuccess()) {
                processingPlayers.remove(profile);
                connection.disconnect(Text.literal("disconnect"));
                return;
            }

            connection.connect(address.getHostName(), address.getPort(), new ClientLoginPacketListener() {
                @Override
                public void onCookieRequest(CookieRequestS2CPacket packet) {}

                @Override
                public void onHello(LoginHelloS2CPacket packet) {}

                @Override
                public void onSuccess(LoginSuccessS2CPacket packet) {}

                @Override
                public void onDisconnect(LoginDisconnectS2CPacket packet) {}

                @Override
                public void onCompression(LoginCompressionS2CPacket packet) {}

                @Override
                public void onQueryRequest(LoginQueryRequestS2CPacket packet) {}

                @Override
                public void onDisconnected(DisconnectionInfo info) {
                    processingPlayers.remove(profile);
                }

                @Override
                public boolean isConnectionOpen() {
                    return connection.isOpen();
                }
            });

            connection.send(new LoginHelloC2SPacket(profile.name(), profile.id()));

            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {}

            processingPlayers.remove(profile);
            connection.disconnect(Text.literal("disconnect"));
        });
    }
}
