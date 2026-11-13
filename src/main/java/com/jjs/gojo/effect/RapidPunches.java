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

/** Rapid Punches: a quick flurry of alternating punches ending in a knockback finisher. */
public class RapidPunches implements ServerEffect {
    private final ServerWorld world;
    private final UUID ownerId;
    private int age;

    public RapidPunches(ServerPlayerEntity owner) {
        this.world = owner.getServerWorld();
        this.ownerId = owner.getUuid();
    }

    @Override
    public boolean tick() {
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
        if (owner == null || !owner.isAlive()) return true;
        GojoConfig c = GojoConfig.get();

        if (age == 0) {
            CombatUtil.lunge(owner, 0.5);
        }

        int interval = Math.max(1, c.punchIntervalTicks);
        if (age % interval == 0) {
            int index = age / interval;
            boolean last = index >= c.punchCount - 1;
            punch(owner, c, index, last);
            if (last) return true;
        }
        age++;
        return false;
    }

    private void punch(ServerPlayerEntity owner, GojoConfig c, int index, boolean last) {
        owner.swingHand(index % 2 == 0 ? Hand.MAIN_HAND : Hand.OFF_HAND, true);

        Vec3d look = owner.getRotationVec(1.0f).normalize();
        List<LivingEntity> targets = CombatUtil.inFront(owner, c.punchReach, 0.5);

        if (targets.isEmpty()) {
            world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE, SoundCategory.PLAYERS, 0.8f, 1.2f);
            Vec3d fist = owner.getEyePos().add(look.multiply(1.5)).subtract(0, 0.3, 0);
            world.spawnParticles(ParticleTypes.CRIT, fist.x, fist.y, fist.z, 2, 0.1, 0.1, 0.1, 0.05);
            return;
        }

        for (LivingEntity t : targets) {
            CombatUtil.damage(owner, t, last ? c.punchFinisherDamage : c.punchDamage, true);
            Vec3d at = t.getPos().add(0, t.getHeight() * 0.6, 0);
            world.spawnParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.25, 0.25, 0.25, 0.25);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 3, 0.2, 0.2, 0.2, 0.1);

            if (last) {
                CombatUtil.knockback(t, owner.getPos(), 1.7, 0.4);
                world.spawnParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            } else {
                // pin the target in front of the player so the flurry keeps connecting
                t.setVelocity(look.x * 0.12, Math.max(0.0, t.getVelocity().y * 0.3), look.z * 0.12);
                t.velocityModified = true;
            }
        }

        if (last) {
            world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 1.4f, 0.8f);
        } else {
            world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 0.9f, 1.0f + 0.04f * index);
        }
    }
}
