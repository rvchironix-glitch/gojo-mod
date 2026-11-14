package com.jjs.gojo;

import com.jjs.gojo.effect.EffectManager;
import com.jjs.gojo.effect.LimitlessAwakening;
import com.jjs.gojo.effect.SukunaTransformation;
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

/** Shared ultimate meter + transformation lifecycle (Gojo: Limitless Awakened, Yuji: Sukuna). Server-authoritative. */
public final class Ultimate {
    /** Gojo's Limitless Awakened lasts exactly 120 seconds. Deliberately not configurable. */
    public static final int GOJO_TICKS = 2400;
    /** Yuji's Sukuna transformation lasts exactly 60 seconds. Deliberately not configurable. */
    public static final int SUKUNA_TICKS = 1200;

    private Ultimate() {}

    /** True while ANY transformation is active (stacking is impossible). */
    public static boolean isAwakened(ServerPlayerEntity p) {
        MinecraftServer s = p.getServer();
        if (s == null) return false;
        PlayerState st = GojoMod.stateOrNull(p.getUuid());
        return st != null && st.awakenedUntil > s.getTicks();
    }

    private static int hero(ServerPlayerEntity p) {
        return CharacterData.get(p.getServer()).hero(p.getUuid());
    }

    public static boolean isGojoAwakened(ServerPlayerEntity p) {
        return isAwakened(p) && hero(p) == Heroes.GOJO;
    }

    public static boolean isSukuna(ServerPlayerEntity p) {
        return isAwakened(p) && hero(p) == Heroes.YUJI;
    }

    public static float damageMult(ServerPlayerEntity p) {
        return isGojoAwakened(p) ? GojoConfig.get().awakenedDamageMult : 1.0f;
    }

    public static double rangeMult(ServerPlayerEntity p) {
        return isGojoAwakened(p) ? GojoConfig.get().awakenedRangeMult : 1.0;
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
        int hero = d.hero(p.getUuid());
        if (hero != Heroes.GOJO && hero != Heroes.YUJI) return;
        if (d.meter(p.getUuid()) < 100f) {
            p.sendMessage(Text.literal("Ultimate not ready: " + (int) d.meter(p.getUuid()) + "%"), true);
            return;
        }
        if (hero == Heroes.GOJO) {
            st.awakenedUntil = now + GOJO_TICKS;
            EffectManager.add(new LimitlessAwakening(p));
        } else {
            st.awakenedUntil = now + SUKUNA_TICKS;
            EffectManager.add(new SukunaTransformation(p));
        }
        sync(p);
    }

    /** Ends the transformation and resets the meter. Safe to call when the player is offline. */
    public static void end(MinecraftServer server, UUID id) {
        PlayerState st = GojoMod.stateOrNull(id);
        if (st != null) st.awakenedUntil = 0;
        CharacterData data = CharacterData.get(server);
        data.setMeter(id, 0f);

        ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
        if (p == null) return;
        p.removeStatusEffect(StatusEffects.GLOWING);
        if (p.isAlive()) {
            var world = p.getServerWorld();
            boolean yuji = data.hero(id) == Heroes.YUJI;
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.5, 0.8, 0.5, 0.05);
            world.playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.5f, 0.7f);
            p.sendMessage(Text.literal(yuji ? "Sukuna's hold fades - you are Yuji again" : "Limitless has faded"), true);
        }
        sync(p);
    }

    public static void sync(ServerPlayerEntity p) {
        if (!ServerPlayNetworking.canSend(p, GojoNet.ULT)) return;
        MinecraftServer server = p.getServer();
        CharacterData d = CharacterData.get(server);
        PlayerState st = GojoMod.stateOrNull(p.getUuid());
        int remaining = st == null ? 0 : (int) Math.max(0, st.awakenedUntil - server.getTicks());
        int hero = d.hero(p.getUuid());
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(hero);
        buf.writeFloat(d.meter(p.getUuid()));
        buf.writeInt(remaining);
        buf.writeInt(hero == Heroes.YUJI ? SUKUNA_TICKS : GOJO_TICKS);
        ServerPlayNetworking.send(p, GojoNet.ULT, buf);
    }
}
