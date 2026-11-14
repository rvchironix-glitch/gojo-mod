package com.jjs.gojo.effect;

import com.jjs.gojo.GojoConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * Malevolent Pressure: for three seconds the area around Sukuna is shredded by countless unseen cuts.
 * Pulses damage and slow everything in range except the caster. Does not damage blocks.
 */
public class MalevolentPressure implements ServerEffect {
    private static final int DURATION = 60;
    private static final int PULSE_EVERY = 10;

    private final ServerWorld world;
    private final UUID ownerId;
    private final Vec3d center;
    private int age;

    public MalevolentPressure(ServerPlayerEntity owner) {
        this.world = owner.getServerWorld();
        this.ownerId = owner.getUuid();
        this.center = owner.getPos();
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.8f, 0.6f);
    }

    @Override
    public boolean tick() {
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
        if (owner == null || !owner.isAlive() || age >= DURATION) return true;
        GojoConfig c = GojoConfig.get();
        double r = c.pressureRadius;

        if (age % 5 == 0) {
            Fx.ring(world, center.add(0, 0.15, 0), r, 48, Fx.CRIMSON);
            Fx.ring(world, center.add(0, 0.15, 0), r * 0.5, 24, Fx.BLACK);
        }

        // random slash marks all over the area
        for (int i = 0; i < 3; i++) {
            double a = world.getRandom().nextDouble() * Math.PI * 2;
            double d = Math.sqrt(world.getRandom().nextDouble()) * r;
            Vec3d p = center.add(Math.cos(a) * d, 0.3 + world.getRandom().nextDouble() * 2.2, Math.sin(a) * d);
            double b = world.getRandom().nextDouble() * Math.PI;
            Vec3d half = new Vec3d(Math.cos(b) * 1.4, (world.getRandom().nextDouble() - 0.5) * 1.6, Math.sin(b) * 1.4);
            Fx.line(world, p.subtract(half), p.add(half), 0.4, Fx.CRIMSON);
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + 0.5, center.z, 3, r * 0.4, 0.2, r * 0.4, 0.01);

        if (age % PULSE_EVERY == PULSE_EVERY - 1) {
            for (LivingEntity t : Fx.around(owner, center.add(0, 1, 0), r)) {
                CombatUtil.damage(owner, t, c.pressureDamagePerPulse, true);
                t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 1, false, true, true));
                Vec3d at = t.getPos().add(0, t.getHeight() * 0.5, 0);
                world.spawnParticles(Fx.CRIMSON, at.x, at.y, at.z, 10, 0.3, 0.4, 0.3, 0);
            }
            world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.5f, 0.5f);
            world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.PLAYERS, 0.8f, 0.7f);
        }
        age++;
        return false;
    }
}
