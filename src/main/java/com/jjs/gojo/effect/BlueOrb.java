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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Lapis Blue: a drifting energy orb that pulls nearby living entities in and holds them helpless. */
public class BlueOrb implements ServerEffect {
    private static final DustParticleEffect BLUE_DUST = new DustParticleEffect(new Vector3f(0.15f, 0.45f, 1.0f), 1.6f);

    private final ServerWorld world;
    private final UUID ownerId;
    private final Vec3d dir;
    private final Set<UUID> held = new HashSet<>();
    private Vec3d pos;
    private int age;

    public BlueOrb(ServerPlayerEntity owner) {
        this.world = owner.getServerWorld();
        this.ownerId = owner.getUuid();
        this.dir = owner.getRotationVec(1.0f).normalize();
        this.pos = owner.getEyePos().add(dir.multiply(3.5));
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.5f, 1.8f);
    }

    @Override
    public boolean tick() {
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
        GojoConfig c = GojoConfig.get();
        if (owner == null || !owner.isAlive() || age >= c.blueDurationTicks) {
            releaseAll();
            world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.2f, 1.6f);
            return true;
        }

        // drift forward for the first second, stop on solid blocks
        if (age < 20) {
            Vec3d next = pos.add(dir.multiply(0.25));
            if (world.getBlockState(BlockPos.ofFloored(next)).isAir()) pos = next;
        }

        spawnVisuals();
        pull(owner, c);

        if (age % 10 == 0) {
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 0.9f, 1.7f);
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

    private void spawnVisuals() {
        world.spawnParticles(BLUE_DUST, pos.x, pos.y, pos.z, 6, 0.15, 0.15, 0.15, 0);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 2, 0.2, 0.2, 0.2, 0.01);
        for (int i = 0; i < 3; i++) {
            double a = age * 0.6 + i * (Math.PI * 2 / 3);
            double r = 1.3;
            double x = pos.x + Math.cos(a) * r;
            double z = pos.z + Math.sin(a) * r;
            double y = pos.y + Math.sin(age * 0.3 + i) * 0.7;
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 1, 0, 0, 0, 0);
        }
        // inward streaks so the pull is readable
        if (age % 3 == 0) {
            double a = world.getRandom().nextDouble() * Math.PI * 2;
            double r = GojoConfig.get().blueRadius * 0.6;
            Vec3d start = pos.add(Math.cos(a) * r, (world.getRandom().nextDouble() - 0.5) * r, Math.sin(a) * r);
            Vec3d in = pos.subtract(start).multiply(0.08);
            world.spawnParticles(ParticleTypes.GLOW, start.x, start.y, start.z, 0, in.x, in.y, in.z, 1.0);
        }
    }

    private void pull(ServerPlayerEntity owner, GojoConfig c) {
        double r = c.blueRadius;
        long now = world.getServer().getTicks();
        Box box = Box.of(pos, r * 2, r * 2, r * 2);
        List<Entity> targets = world.getOtherEntities(owner, box,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator());
        for (Entity e : targets) {
            Vec3d center = e.getPos().add(0, e.getHeight() / 2.0, 0);
            Vec3d to = pos.subtract(center);
            double dist = to.length();
            if (dist > r || dist < 0.05) continue;

            // caught: cannot move on its own, cannot target or attack anything
            BlueTrap.hold((LivingEntity) e, now);
            held.add(e.getUuid());

            double strength = c.bluePullStrength * (1.0 - dist / r) + 0.1;
            Vec3d v = e.getVelocity().multiply(0.6).add(to.normalize().multiply(strength));
            if (v.length() > 1.2) v = v.normalize().multiply(1.2);
            e.setVelocity(v);
            e.velocityModified = true;
            e.fallDistance = 0;

            if (age % 10 == 0) {
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 3, 0.25, 0.3, 0.25, 0.05);
            }
            if (dist < 2.5 && age % c.blueDamageIntervalTicks == 0) {
                CombatUtil.damage(owner, (LivingEntity) e, c.blueDamage);
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 6, 0.2, 0.2, 0.2, 0.1);
            }
        }
    }
}
