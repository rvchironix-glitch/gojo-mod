package com.jjs.gojo;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.UUID;

/** Server-authoritative character (Human / Sorcerer) logic. */
public final class Characters {
    public static final int NONE = 0;
    public static final int HUMAN = 1;
    public static final int SORCERER = 2;

    /** Fixed id so the +20 max health modifier can never be stacked twice. */
    private static final UUID HEALTH_ID = UUID.fromString("5b1f3a52-8c1e-4f0a-9d5e-3c2a7e6b1d90");
    private static final double BONUS_HEALTH = 20.0;

    private Characters() {}

    public static int type(ServerPlayerEntity p) {
        MinecraftServer s = p.getServer();
        return s == null ? NONE : CharacterData.get(s).type(p.getUuid());
    }

    public static boolean isSorcerer(ServerPlayerEntity p) {
        return type(p) == SORCERER;
    }

    /** Idempotent: adds the bonus for sorcerers, removes it for everyone else. */
    public static void applyHealth(ServerPlayerEntity p) {
        EntityAttributeInstance inst = p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (inst == null) return;
        boolean sorcerer = isSorcerer(p);
        EntityAttributeModifier existing = inst.getModifier(HEALTH_ID);
        if (sorcerer && existing == null) {
            inst.addPersistentModifier(new EntityAttributeModifier(
                    HEALTH_ID, "Gojo sorcerer bonus health", BONUS_HEALTH, EntityAttributeModifier.Operation.ADDITION));
        } else if (!sorcerer && existing != null) {
            inst.removeModifier(HEALTH_ID);
            if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        }
    }

    public static void choose(MinecraftServer server, ServerPlayerEntity player, int wanted) {
        if (wanted != HUMAN && wanted != SORCERER) return;
        CharacterData data = CharacterData.get(server);
        if (!data.setTypeIfUnset(player.getUuid(), wanted)) {
            GojoMod.LOGGER.warn("{} tried to change their character choice; ignored", player.getName().getString());
            sync(player);
            return;
        }
        applyHealth(player);
        if (wanted == SORCERER) player.setHealth(player.getMaxHealth());
        sync(player);
        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, wanted == SORCERER ? 1.6f : 1.0f);
    }

    /** Pick or switch the sorcerer character. Sorcerers only, never while transformed. */
    public static void chooseHero(MinecraftServer server, ServerPlayerEntity player, int hero) {
        if (hero != Heroes.GOJO && hero != Heroes.YUJI) return;
        if (!isSorcerer(player)) {
            GojoMod.LOGGER.warn("{} tried to pick a sorcerer character without being a Sorcerer; ignored",
                    player.getName().getString());
            return;
        }
        if (Ultimate.isAwakened(player)) return;
        CharacterData.get(server).setHero(player.getUuid(), hero);
        Ultimate.sync(player);
    }

    public static void sync(ServerPlayerEntity p) {
        if (!ServerPlayNetworking.canSend(p, GojoNet.SYNC)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(type(p));
        ServerPlayNetworking.send(p, GojoNet.SYNC, buf);
    }

    public static void sendCooldown(ServerPlayerEntity p, Ability a, int remaining, int total) {
        if (!ServerPlayNetworking.canSend(p, GojoNet.COOLDOWN)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(a.id);
        buf.writeInt(Math.max(0, remaining));
        buf.writeInt(Math.max(1, total));
        ServerPlayNetworking.send(p, GojoNet.COOLDOWN, buf);
    }

    /** Re-sends the persistent RCT cooldown (join / respawn). */
    public static void sendRctCooldown(ServerPlayerEntity p) {
        MinecraftServer s = p.getServer();
        if (s == null) return;
        long remaining = CharacterData.get(s).rctReadyAt(p.getUuid()) - s.getOverworld().getTime();
        if (remaining > 0) {
            sendCooldown(p, Ability.RCT, (int) remaining, Ability.RCT.cooldownTicks());
        }
    }

    public static void syncAll(ServerPlayerEntity p) {
        sync(p);
        sendRctCooldown(p);
        Ultimate.sync(p);
    }
}
