package com.jjs.gojo.effect;

import com.jjs.gojo.GojoMod;
import com.jjs.gojo.Ultimate;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public final class CombatUtil {
    private CombatUtil() {}

    /** Damage as the owner (server-authoritative; respects PvP settings). */
    public static boolean damage(ServerPlayerEntity owner, LivingEntity target, float amount) {
        return damage(owner, target, amount, false);
    }

    /** @param ignoreIframes true lets rapid multi-hit moves land every hit. */
    public static boolean damage(ServerPlayerEntity owner, LivingEntity target, float amount, boolean ignoreIframes) {
        if (target == owner || !target.isAlive()) return false;
        if (ignoreIframes) target.timeUntilRegen = 0;
        // Marks this as an ability hit so Cursed Energy's M1 Black Flash never applies to it.
        GojoMod.inAbility = true;
        try {
            return target.damage(owner.getDamageSources().playerAttack(owner), amount * Ultimate.damageMult(owner));
        } finally {
            GojoMod.inAbility = false;
        }
    }

    /**
     * Damage that is NOT scaled by Limitless Awakened, ignores invulnerability frames and armor, and cannot be
     * stopped by Gojo-mod blocking. Used by moves with fixed damage numbers. (Vanilla Resistance / absorption
     * effects on the victim can still reduce it, as for any damage in the game.)
     */
    public static boolean damageExact(ServerPlayerEntity owner, LivingEntity target, float amount) {
        if (target == owner || !target.isAlive()) return false;
        target.timeUntilRegen = 0;
        GojoMod.bypassGuard = true;
        try {
            return target.damage(owner.getDamageSources().indirectMagic(owner, owner), amount);
        } finally {
            GojoMod.bypassGuard = false;
        }
    }

    /** Fixed self-damage (e.g. Hollow Nuke recoil). */
    public static void selfDamageExact(ServerPlayerEntity owner, float amount) {
        owner.timeUntilRegen = 0;
        GojoMod.bypassGuard = true;
        try {
            owner.damage(owner.getDamageSources().magic(), amount);
        } finally {
            GojoMod.bypassGuard = false;
        }
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

    /** Living entities in front of the player within reach and a view cone (minDot = cosine of half angle). */
    public static List<LivingEntity> inFront(ServerPlayerEntity p, double reach, double minDot) {
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1.0f).normalize();
        Box box = p.getBoundingBox().stretch(look.multiply(reach)).expand(1.0);
        List<LivingEntity> out = new ArrayList<>();
        for (var e : p.getWorld().getOtherEntities(p, box, x -> x instanceof LivingEntity && x.isAlive() && !x.isSpectator())) {
            Vec3d to = e.getPos().add(0, e.getHeight() / 2.0, 0).subtract(eye);
            double dist = to.length();
            if (dist < 1.0E-4) continue;
            if (dist <= reach + e.getWidth() / 2.0 && to.normalize().dotProduct(look) >= minDot) {
                out.add((LivingEntity) e);
            }
        }
        return out;
    }

    /** Short forward lunge for the player. */
    public static void lunge(ServerPlayerEntity p, double strength) {
        Vec3d look = p.getRotationVec(1.0f).multiply(1, 0, 1);
        if (look.lengthSquared() < 1.0E-4) return;
        look = look.normalize().multiply(strength);
        p.setVelocity(look.x, Math.max(p.getVelocity().y, 0.0), look.z);
        p.velocityModified = true;
    }
}
