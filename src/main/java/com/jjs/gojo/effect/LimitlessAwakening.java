package com.jjs.gojo.effect;

import com.jjs.gojo.GojoMod;
import com.jjs.gojo.PlayerState;
import com.jjs.gojo.Ultimate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

/**
 * Gojo's Limitless Awakened: a 2 second transformation cinematic followed by a visible Six Eyes aura.
 * The server clock in PlayerState.awakenedUntil decides when it ends; this effect only renders it.
 * Ends (and resets the meter) on timer expiry, death, disconnect or server stop.
 */
public class LimitlessAwakening implements ServerEffect {
    private static final int CINEMATIC = 40;
    private static final DustParticleEffect BLUE = new DustParticleEffect(new Vector3f(0.15f, 0.5f, 1.0f), 1.4f);
    private static final DustParticleEffect EYE = new DustParticleEffect(new Vector3f(0.6f, 0.95f, 1.0f), 0.7f);

    private final ServerWorld world;
    private final UUID ownerId;
    private int age;

    public LimitlessAwakening(ServerPlayerEntity owner) {
        this.world = owner.getServerWorld();
        this.ownerId = owner.getUuid();

        owner.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 50, 20));
        owner.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("Six Eyes unleashed").formatted(Formatting.AQUA)));
        owner.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("LIMITLESS AWAKENED").formatted(Formatting.BLUE, Formatting.BOLD)));

        for (ServerPlayerEntity other : world.getPlayers(p -> p != owner && p.squaredDistanceTo(owner) < 64 * 64)) {
            other.sendMessage(Text.literal(owner.getName().getString() + ": LIMITLESS AWAKENED").formatted(Formatting.AQUA), true);
        }

        // brief invulnerable, rooted cinematic
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, CINEMATIC, 6, false, false, false));
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, CINEMATIC + 10, 4, false, false, false));

        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 1.0f, 1.5f);
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.5f, 0.7f);
    }

    @Override
    public boolean tick() {
        MinecraftServer server = world.getServer();
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(ownerId);
        PlayerState st = GojoMod.stateOrNull(ownerId);
        if (owner == null || !owner.isAlive() || st == null || server.getTicks() >= st.awakenedUntil) {
            Ultimate.end(server, ownerId);
            return true;
        }

        if (age < CINEMATIC) cinematic(owner); else aura(owner);
        if (age % 40 == 0) {
            // outline visible to other players; refreshed so it vanishes within 3s of the end
            owner.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0, false, false, false));
        }
        age++;
        return false;
    }

    private void cinematic(ServerPlayerEntity owner) {
        double t = age / (double) CINEMATIC;
        double radius = 0.8 + t * 3.0;
        double y = owner.getY() + t * 2.2;
        for (int i = 0; i < 12; i++) {
            double a = i * (Math.PI * 2 / 12) + age * 0.4;
            world.spawnParticles(BLUE, owner.getX() + Math.cos(a) * radius, y, owner.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, owner.getX(), owner.getY() + 0.2, owner.getZ(), 4, 0.3, 0.1, 0.3, 0.12);

        if (age == 30) {
            Vec3d eye = owner.getEyePos();
            world.spawnParticles(ParticleTypes.FLASH, eye.x, eye.y, eye.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.END_ROD, eye.x, eye.y, eye.z, 30, 0.3, 0.3, 0.3, 0.2);
            world.playSound(null, eye.x, eye.y, eye.z, SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 1.2f, 1.6f);
        }
        if (age == CINEMATIC - 1) shockwave(owner);
    }

    private void shockwave(ServerPlayerEntity owner) {
        for (int i = 0; i < 48; i++) {
            double a = i * (Math.PI * 2 / 48);
            world.spawnParticles(BLUE, owner.getX() + Math.cos(a) * 5, owner.getY() + 0.3, owner.getZ() + Math.sin(a) * 5, 2, 0.1, 0.2, 0.1, 0);
        }
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, owner.getX(), owner.getY() + 1, owner.getZ(), 1, 0, 0, 0, 0);
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.5f, 1.4f);
        // knockback only, no damage
        List<Entity> near = world.getOtherEntities(owner, Box.of(owner.getPos(), 12, 6, 12),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator());
        for (Entity e : near) {
            if (e.getPos().distanceTo(owner.getPos()) <= 6) {
                CombatUtil.knockback((LivingEntity) e, owner.getPos(), 1.3, 0.35);
            }
        }
    }

    private void aura(ServerPlayerEntity owner) {
        if (age % 2 == 0) {
            for (int i = 0; i < 4; i++) {
                double a = world.getRandom().nextDouble() * Math.PI * 2;
                double h = world.getRandom().nextDouble() * 1.9;
                world.spawnParticles(BLUE, owner.getX() + Math.cos(a) * 0.9, owner.getY() + h, owner.getZ() + Math.sin(a) * 0.9, 1, 0, 0, 0, 0);
            }
        }
        if (age % 4 == 0) {
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, owner.getX(), owner.getY() + 0.1, owner.getZ(), 2, 0.4, 0.05, 0.4, 0.04);
        }
        if (age % 60 == 0) {
            world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                    SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 1.0f, 1.6f);
        }
        if (age % 2 == 0) sixEyes(owner);
    }

    /** Glowing eyes, sent to everyone except the owner so they don't clutter the first-person view. */
    private void sixEyes(ServerPlayerEntity owner) {
        Vec3d look = owner.getRotationVec(1.0f).normalize();
        Vec3d right = new Vec3d(-look.z, 0, look.x);
        if (right.lengthSquared() < 1.0E-4) return;
        right = right.normalize();
        Vec3d base = owner.getEyePos().add(look.multiply(0.3));
        for (ServerPlayerEntity viewer : world.getPlayers(p -> p != owner && p.squaredDistanceTo(owner) < 48 * 48)) {
            for (int side = -1; side <= 1; side += 2) {
                Vec3d p = base.add(right.multiply(0.12 * side));
                world.spawnParticles(viewer, EYE, true, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                world.spawnParticles(viewer, ParticleTypes.END_ROD, true, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
    }
}
