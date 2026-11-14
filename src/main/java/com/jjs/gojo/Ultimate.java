package com.jjs.gojo;

import com.jjs.gojo.effect.EffectManager;
import com.jjs.gojo.effect.LimitlessAwakening;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.UUID;

/** Shared ultimate meter + transformation lifecycle. All state is server-authoritative. */
public final class Ultimate {
    /** Gojo's Limitless Awakened lasts exactly 120 seconds. Deliberately not configurable. */
    public static final int AWAKENED_TICKS = 2400;

    private Ultimate() {}

    public static boolean isAwakened(ServerPlayerEntity p) {
        MinecraftServer s = p.getServer();
        if (s == null) return false;
        PlayerState st = GojoMod.stateOrNull(p.getUuid());
        return st != null && st.awakenedUntil > s.getTicks();
    }

    public static float damageMult(ServerPlayerEntity p) {
        return isAwakened(p) ? GojoConfig.get().awakenedDamageMult : 1.0f;
    }

    public static double rangeMult(ServerPlayerEntity p) {
        return isAwakened(p) ? GojoConfig.get().awakenedRangeMult : 1.0;
    }

    /** Adds charge (capped at 100). No charging while transformed or for non-sorcerers. */
    public static void charge(ServerPlayerEntity p, float amount) {
        if (amount <= 0f || !Characters.isSorcerer(p) || isAwakened(p)) return;
        CharacterData d = CharacterData.get(p.getServer());
        float old = d.meter(p.getUuid());
        if (old >= 100f) return;
        float now = Math.min(100f, old + amount);
        d.setMeter(p.getUuid(), now);
        if ((int) old != (int) now) sync(p);
    }

    /** Validates everything on the server; the client only asks. */
    public static void tryActivate(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        CharacterData d = CharacterData.get(server);
        PlayerState st = GojoMod.state(p);
        long now = server.getTicks();

        if (st.awakenedUntil > now) return; // no stacking
        if (d.hero(p.getUuid()) != Heroes.GOJO) return; // Yuji's ultimate arrives in Stage 5B
        if (d.meter(p.getUuid()) < 100f) {
            p.sendMessage(Text.literal("Ultimate not ready: " + (int) d.meter(p.getUuid()) + "%"), true);
            return;
        }
        st.awakenedUntil = now + AWAKENED_TICKS;
        EffectManager.add(new LimitlessAwakening(p));
        sync(p);
    }

    /** Ends the transformation and resets the meter. Safe to call when the player is offline. */
    public static void end(MinecraftServer server, UUID id) {
        PlayerState st = GojoMod.stateOrNull(id);
        if (st != null) st.awakenedUntil = 0;
        CharacterData.get(server).setMeter(id, 0f);

        ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
        if (p == null) return;
        p.removeStatusEffect(StatusEffects.GLOWING);
        if (p.isAlive()) {
            var world = p.getServerWorld();
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.5, 0.8, 0.5, 0.05);
            world.playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.5f, 0.7f);
            p.sendMessage(Text.literal("Limitless has faded"), true);
        }
        sync(p);
    }

    public static void sync(ServerPlayerEntity p) {
        if (!ServerPlayNetworking.canSend(p, GojoNet.ULT)) return;
        MinecraftServer server = p.getServer();
        CharacterData d = CharacterData.get(server);
        PlayerState st = GojoMod.stateOrNull(p.getUuid());
        int remaining = st == null ? 0 : (int) Math.max(0, st.awakenedUntil - server.getTicks());
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(d.hero(p.getUuid()));
        buf.writeFloat(d.meter(p.getUuid()));
        buf.writeInt(remaining);
        ServerPlayNetworking.send(p, GojoNet.ULT, buf);
    }
}
