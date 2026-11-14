package com.jjs.gojo.effect;

import com.jjs.gojo.GojoConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Maximum Blue: a huge, stationary Lapis Blue. Lasts exactly 10 seconds, pulls every living entity within
 * range into its core, holds them helpless (BlueTrap) and deals exactly 2 damage per second to each.
 */
public class MaximumBlue implements ServerEffect {
    /** Exactly 10 seconds. */
    public static final int DURATION_TICKS = 200;
    /** Exactly 2 damage per second. */
    public static final float DAMAGE_PER_SECOND = 2.0f;

    private static final DustParticleEffect BLUE = new DustParticleEffect(new Vector3f(0.1f, 0.4f, 1.0f), 2.2f);
    private static final DustParticleEffect PALE = new DustParticleEffect(new Vector3f(0.6f, 0.85f, 1.0f), 1.4f);

    private final ServerWorld world;
    private final UUID ownerId;
    private final Set<UUID> held = new HashSet<>();
    private final Vec3d pos;
    private int age;

    public MaximumBlue(ServerPlayerEntity owner) {
        this.world = owner.getServerWorld();
        this.ownerId = owner.getUuid();
        Vec3d dir = owner.getRotationVec(1.0f).normalize();
        Vec3d eye = owner.getEyePos();
        BlockHitResult hit = world.raycast(new RaycastContext(eye, eye.add(dir.multiply(22)),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, owner));
        this.pos = hit.getType() == HitResult.Type.MISS
                ? eye.add(dir.multiply(14))
                : hit.getPos().subtract(dir.multiply(2.5));
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 3.0f, 0.6f);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 2.0f, 0.5f);
    }

    @Override
    public boolean tick() {
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
        if (owner == null || !owner.isAlive() || age >= DURATION_TICKS) {
            releaseAll();
            world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 2, 0.5, 0.5, 0.5, 0);
            Fx.ring(world, pos, 6.0, 40, PALE);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 3.0f, 0.6f);
            return true;
        }
        visuals();
        pull(owner);
        if (age % 10 == 0) {
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 2.0f, 0.6f);
        }
        if (age % 40 == 0) {
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 3.0f, 0.7f);
        }
        age++;
        return false;
    }

    private void releaseAll() {
        for (UUID id : held) {
            Entity e = world.getEntity(id);
            if (e instanceof LivingEntity le) BlueTrap.release(le);
        }
        held.clear();
    }

    private void visuals() {
        // dense core sphere
        for (int i = 0; i < 14; i++) {
            Vec3d v = new Vec3d(world.getRandom().nextGaussian(), world.getRandom().nextGaussian(),
                    world.getRandom().nextGaussian());
            if (v.lengthSquared() < 1.0E-4) continue;
            Vec3d p = pos.add(v.normalize().multiply(3.4));
            world.spawnParticles(BLUE, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        // two tilted rotating rings
        for (int k = 0; k < 2; k++) {
            double tilt = k * 1.0;
            for (int j = 0; j < 18; j++) {
                double a = age * 0.25 + j * (Math.PI * 2 / 18) + k;
                double x = Math.cos(a) * 5.0;
                double z = Math.sin(a) * 5.0;
                world.spawnParticles(PALE, pos.x + x, pos.y + z * Math.sin(tilt), pos.z + z * Math.cos(tilt), 1, 0, 0, 0, 0);
            }
        }
        world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 3, 0.6, 0.6, 0.6, 0.02);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 3, 1.2, 1.2, 1.2, 0.02);

        if (age % 2 == 0) {
            double r = GojoConfig.get().maxBlueRadius * 0.9;
            for (int i = 0; i < 5; i++) {
                Vec3d v = new Vec3d(world.getRandom().nextGaussian(), world.getRandom().nextGaussian() * 0.6,
                        world.getRandom().nextGaussian());
                if (v.lengthSquared() < 1.0E-4) continue;
                Vec3d start = pos.add(v.normalize().multiply(r * (0.5 + world.getRandom().nextDouble() * 0.5)));
                Vec3d in = pos.subtract(start).multiply(0.1);
                world.spawnParticles(ParticleTypes.GLOW, start.x, start.y, start.z, 0, in.x, in.y, in.z, 1.0);
            }
        }
        if (age % 20 == 0) Fx.ring(world, pos, 6.0, 40, PALE);
    }

    private void pull(ServerPlayerEntity owner) {
        GojoConfig c = GojoConfig.get();
        double r = c.maxBlueRadius;
        long now = world.getServer().getTicks();
        List<Entity> targets = world.getOtherEntities(owner, Box.of(pos, r * 2, r * 2, r * 2),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator());
        for (Entity e : targets) {
            Vec3d center = e.getPos().add(0, e.getHeight() / 2.0, 0);
            Vec3d to = pos.subtract(center);
            double dist = to.length();
            if (dist > r) continue;
            LivingEntity le = (LivingEntity) e;

            BlueTrap.hold(le, now); // cannot move on its own, cannot target or attack
            held.add(e.getUuid());

            Vec3d v;
            if (dist < 1.5) {
                v = to.multiply(0.3);
            } else {
                double strength = c.maxBluePullStrength * (1.0 - dist / r) + 0.15;
                v = e.getVelocity().multiply(0.5).add(to.normalize().multiply(strength));
                if (v.length() > 1.6) v = v.normalize().multiply(1.6);
            }
            e.setVelocity(v);
            e.velocityModified = true;
            e.fallDistance = 0;

            // exactly 2 damage per second
            if (age % 20 == 0) {
                CombatUtil.damageExact(owner, le, DAMAGE_PER_SECOND);
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 6, 0.3, 0.3, 0.3, 0.1);
            }
        }
    }
}
