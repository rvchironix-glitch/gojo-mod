package com.jjs.gojo.effect;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Timed stun (0.2 Domain). Server thread only.
 *
 * A stunned entity is held every tick: target cleared, pathfinding stopped, horizontal velocity zeroed and a
 * very short Slowness VII / Jump "negative" / Mining Fatigue is re-applied (synced to clients). A stunned
 * attacker's damage is cancelled (GojoMod.allowDamage) and stunned players cannot use abilities.
 * Nothing is permanent: the short effects expire by themselves within 5 ticks, the entry is dropped when the
 * entity dies, leaves the game or the time runs out, and no AI flags are ever written to the entity.
 */
public final class Stun {
    /** 15 seconds. */
    public static final int DURATION_TICKS = 300;
    private static final int HOLD_TICKS = 5;
    private static final Map<UUID, Long> UNTIL = new HashMap<>();

    private Stun() {}

    public static void apply(MinecraftServer server, LivingEntity e, int ticks) {
        UNTIL.put(e.getUuid(), server.getTicks() + ticks);
        hold(e, ticks, server.getTicks());
    }

    public static boolean isStunned(Entity e) {
        Long until = UNTIL.get(e.getUuid());
        MinecraftServer s = e.getServer();
        return until != null && s != null && s.getTicks() < until;
    }

    public static void tick(MinecraftServer server) {
        if (UNTIL.isEmpty()) return;
        long now = server.getTicks();
        Iterator<Map.Entry<UUID, Long>> it = UNTIL.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            LivingEntity le = find(server, entry.getKey());
            if (le == null || !le.isAlive() || now >= entry.getValue()) {
                if (le != null) release(le);
                it.remove();
                continue;
            }
            hold(le, (int) (entry.getValue() - now), now);
        }
    }

    /** Ends one entity's stun immediately (domain ended early, caster died / left / changed dimension). */
    public static void cancel(MinecraftServer server, java.util.UUID id) {
        if (UNTIL.remove(id) == null) return;
        LivingEntity le = find(server, id);
        if (le != null) release(le);
    }

    public static void clear() {
        UNTIL.clear();
    }

    /** Finds the entity in any dimension (survives dimension changes). */
    private static LivingEntity find(MinecraftServer server, UUID id) {
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
        if (p != null) return p;
        for (ServerWorld w : server.getWorlds()) {
            Entity e = w.getEntity(id);
            if (e instanceof LivingEntity le) return le;
        }
        return null;
    }

    private static void hold(LivingEntity e, int remaining, long now) {
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, HOLD_TICKS, 7, false, false, false));
        e.setVelocity(0, Math.min(e.getVelocity().y, 0.0), 0);
        e.velocityModified = true;
        if (e instanceof MobEntity mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
        if (e instanceof ServerPlayerEntity p) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, HOLD_TICKS, 128, false, false, false));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, HOLD_TICKS, 5, false, false, false));
            if (now % 20 == 0) {
                p.sendMessage(Text.literal("STUNNED  " + ((remaining + 19) / 20) + "s"), true);
            }
        }
    }

    private static void release(LivingEntity e) {
        StatusEffectInstance slow = e.getStatusEffect(StatusEffects.SLOWNESS);
        if (slow != null && slow.getAmplifier() == 7 && slow.getDuration() <= HOLD_TICKS) {
            e.removeStatusEffect(StatusEffects.SLOWNESS);
        }
        StatusEffectInstance jump = e.getStatusEffect(StatusEffects.JUMP_BOOST);
        if (jump != null && jump.getAmplifier() == 128 && jump.getDuration() <= HOLD_TICKS) {
            e.removeStatusEffect(StatusEffects.JUMP_BOOST);
        }
        StatusEffectInstance fat = e.getStatusEffect(StatusEffects.MINING_FATIGUE);
        if (fat != null && fat.getAmplifier() == 5 && fat.getDuration() <= HOLD_TICKS) {
            e.removeStatusEffect(StatusEffects.MINING_FATIGUE);
        }
    }
}
