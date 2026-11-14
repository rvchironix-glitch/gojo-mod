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

/** Cleave: a wide crimson crescent slash in front of Sukuna that adapts to nothing but raw power. */
public class SukunaCleave implements ServerEffect {
    private final ServerWorld world;
    private final ServerPlayerEntity owner;
    private boolean done;

    public SukunaCleave(ServerPlayerEntity owner) {
        this.world = owner.getServerWorld();
        this.owner = owner;
    }

    @Override
    public boolean tick() {
        if (done || !owner.isAlive()) return true;
        done = true;
        GojoConfig c = GojoConfig.get();

        owner.swingHand(Hand.MAIN_HAND, true);
        CombatUtil.lunge(owner, 0.5);

        Vec3d look = owner.getRotationVec(1.0f).normalize();
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        flat = flat.lengthSquared() < 1.0E-4 ? new Vec3d(0, 0, 1) : flat.normalize();
        Vec3d right = new Vec3d(-flat.z, 0, flat.x);
        Vec3d origin = owner.getPos().add(0, 1.2, 0);

        // crescent arc, tilted a little for a diagonal cut
        for (int deg = -70; deg <= 70; deg += 7) {
            double a = Math.toRadians(deg);
            Vec3d p = origin.add(flat.multiply(Math.cos(a) * 2.6)).add(right.multiply(Math.sin(a) * 2.6))
                    .add(0, Math.sin(a) * 0.5, 0);
            world.spawnParticles(Fx.CRIMSON, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0);
            world.spawnParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.1);
        }
        Vec3d mid = origin.add(flat.multiply(2.0));
        world.spawnParticles(ParticleTypes.SWEEP_ATTACK, mid.x, mid.y, mid.z, 1, 0, 0, 0, 0);

        List<LivingEntity> targets = CombatUtil.inFront(owner, 4.2, 0.2);
        for (LivingEntity t : targets) {
            CombatUtil.damage(owner, t, c.cleaveDamage, true);
            CombatUtil.knockback(t, owner.getPos(), 1.4, 0.2);
            Vec3d at = t.getPos().add(0, t.getHeight() * 0.6, 0);
            world.spawnParticles(Fx.CRIMSON, at.x, at.y, at.z, 20, 0.35, 0.35, 0.35, 0);
            world.spawnParticles(Fx.BLACK, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0);
        }

        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.3f, 0.6f);
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.PLAYERS, 0.5f, 1.6f);
        return true;
    }
}
