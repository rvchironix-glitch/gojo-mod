package com.jjs.gojo.effect;

import com.jjs.gojo.GojoConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.UUID;

/**
 * Dismantle: three rapid, thin cutting slashes fired along the line of sight. Instant ranged hits that
 * pierce entities and stop at blocks (blocks are never damaged).
 */
public class Dismantle implements ServerEffect {
    private static final double RANGE = 28.0;
    private static final double[] TILT_DEGREES = {0, 55, -55};

    private final ServerWorld world;
    private final UUID ownerId;
    private int age;

    public Dismantle(ServerPlayerEntity owner) {
        this.world = owner.getServerWorld();
        this.ownerId = owner.getUuid();
    }

    @Override
    public boolean tick() {
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
        if (owner == null || !owner.isAlive()) return true;

        if (age % 4 == 0 && age / 4 < TILT_DEGREES.length) {
            slash(owner, TILT_DEGREES[age / 4]);
        }
        age++;
        return age > 4 * (TILT_DEGREES.length - 1);
    }

    private void slash(ServerPlayerEntity owner, double tiltDeg) {
        GojoConfig c = GojoConfig.get();
        Vec3d dir = owner.getRotationVec(1.0f).normalize();
        Vec3d from = owner.getEyePos().add(dir.multiply(0.8)).subtract(0, 0.2, 0);
        Vec3d to = from.add(dir.multiply(RANGE));
        BlockHitResult hit = world.raycast(new RaycastContext(from, to,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, owner));
        if (hit.getType() != HitResult.Type.MISS) to = hit.getPos();

        // slash "blades": short lines perpendicular to the path, tilted by tiltDeg
        Vec3d up = Math.abs(dir.y) > 0.95 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        Vec3d side = dir.crossProduct(up).normalize();
        Vec3d perp = up.crossProduct(side).normalize();
        double t = Math.toRadians(tiltDeg);
        Vec3d blade = side.multiply(Math.cos(t)).add(perp.multiply(Math.sin(t)));

        double length = to.distanceTo(from);
        for (double d = 1.5; d < length; d += 2.5) {
            Vec3d center = from.add(dir.multiply(d));
            Fx.line(world, center.subtract(blade.multiply(1.3)), center.add(blade.multiply(1.3)), 0.35, Fx.CRIMSON);
            world.spawnParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 2, 0.1, 0.1, 0.1, 0.05);
        }
        Fx.line(world, from, to, 1.0, Fx.BONE);
        world.spawnParticles(ParticleTypes.SWEEP_ATTACK, to.x, to.y, to.z, 1, 0, 0, 0, 0);

        for (LivingEntity target : Fx.alongSegment(owner, from, to, 1.3)) {
            CombatUtil.damage(owner, target, c.dismantleDamage, true);
            CombatUtil.knockback(target, from, 0.5, 0.1);
            Vec3d at = target.getPos().add(0, target.getHeight() * 0.5, 0);
            world.spawnParticles(Fx.CRIMSON, at.x, at.y, at.z, 14, 0.3, 0.3, 0.3, 0);
        }

        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 1.8f);
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 1.0f, 1.9f);
    }
}
