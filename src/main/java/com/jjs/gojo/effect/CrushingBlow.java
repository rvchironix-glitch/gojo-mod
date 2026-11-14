package com.jjs.gojo.effect;

import com.jjs.gojo.GojoConfig;
import com.jjs.gojo.GojoMod;
import com.jjs.gojo.PlayerState;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/** Crushing Blow: leap, then slam both fists down in front of you, cracking the ground. */
public class CrushingBlow implements ServerEffect {
    private final ServerWorld world;
    private final UUID ownerId;
    private int age;

    public CrushingBlow(ServerPlayerEntity owner) {
        this.world = owner.getServerWorld();
        this.ownerId = owner.getUuid();
    }

    @Override
    public boolean tick() {
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
        if (owner == null || !owner.isAlive()) return true;
        Vec3d look = owner.getRotationVec(1.0f).multiply(1, 0, 1);
        look = look.lengthSquared() < 1.0E-4 ? new Vec3d(0, 0, 1) : look.normalize();

        if (age == 0) {
            owner.setVelocity(look.x * 0.25, 0.55, look.z * 0.25);
            owner.velocityModified = true;
            owner.swingHand(Hand.MAIN_HAND, true);
            owner.swingHand(Hand.OFF_HAND, true);
            // no fall damage from our own slam
            PlayerState s = GojoMod.state(owner);
            s.fallImmuneUntil = world.getServer().getTicks() + 40;
            world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0f, 0.6f);
        } else if (age < 7) {
            Vec3d top = owner.getPos().add(0, 2.4, 0);
            world.spawnParticles(Fx.ORANGE, top.x, top.y, top.z, 4, 0.4, 0.2, 0.4, 0);
        } else if (age == 7) {
            owner.setVelocity(look.x * 0.3, -1.1, look.z * 0.3);
            owner.velocityModified = true;
        } else if (age == 9) {
            impact(owner, look);
            return true;
        }
        age++;
        return false;
    }

    private void impact(ServerPlayerEntity owner, Vec3d look) {
        GojoConfig c = GojoConfig.get();
        Vec3d center = owner.getPos().add(look.multiply(1.6));
        for (LivingEntity t : Fx.around(owner, center.add(0, 0.5, 0), 3.8)) {
            CombatUtil.damage(owner, t, c.crushingDamage, true);
            CombatUtil.knockback(t, center, 0.8, 0.25);
            t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 25, 3, false, true, true));
        }

        BlockState ground = world.getBlockState(BlockPos.ofFloored(center).down());
        if (ground.isAir()) ground = Blocks.STONE.getDefaultState();
        BlockStateParticleEffect debris = new BlockStateParticleEffect(ParticleTypes.BLOCK, ground);
        world.spawnParticles(debris, center.x, center.y + 0.1, center.z, 70, 1.8, 0.2, 1.8, 0.25);
        Fx.ring(world, center.add(0, 0.15, 0), 2.0, 24, Fx.CRIMSON);
        Fx.ring(world, center.add(0, 0.15, 0), 3.5, 36, debris);
        world.spawnParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.5, center.z, 2, 0.5, 0.2, 0.5, 0);
        world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 1.2f, 0.5f);
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.2f, 0.8f);
    }
}
