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

/** Yuji's Rapid Strikes: a quick one-two combination that ends in a launching uppercut. */
public class YujiStrikes implements ServerEffect {
    private final ServerWorld world;
    private final UUID ownerId;
    private int age;

    public YujiStrikes(ServerPlayerEntity owner) {
        this.world = owner.getServerWorld();
        this.ownerId = owner.getUuid();
    }

    @Override
    public boolean tick() {
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
        if (owner == null || !owner.isAlive()) return true;
        GojoConfig c = GojoConfig.get();

        if (age == 0) CombatUtil.lunge(owner, 0.45);
        if (age % 2 == 0) {
            int index = age / 2;
            boolean last = index >= c.strikeCount - 1;
            strike(owner, c, index, last);
            if (last) return true;
        }
        age++;
        return false;
    }

    private void strike(ServerPlayerEntity owner, GojoConfig c, int index, boolean last) {
        owner.swingHand(index % 2 == 0 ? Hand.MAIN_HAND : Hand.OFF_HAND, true);
        Vec3d look = owner.getRotationVec(1.0f).normalize();
        List<LivingEntity> targets = CombatUtil.inFront(owner, 3.2, 0.5);

        if (targets.isEmpty()) {
            world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE, SoundCategory.PLAYERS, 0.8f, 1.3f);
            return;
        }
        for (LivingEntity t : targets) {
            CombatUtil.damage(owner, t, last ? c.strikeFinisherDamage : c.strikeDamage, true);
            Vec3d at = t.getPos().add(0, t.getHeight() * 0.6, 0);
            world.spawnParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 5, 0.25, 0.25, 0.25, 0.2);
            world.spawnParticles(Fx.ORANGE, at.x, at.y, at.z, 6, 0.25, 0.25, 0.25, 0);
            if (last) {
                t.setVelocity(look.x * 0.4, 0.9, look.z * 0.4); // uppercut launch
                t.velocityModified = true;
                world.spawnParticles(ParticleTypes.CLOUD, at.x, at.y - 0.4, at.z, 12, 0.2, 0.4, 0.2, 0.1);
                world.spawnParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            } else {
                t.setVelocity(look.x * 0.1, Math.max(0.0, t.getVelocity().y * 0.3), look.z * 0.1);
                t.velocityModified = true;
            }
        }
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                last ? SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK : SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,
                SoundCategory.PLAYERS, last ? 1.4f : 0.9f, last ? 0.9f : 1.1f + 0.03f * index);
    }
}
