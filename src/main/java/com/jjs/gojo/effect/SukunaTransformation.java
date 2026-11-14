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

import java.util.List;
import java.util.UUID;

/**
 * Binding Vow: Sukuna Awakened. A 2 second cinematic, then Sukuna's markings and extra eyes are shown
 * to everyone nearby for exactly 60 seconds. The server clock (PlayerState.awakenedUntil) decides when it
 * ends; this effect only renders it. Ends and resets the meter on expiry, death, disconnect or server stop.
 *
 * The player model/skin itself is not replaced (that needs client rendering mixins and a skin asset);
 * the transformation is shown with markings, eyes, aura, outline and the title.
 */
public class SukunaTransformation implements ServerEffect {
    private static final int CINEMATIC = 40;

    private final ServerWorld world;
    private final UUID ownerId;
    private int age;

    public SukunaTransformation(ServerPlayerEntity owner) {
        this.world = owner.getServerWorld();
        this.ownerId = owner.getUuid();

        owner.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 50, 20));
        owner.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("Sukuna awakened").formatted(Formatting.DARK_RED)));
        owner.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("BINDING VOW").formatted(Formatting.RED, Formatting.BOLD)));

        for (ServerPlayerEntity other : world.getPlayers(p -> p != owner && p.squaredDistanceTo(owner) < 64 * 64)) {
            other.sendMessage(Text.literal(owner.getName().getString() + ": BINDING VOW").formatted(Formatting.DARK_RED), true);
        }

        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, CINEMATIC, 6, false, false, false));
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, CINEMATIC + 10, 4, false, false, false));

        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.PLAYERS, 1.0f, 0.7f);
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.8f, 0.6f);
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
            owner.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0, false, false, false));
        }
        age++;
        return false;
    }

    private void cinematic(ServerPlayerEntity owner) {
        double t = age / (double) CINEMATIC;
        double radius = 3.0 - t * 2.0; // contracting ring: the curse gathers into the body
        Fx.ring(world, owner.getPos().add(0, 0.2 + t * 2.0, 0), radius, 14, Fx.BLACK);
        Fx.ring(world, owner.getPos().add(0, 0.2 + t * 1.6, 0), radius * 0.8, 10, Fx.CRIMSON);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, owner.getX(), owner.getY() + 0.3, owner.getZ(), 3, 0.4, 0.1, 0.4, 0.05);

        if (age == 30) {
            Vec3d eye = owner.getEyePos();
            world.spawnParticles(ParticleTypes.FLASH, eye.x, eye.y, eye.z, 1, 0, 0, 0, 0);
            world.spawnParticles(Fx.CRIMSON, eye.x, eye.y, eye.z, 30, 0.4, 0.4, 0.4, 0);
            world.playSound(null, eye.x, eye.y, eye.z, SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 1.2f, 0.6f);
        }
        if (age == CINEMATIC - 1) shockwave(owner);
    }

    private void shockwave(ServerPlayerEntity owner) {
        Fx.ring(world, owner.getPos().add(0, 0.3, 0), 5, 48, Fx.CRIMSON);
        Fx.ring(world, owner.getPos().add(0, 0.3, 0), 4, 40, Fx.BLACK);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, owner.getX(), owner.getY() + 1, owner.getZ(), 1, 0, 0, 0, 0);
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.5f, 0.6f);
        List<Entity> near = world.getOtherEntities(owner, Box.of(owner.getPos(), 12, 6, 12),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator());
        for (Entity e : near) {
            if (e.getPos().distanceTo(owner.getPos()) <= 6) {
                CombatUtil.knockback((LivingEntity) e, owner.getPos(), 1.3, 0.35); // knockback only
            }
        }
    }

    private void aura(ServerPlayerEntity owner) {
        if (age % 2 == 0) {
            for (int i = 0; i < 3; i++) {
                double a = world.getRandom().nextDouble() * Math.PI * 2;
                double h = world.getRandom().nextDouble() * 1.9;
                world.spawnParticles(Fx.CRIMSON, owner.getX() + Math.cos(a) * 0.9, owner.getY() + h, owner.getZ() + Math.sin(a) * 0.9, 1, 0, 0, 0, 0);
            }
        }
        if (age % 4 == 0) {
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, owner.getX(), owner.getY() + 0.1, owner.getZ(), 1, 0.4, 0.05, 0.4, 0.01);
        }
        if (age % 3 == 0) markings(owner);
    }

    /** Sukuna's markings and four eyes, sent to everyone except the owner (so the first-person view stays clear). */
    private void markings(ServerPlayerEntity owner) {
        double yaw = Math.toRadians(owner.bodyYaw);
        Vec3d fwd = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3d right = new Vec3d(-fwd.z, 0, fwd.x);
        Vec3d feet = owner.getPos();

        Vec3d head = owner.getEyePos().add(owner.getRotationVec(1.0f).multiply(0.28));
        Vec3d headRight = owner.getRotationVec(1.0f).multiply(1, 0, 1);
        headRight = headRight.lengthSquared() < 1.0E-4 ? right : new Vec3d(-headRight.z, 0, headRight.x).normalize();

        for (ServerPlayerEntity viewer : Fx.viewers(world, owner)) {
            for (int side = -1; side <= 1; side += 2) {
                // main eye + extra lower eye
                Vec3d eye = head.add(headRight.multiply(0.12 * side));
                world.spawnParticles(viewer, Fx.CRIMSON, true, eye.x, eye.y, eye.z, 1, 0, 0, 0, 0);
                world.spawnParticles(viewer, Fx.CRIMSON, true, eye.x, eye.y - 0.1, eye.z, 1, 0, 0, 0, 0);
                // cheek stripes
                Vec3d cheek = head.add(headRight.multiply(0.2 * side)).add(0, -0.18, 0);
                world.spawnParticles(viewer, Fx.BLACK, true, cheek.x, cheek.y, cheek.z, 1, 0, 0.03, 0, 0);
                // upper-arm bands
                Vec3d arm = feet.add(right.multiply(0.42 * side)).add(0, 1.15, 0);
                world.spawnParticles(viewer, Fx.BLACK, true, arm.x, arm.y, arm.z, 1, 0.04, 0.04, 0.04, 0);
            }
            // chest lines
            for (int row = 0; row < 3; row++) {
                Vec3d chest = feet.add(fwd.multiply(0.3)).add(0, 1.0 + row * 0.12, 0);
                world.spawnParticles(viewer, Fx.BLACK, true, chest.x, chest.y, chest.z, 1, 0.12, 0.0, 0.12, 0);
            }
        }
    }
}
