package com.jjs.gojo.effect;

import com.jjs.gojo.GojoConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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
 * R Special (PLACEHOLDER): Hollow Purple. Blue and Red orbs converge in front of the player, then
 * merge into a piercing purple beam. Replace with the exact R move once it is known.
 * Never destroys blocks.
 */
public class HollowPurple implements ServerEffect {
    private static final DustParticleEffect BLUE = new DustParticleEffect(new Vector3f(0.15f, 0.45f, 1.0f), 1.6f);
    private static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(1.0f, 0.1f, 0.1f), 1.6f);
    private static final DustParticleEffect PURPLE = new DustParticleEffect(new Vector3f(0.65f, 0.15f, 1.0f), 2.2f);

    private final ServerWorld world;
    private final UUID ownerId;
    private final Set<UUID> alreadyHit = new HashSet<>();
    private int age;
    private boolean fired;
    private Vec3d beamPos;
    private Vec3d beamDir;
    private double traveled;

    public HollowPurple(ServerPlayerEntity owner) {
        this.world = owner.getServerWorld();
        this.ownerId = owner.getUuid();
        int charge = GojoConfig.get().specialChargeTicks;
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, charge + 2, 4, false, false, false));
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.5f, 0.6f);
    }

    @Override
    public boolean tick() {
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
        GojoConfig c = GojoConfig.get();

        if (!fired) {
            if (owner == null || !owner.isAlive()) return true;
            charge(owner, c);
            age++;
            if (age >= c.specialChargeTicks) fire(owner);
            return false;
        }
        return beamTick(owner, c);
    }

    private Vec3d center(ServerPlayerEntity owner) {
        return owner.getEyePos().add(owner.getRotationVec(1.0f).normalize().multiply(2.5)).subtract(0, 0.2, 0);
    }

    private void charge(ServerPlayerEntity owner, GojoConfig c) {
        double yaw = Math.toRadians(owner.getYaw());
        Vec3d forward = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3d right = new Vec3d(-forward.z, 0, forward.x);
        Vec3d mid = center(owner);

        double progress = Math.min(1.0, (double) age / Math.max(1, c.specialChargeTicks));
        double d = 2.5 * (1.0 - progress);

        Vec3d blue = mid.add(right.multiply(d));
        Vec3d red = mid.subtract(right.multiply(d));
        world.spawnParticles(BLUE, blue.x, blue.y, blue.z, 6, 0.15, 0.15, 0.15, 0);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, blue.x, blue.y, blue.z, 1, 0.1, 0.1, 0.1, 0.01);
        world.spawnParticles(RED, red.x, red.y, red.z, 6, 0.15, 0.15, 0.15, 0);
        world.spawnParticles(ParticleTypes.FLAME, red.x, red.y, red.z, 1, 0.1, 0.1, 0.1, 0.01);

        if (progress > 0.8) {
            world.spawnParticles(PURPLE, mid.x, mid.y, mid.z, 10, 0.3, 0.3, 0.3, 0);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, mid.x, mid.y, mid.z, 4, 0.5, 0.5, 0.5, 0.2);
        }

        if (age % 8 == 0) {
            world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE,
                    SoundCategory.PLAYERS, 1.2f, 0.6f + (float) progress);
        }
    }

    private void fire(ServerPlayerEntity owner) {
        fired = true;
        owner.removeStatusEffect(StatusEffects.SLOWNESS);
        beamDir = owner.getRotationVec(1.0f).normalize();
        beamPos = center(owner);
        world.spawnParticles(ParticleTypes.FLASH, beamPos.x, beamPos.y, beamPos.z, 1, 0, 0, 0, 0);
        world.playSound(null, beamPos.x, beamPos.y, beamPos.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM,
                SoundCategory.PLAYERS, 2.0f, 0.8f);
        world.playSound(null, beamPos.x, beamPos.y, beamPos.z, SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.PLAYERS, 1.5f, 0.6f);
    }

    private boolean beamTick(ServerPlayerEntity owner, GojoConfig c) {
        // the beam keeps going even if the caster logs out; damage just has no attributed owner then
        Vec3d next = beamPos.add(beamDir.multiply(c.specialSpeed));
        BlockHitResult hit = world.raycast(new RaycastContext(beamPos, next,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE,
                owner != null ? owner : null));
        boolean blocked = hit.getType() != HitResult.Type.MISS;
        Vec3d end = blocked ? hit.getPos() : next;

        // particles along the swept segment
        Vec3d seg = end.subtract(beamPos);
        int n = Math.max(1, (int) Math.ceil(seg.length() / 1.0));
        for (int i = 0; i < n; i++) {
            Vec3d p = beamPos.add(seg.multiply((i + 1.0) / n));
            world.spawnParticles(PURPLE, p.x, p.y, p.z, 8, 0.8, 0.8, 0.8, 0);
            world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 2, 0.6, 0.6, 0.6, 0.05);
            world.spawnParticles(ParticleTypes.PORTAL, p.x, p.y, p.z, 5, 1.0, 1.0, 1.0, 0.4);
        }

        beamPos = end;
        traveled += seg.length();

        if (owner != null) {
            double r = c.specialRadius;
            List<Entity> targets = world.getOtherEntities(owner, Box.of(beamPos, r * 2, r * 2, r * 2),
                    e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator());
            for (Entity e : targets) {
                if (alreadyHit.contains(e.getUuid())) continue;
                if (e.getPos().add(0, e.getHeight() / 2.0, 0).distanceTo(beamPos) > r + e.getWidth() / 2.0) continue;
                alreadyHit.add(e.getUuid());
                LivingEntity le = (LivingEntity) e;
                CombatUtil.damage(owner, le, c.specialDamage, true);
                CombatUtil.knockback(le, beamPos.subtract(beamDir), c.specialKnockback, 0.6);
                world.spawnParticles(ParticleTypes.EXPLOSION, le.getX(), le.getY() + 1, le.getZ(), 1, 0, 0, 0, 0);
            }
        }

        if (blocked || traveled >= c.specialRange) {
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, beamPos.x, beamPos.y, beamPos.z, 1, 0, 0, 0, 0);
            world.spawnParticles(PURPLE, beamPos.x, beamPos.y, beamPos.z, 50, 2.0, 2.0, 2.0, 0);
            world.playSound(null, beamPos.x, beamPos.y, beamPos.z, SoundEvents.ENTITY_GENERIC_EXPLODE,
                    SoundCategory.PLAYERS, 2.0f, 0.5f);
            return true;
        }
        return false;
    }
}
