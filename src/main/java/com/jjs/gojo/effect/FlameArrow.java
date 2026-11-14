package com.jjs.gojo.effect;

import com.jjs.gojo.GojoConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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

import java.util.List;
import java.util.UUID;

/**
 * Flame Arrow: draw a bow of fire (short charge), then loose a blazing arrow that detonates and ignites
 * its targets. Never places fire or damages blocks.
 */
public class FlameArrow implements ServerEffect {
    private static final int CHARGE = 12;
    private static final double RANGE = 60.0;
    private static final double RADIUS = 4.5;

    private final ServerWorld world;
    private final UUID ownerId;
    private int age;
    private boolean launched;
    private Vec3d pos;
    private Vec3d dir;
    private double traveled;

    public FlameArrow(ServerPlayerEntity owner) {
        this.world = owner.getServerWorld();
        this.ownerId = owner.getUuid();
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, CHARGE + 1, 3, false, false, false));
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.2f, 0.6f);
    }

    @Override
    public boolean tick() {
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
        if (owner == null || !owner.isAlive()) return true;
        GojoConfig c = GojoConfig.get();

        if (!launched) {
            drawBow(owner);
            age++;
            if (age >= CHARGE) launch(owner);
            return false;
        }

        for (int i = 0; i < 2; i++) {
            Vec3d next = pos.add(dir.multiply(1.2));
            BlockHitResult hit = world.raycast(new RaycastContext(pos, next,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, owner));
            if (hit.getType() != HitResult.Type.MISS) {
                pos = hit.getPos();
                explode(owner, c);
                return true;
            }
            pos = next;
            traveled += 1.2;
            trail();
            List<Entity> near = world.getOtherEntities(owner, Box.of(pos, 1.4, 1.4, 1.4),
                    e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator());
            if (!near.isEmpty() || traveled >= RANGE) {
                explode(owner, c);
                return true;
            }
        }
        return false;
    }

    private void drawBow(ServerPlayerEntity owner) {
        Vec3d look = owner.getRotationVec(1.0f).normalize();
        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d side = look.crossProduct(up);
        side = side.lengthSquared() < 1.0E-4 ? new Vec3d(1, 0, 0) : side.normalize();
        Vec3d center = owner.getEyePos().add(look.multiply(1.5)).subtract(0, 0.3, 0);

        // bow limb arc
        for (int deg = -80; deg <= 80; deg += 16) {
            double a = Math.toRadians(deg);
            Vec3d p = center.add(up.multiply(Math.sin(a) * 0.9)).subtract(look.multiply((1 - Math.cos(a)) * 0.4));
            world.spawnParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
        // string pulled back and the arrow forming
        double pull = Math.min(1.0, age / (double) CHARGE) * 0.7;
        Vec3d nock = center.subtract(look.multiply(pull));
        world.spawnParticles(Fx.ORANGE, nock.x, nock.y, nock.z, 2, 0.03, 0.03, 0.03, 0);
        world.spawnParticles(ParticleTypes.LAVA, center.x, center.y, center.z, 1, 0.3, 0.3, 0.3, 0);
    }

    private void launch(ServerPlayerEntity owner) {
        launched = true;
        dir = owner.getRotationVec(1.0f).normalize();
        pos = owner.getEyePos().add(dir.multiply(1.5)).subtract(0, 0.3, 0);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.5f, 0.7f);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.2f, 0.6f);
    }

    private void trail() {
        world.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 4, 0.1, 0.1, 0.1, 0.02);
        world.spawnParticles(Fx.ORANGE, pos.x, pos.y, pos.z, 3, 0.1, 0.1, 0.1, 0);
        world.spawnParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 1, 0.05, 0.05, 0.05, 0.01);
    }

    private void explode(ServerPlayerEntity owner, GojoConfig c) {
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 80, 1.5, 1.5, 1.5, 0.2);
        world.spawnParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z, 20, 1.5, 1.0, 1.5, 0);
        world.spawnParticles(Fx.ORANGE, pos.x, pos.y, pos.z, 40, 1.8, 1.8, 1.8, 0);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 2.0f, 0.8f);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.5f, 0.5f);

        for (LivingEntity t : Fx.around(owner, pos, RADIUS)) {
            double falloff = 1.0 - (t.getPos().add(0, t.getHeight() / 2.0, 0).distanceTo(pos) / RADIUS) * 0.5;
            CombatUtil.damage(owner, t, (float) (c.flameArrowDamage * falloff), true);
            CombatUtil.knockback(t, pos, 1.3 * falloff, 0.4);
            t.setOnFireFor(6);
        }
    }
}
