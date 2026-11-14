package com.jjs.gojo.effect;

import com.jjs.gojo.GojoConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.UUID;

/** Black Flash: a short charge of black-red lightning, then one devastating punch. */
public class BlackFlash implements ServerEffect {
    private static final int WINDUP = 6;

    private final ServerWorld world;
    private final UUID ownerId;
    private int age;

    public BlackFlash(ServerPlayerEntity owner) {
        this.world = owner.getServerWorld();
        this.ownerId = owner.getUuid();
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.0f, 0.5f);
    }

    @Override
    public boolean tick() {
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
        if (owner == null || !owner.isAlive()) return true;

        Vec3d fist = Fx.fist(owner);
        if (age < WINDUP) {
            world.spawnParticles(Fx.BLACK, fist.x, fist.y, fist.z, 6, 0.25, 0.25, 0.25, 0);
            world.spawnParticles(Fx.CRIMSON, fist.x, fist.y, fist.z, 3, 0.2, 0.2, 0.2, 0);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, fist.x, fist.y, fist.z, 3, 0.3, 0.3, 0.3, 0.15);
            age++;
            return false;
        }

        GojoConfig c = GojoConfig.get();
        owner.swingHand(Hand.MAIN_HAND, true);
        CombatUtil.lunge(owner, 0.9);
        List<LivingEntity> targets = CombatUtil.inFront(owner, 3.6, 0.5);

        if (targets.isEmpty()) {
            world.spawnParticles(Fx.BLACK, fist.x, fist.y, fist.z, 10, 0.3, 0.3, 0.3, 0);
            world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 0.6f);
            return true;
        }

        for (LivingEntity t : targets) {
            CombatUtil.damage(owner, t, c.blackFlashDamage, true);
            CombatUtil.knockback(t, owner.getPos(), 2.4, 0.35);
            Vec3d at = t.getPos().add(0, t.getHeight() * 0.6, 0);
            world.spawnParticles(Fx.BLACK, at.x, at.y, at.z, 40, 0.5, 0.5, 0.5, 0);
            world.spawnParticles(Fx.CRIMSON, at.x, at.y, at.z, 30, 0.5, 0.5, 0.5, 0);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 30, 0.4, 0.4, 0.4, 0.4);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.05);
            world.spawnParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.8f, 1.5f);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.2f, 0.8f);
        }
        return true;
    }
}
