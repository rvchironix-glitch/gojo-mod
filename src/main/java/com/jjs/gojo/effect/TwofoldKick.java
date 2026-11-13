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

/** Twofold Kick: a lunging launcher kick followed by a heavy second kick. */
public class TwofoldKick implements ServerEffect {
    private final ServerWorld world;
    private final UUID ownerId;
    private int age;

    public TwofoldKick(ServerPlayerEntity owner) {
        this.world = owner.getServerWorld();
        this.ownerId = owner.getUuid();
    }

    @Override
    public boolean tick() {
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
        if (owner == null || !owner.isAlive()) return true;
        GojoConfig c = GojoConfig.get();

        if (age == 0) {
            kick(owner, c, false);
        } else if (age == c.kickGapTicks) {
            kick(owner, c, true);
            return true;
        }
        age++;
        return false;
    }

    private void kick(ServerPlayerEntity owner, GojoConfig c, boolean second) {
        owner.swingHand(second ? Hand.OFF_HAND : Hand.MAIN_HAND, true);
        CombatUtil.lunge(owner, second ? 0.75 : 0.6);

        Vec3d look = owner.getRotationVec(1.0f).normalize();
        Vec3d sweep = owner.getPos().add(0, 0.9, 0).add(look.multiply(1.6));
        world.spawnParticles(ParticleTypes.SWEEP_ATTACK, sweep.x, sweep.y, sweep.z, 1, 0, 0, 0, 0);

        List<LivingEntity> targets = CombatUtil.inFront(owner, c.kickReach, 0.4);
        if (targets.isEmpty()) {
            world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, second ? 0.8f : 1.1f);
            return;
        }

        for (LivingEntity t : targets) {
            CombatUtil.damage(owner, t, second ? c.kickDamage2 : c.kickDamage1, true);
            Vec3d at = t.getPos().add(0, t.getHeight() * 0.5, 0);
            world.spawnParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.3);
            if (second) {
                CombatUtil.knockback(t, owner.getPos(), 2.0, 0.5);
                world.spawnParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            } else {
                // first kick pops the target up so the second connects
                t.setVelocity(look.x * 0.3, 0.45, look.z * 0.3);
                t.velocityModified = true;
            }
        }
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                second ? SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK : SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,
                SoundCategory.PLAYERS, 1.3f, second ? 0.8f : 1.0f);
    }
}
