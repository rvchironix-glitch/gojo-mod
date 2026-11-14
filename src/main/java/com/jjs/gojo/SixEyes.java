package com.jjs.gojo;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Gojo's permanent passive: Six Eyes. Applies only to Sorcerers who picked Gojo.
 *  - Night Vision: kept alive by topping up ONE finite effect once a second (never stacks, survives death,
 *    respawn and rejoin, and fades by itself within seconds if the player stops being Gojo).
 *  - Through-wall vision: the server tells the client it is active; the client outlines nearby living entities
 *    (client-side glow, only for Gojo's own screen).
 */
public final class SixEyes {
    private static final int REFRESH_BELOW = 300;
    private static final int GRANT = 400;

    private SixEyes() {}

    public static boolean active(ServerPlayerEntity p) {
        MinecraftServer s = p.getServer();
        if (s == null || !Characters.isSorcerer(p)) return false;
        return CharacterData.get(s).hero(p.getUuid()) == Heroes.GOJO;
    }

    /** Tops up Night Vision for Gojo; call once a second (and on join / respawn). */
    public static void refresh(ServerPlayerEntity p) {
        if (!active(p)) return;
        StatusEffectInstance cur = p.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (cur == null || cur.getDuration() < REFRESH_BELOW) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, GRANT, 0, false, false, false));
        }
    }

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) refresh(p);
    }

    /** Called when the player's character changes: drop our Night Vision if they are no longer Gojo. */
    public static void onHeroChange(ServerPlayerEntity p) {
        if (!active(p)) {
            StatusEffectInstance cur = p.getStatusEffect(StatusEffects.NIGHT_VISION);
            if (cur != null && cur.getAmplifier() == 0 && cur.getDuration() <= GRANT && !cur.shouldShowIcon()) {
                p.removeStatusEffect(StatusEffects.NIGHT_VISION);
            }
        } else {
            refresh(p);
        }
        sync(p);
    }

    public static void sync(ServerPlayerEntity p) {
        if (!ServerPlayNetworking.canSend(p, GojoNet.PASSIVE)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(active(p));
        buf.writeInt(Math.max(4, GojoConfig.get().sixEyesRange));
        ServerPlayNetworking.send(p, GojoNet.PASSIVE, buf);
    }
}
