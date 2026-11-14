package com.jjs.gojo;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/**
 * Exhaustion after Hollow Nuke / 0.2 Domain. While exhausted the player cannot use any ability (checked on the
 * server for every ability and for blocking) and has Nausea. The end time is stored in the world save, so
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
            applyNausea(p, ticks);
            sync(p);
        }
    }

    private static void applyNausea(ServerPlayerEntity p, int ticks) {
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, ticks + 20, 0, false, false, true));
    }

    /** Join / respawn: restore the effect and the HUD if exhaustion is still running. */
    public static void onJoin(ServerPlayerEntity p) {
        int rem = remaining(p.getServer(), p.getUuid());
        PlayerState st = GojoMod.state(p);
        st.exhaustedFlag = rem > 0;
        if (rem > 0) applyNausea(p, rem);
        sync(p);
    }

    /** Called every server tick: notices the moment exhaustion ends. */
    public static void tick(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            PlayerState st = GojoMod.state(p);
            boolean now = isExhausted(p);
            if (st.exhaustedFlag && !now) {
                p.removeStatusEffect(StatusEffects.NAUSEA);
                sync(p);
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
