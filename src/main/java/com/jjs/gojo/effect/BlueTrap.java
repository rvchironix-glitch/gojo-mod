package com.jjs.gojo.effect;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Immobilisation for entities caught in Lapis Blue (server thread only).
 *
 * Every tick inside the orb an entity is "held": its target is cleared, its pathfinding stopped and a very
 * short Slowness VII is (re)applied, which is synced to clients. Entities that are held cannot deal damage
 * (see GojoMod.allowDamage). Everything expires by itself within 6 ticks, so nothing can stay frozen if
 * the orb ends abruptly, the caster disconnects or the server stops.
 */
public final class BlueTrap {
    private static final int HOLD_TICKS = 6;
    private static final int SLOW_AMPLIFIER = 7;
    private static final Map<UUID, Long> UNTIL = new HashMap<>();

    private BlueTrap() {}

    public static void hold(LivingEntity e, long nowTick) {
        UNTIL.put(e.getUuid(), nowTick + HOLD_TICKS);
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, HOLD_TICKS, SLOW_AMPLIFIER, false, false, false));
        if (e instanceof MobEntity mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
    }

    public static boolean isTrapped(Entity e, long nowTick) {
        Long until = UNTIL.get(e.getUuid());
        if (until == null) return false;
        if (nowTick > until) {
            UNTIL.remove(e.getUuid());
            return false;
        }
        return true;
    }

    /** Immediately restores normal movement/attacking. */
    public static void release(LivingEntity e) {
        UNTIL.remove(e.getUuid());
        StatusEffectInstance inst = e.getStatusEffect(StatusEffects.SLOWNESS);
        if (inst != null && inst.getAmplifier() == SLOW_AMPLIFIER && inst.getDuration() <= HOLD_TICKS) {
            e.removeStatusEffect(StatusEffects.SLOWNESS);
        }
    }

    public static void clear() {
        UNTIL.clear();
    }
}
