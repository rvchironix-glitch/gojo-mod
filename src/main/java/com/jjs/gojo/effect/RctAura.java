package com.jjs.gojo.effect;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.UUID;

/** Reverse Cursed Technique: rising double-helix of positive energy around the player. */
public class RctAura implements ServerEffect {
    private static final int DURATION = 24;

    private final ServerWorld world;
    private final UUID ownerId;
    private int age;

    public RctAura(ServerPlayerEntity owner) {
        this.world = owner.getServerWorld();
        this.ownerId = owner.getUuid();
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.9f);
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.0f, 2.0f);
        world.spawnParticles(ParticleTypes.HEART, owner.getX(), owner.getY() + 1.8, owner.getZ(), 4, 0.4, 0.2, 0.4, 0.0);
    }

    @Override
    public boolean tick() {
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
        if (owner == null || !owner.isAlive() || age >= DURATION) return true;

        double height = 0.1 + (age / (double) DURATION) * 1.9;
        for (int k = 0; k < 2; k++) {
            double a = age * 0.55 + k * Math.PI;
            double x = owner.getX() + Math.cos(a) * 0.9;
            double z = owner.getZ() + Math.sin(a) * 0.9;
            double y = owner.getY() + height;
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 1, 0.02, 0.02, 0.02, 0);
            world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.0, 0.05, 0.0, 0.01);
        }
        world.spawnParticles(ParticleTypes.GLOW, owner.getX(), owner.getY() + 1.0, owner.getZ(), 2, 0.4, 0.6, 0.4, 0.02);

        if (age % 8 == 0) {
            world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                    SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 1.0f + age * 0.04f);
        }
        age++;
        return false;
    }
}
