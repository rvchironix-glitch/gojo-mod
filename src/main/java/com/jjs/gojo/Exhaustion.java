package com.jjs.gojo;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/**
 * Exhaustion after Hollow Nuke / 0.2 Domain. While exhausted the player cannot use any ability (checked on the
 * server for every ability and for blocking). No potion effects are applied. The end time is stored in the world save, so
 * relogging, dying or restarting cannot skip it, and it always expires on its own.
 */
public final class Exhaustion {
    private Exhaustion() {}

    private static long worldTime(MinecraftServer s) {
        return s.getOverworld().getTime();
    }

    public static int remaining(MinecraftServer server, UUID id) {
        return (int) Math.max(0, CharacterData.get(server).exhaustedUntil(id) - worldTime(server));
    }

    public static boolean isExhausted(ServerPlayerEntity p) {
        return remaining(p.getServer(), p.getUuid()) > 0;
    }

    /** Starts exhaustion for the configured duration. Works for offline players too (no effects then). */
    public static void apply(MinecraftServer server, UUID id) {
        int ticks = Math.max(1, GojoConfig.get().exhaustionTicks);
        CharacterData.get(server).setExhaustedUntil(id, worldTime(server) + ticks);
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
        if (p != null) {
            GojoMod.state(p).exhaustedFlag = true;
            sync(p);
        }
    }

    /**
     * RCT during burnout: shortens the timer by {@code ticks} (never below zero). At zero the restrictions are
     * lifted immediately and the HUD is updated. Returns the new remaining ticks.
     */
    public static int reduce(MinecraftServer server, UUID id, int ticks) {
        long now = worldTime(server);
        long until = CharacterData.get(server).exhaustedUntil(id);
        long left = Math.max(0, until - now);
        long newLeft = Math.max(0, left - ticks);
        CharacterData.get(server).setExhaustedUntil(id, newLeft == 0 ? 0 : now + newLeft);
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
        if (p != null) {
            GojoMod.state(p).exhaustedFlag = newLeft > 0;
            sync(p);
        }
        return (int) newLeft;
    }

    /** Join / respawn: restore the effect and the HUD if exhaustion is still running. */
    public static void onJoin(ServerPlayerEntity p) {
        int rem = remaining(p.getServer(), p.getUuid());
        PlayerState st = GojoMod.state(p);
        st.exhaustedFlag = rem > 0;
        sync(p);
    }

    /** Called every server tick: notices the moment exhaustion ends. */
    public static void tick(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            PlayerState st = GojoMod.state(p);
            boolean now = isExhausted(p);
            if (st.exhaustedFlag && !now) {
                sync(p); // timer ran out: tell the HUD (no potion effects to clean up)
            }
            st.exhaustedFlag = now;
        }
    }

    public static void sync(ServerPlayerEntity p) {
        if (!ServerPlayNetworking.canSend(p, GojoNet.EXHAUST)) return;
        int rem = remaining(p.getServer(), p.getUuid());
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(rem);
        buf.writeInt(Math.max(1, GojoConfig.get().exhaustionTicks));
        ServerPlayNetworking.send(p, GojoNet.EXHAUST, buf);
    }
}
