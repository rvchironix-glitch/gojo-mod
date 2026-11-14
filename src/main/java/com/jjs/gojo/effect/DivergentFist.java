package com.jjs.gojo.effect;

import com.jjs.gojo.GojoConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Divergent Fist: a punch whose damage "arrives late" as a second, much heavier impact. */
public class DivergentFist implements ServerEffect {
    private final ServerWorld world;
    private final UUID ownerId;
    private final List<UUID> hit = new ArrayList<>();
    private int age;

    public DivergentFist(ServerPlayerEntity owner) {
        this.world = owner.getServerWorld();
        this.ownerId = owner.getUuid();
    }

    @Override
    public boolean tick() {
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
        if (owner == null || !owner.isAlive()) return true;
        GojoConfig c = GojoConfig.get();

        if (age == 0) {
            owner.swingHand(Hand.MAIN_HAND, true);
            CombatUtil.lunge(owner, 0.6);
            List<LivingEntity> targets = CombatUtil.inFront(owner, 3.3, 0.5);
            if (targets.isEmpty()) {
                world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                        SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 0.9f);
                return true; // a whiff has no delayed impact
            }
            for (LivingEntity t : targets) {
                CombatUtil.damage(owner, t, c.divergentDamage1, true);
                CombatUtil.knockback(t, owner.getPos(), 0.5, 0.12);
                hit.add(t.getUuid());
                Vec3d at = t.getPos().add(0, t.getHeight() * 0.6, 0);
                world.spawnParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.25, 0.25, 0.25, 0.2);
                world.spawnParticles(Fx.ORANGE, at.x, at.y, at.z, 10, 0.25, 0.25, 0.25, 0);
            }
            world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.2f, 0.9f);
        } else if (age < c.divergentDelayTicks) {
            // lingering orange sparks show the delayed impact is building
            for (UUID id : hit) {
                Entity e = world.getEntity(id);
                if (e != null && e.isAlive()) {
                    world.spawnParticles(Fx.ORANGE, e.getX(), e.getY() + e.getHeight() * 0.6, e.getZ(), 2, 0.3, 0.3, 0.3, 0);
                }
            }
        } else if (age == c.divergentDelayTicks) {
            for (UUID id : hit) {
                Entity e = world.getEntity(id);
                if (!(e instanceof LivingEntity le) || !le.isAlive()) continue;
                CombatUtil.damage(owner, le, c.divergentDamage2, true);
                CombatUtil.knockback(le, owner.getPos(), 2.0, 0.5);
                Vec3d at = le.getPos().add(0, le.getHeight() * 0.5, 0);
                world.spawnParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 25, 0.4, 0.4, 0.4, 0.3);
                Fx.ring(world, le.getPos().add(0, 0.2, 0), 1.6, 20, Fx.CRIMSON);
                world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.9f, 1.4f);
                world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 1.4f, 0.7f);
            }
            return true;
        }
        age++;
        return false;
    }
}
