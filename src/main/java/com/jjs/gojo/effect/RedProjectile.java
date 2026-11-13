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

import java.util.List;
import java.util.UUID;

/** Reversal Red: fast repulsive projectile that detonates with heavy knockback. Does not break blocks. */
public class RedProjectile implements ServerEffect {
    private static final DustParticleEffect RED_DUST = new DustParticleEffect(new Vector3f(1.0f, 0.1f, 0.1f), 1.8f);

    private final ServerWorld world;
    private final UUID ownerId;
    private final Vec3d dir;
    private Vec3d pos;
    private double traveled;

    public RedProjectile(ServerPlayerEntity owner) {
        this.world = owner.getServerWorld();
        this.ownerId = owner.getUuid();
        this.dir = owner.getRotationVec(1.0f).normalize();
        this.pos = owner.getEyePos().add(dir.multiply(1.2)).subtract(0, 0.2, 0);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.5f, 0.6f);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 0.5f);
    }

    @Override
    public boolean tick() {
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
        if (owner == null) return true;
        GojoConfig c = GojoConfig.get();

        int steps = 2;
        double step = c.redSpeed / steps;
        for (int i = 0; i < steps; i++) {
            Vec3d next = pos.add(dir.multiply(step));

            BlockHitResult hit = world.raycast(new RaycastContext(pos, next,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, owner));
            if (hit.getType() != HitResult.Type.MISS) {
                pos = hit.getPos();
                explode(owner, c);
                return true;
            }

            pos = next;
            traveled += step;
            trail();

            List<Entity> hits = world.getOtherEntities(owner, Box.of(pos, 1.6, 1.6, 1.6),
                    e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator());
            if (!hits.isEmpty() || traveled >= c.redRange) {
                explode(owner, c);
                return true;
            }
        }
        return false;
    }

    private void trail() {
        world.spawnParticles(RED_DUST, pos.x, pos.y, pos.z, 4, 0.12, 0.12, 0.12, 0);
        world.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 2, 0.15, 0.15, 0.15, 0.02);
        world.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 1, 0.1, 0.1, 0.1, 0.3);
    }

    private void explode(ServerPlayerEntity owner, GojoConfig c) {
        double r = c.redExplosionRadius;
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        world.spawnParticles(RED_DUST, pos.x, pos.y, pos.z, 60, r * 0.4, r * 0.4, r * 0.4, 0);
        world.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 40, r * 0.3, r * 0.3, r * 0.3, 0.2);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 2.0f, 0.7f);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.PLAYERS, 1.0f, 0.8f);

        List<Entity> targets = world.getOtherEntities(owner, Box.of(pos, r * 2, r * 2, r * 2),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator());
        for (Entity e : targets) {
            double dist = e.getPos().add(0, e.getHeight() / 2.0, 0).distanceTo(pos);
            if (dist > r) continue;
            double falloff = 1.0 - (dist / r) * 0.6;
            LivingEntity le = (LivingEntity) e;
            CombatUtil.damage(owner, le, (float) (c.redDamage * falloff));
            CombatUtil.knockback(le, pos, c.redKnockback * falloff, 0.45);
        }
    }
}
