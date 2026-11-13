package com.jjs.gojo.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

public final class CombatUtil {
    private CombatUtil() {}

    /** Damage as the owner (server-authoritative; respects PvP settings). */
    public static boolean damage(ServerPlayerEntity owner, LivingEntity target, float amount) {
        if (target == owner || !target.isAlive()) return false;
        return target.damage(owner.getDamageSources().playerAttack(owner), amount);
    }

    /** Horizontal knockback away from {@code from} plus an upward kick. */
    public static void knockback(LivingEntity target, Vec3d from, double strength, double up) {
        Vec3d away = target.getPos().subtract(from).multiply(1, 0, 1);
        if (away.lengthSquared() < 1.0E-4) {
            away = new Vec3d(0, 0, 1);
        }
        away = away.normalize().multiply(strength);
        target.setVelocity(away.x, up, away.z);
        target.velocityModified = true;
    }
}
