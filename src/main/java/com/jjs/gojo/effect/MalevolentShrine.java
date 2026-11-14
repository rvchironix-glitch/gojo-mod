package com.jjs.gojo.effect;

import com.jjs.gojo.GojoConfig;
import com.jjs.gojo.GojoMod;
import com.jjs.gojo.Ultimate;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Malevolent Shrine: Sukuna's domain. A short cast (Sukuna stands still), then a shrine of crimson and bone
 * rises in the middle of a large area and every living thing inside is cut by relentless slashes at fixed
 * intervals (one hit per target per interval, never duplicated). No blocks are touched. It ends after the
 * configured duration, or immediately if Sukuna dies, disconnects, changes dimension or loses the transformation.
 */
public class MalevolentShrine implements ServerEffect {
    private static final int CAST = 30;
    private static final Set<UUID> ACTIVE = new HashSet<>();

    private final ServerWorld world;
    private final UUID ownerId;
    private final Vec3d center;
    private final double radius;
    private int age;

    private MalevolentShrine(ServerPlayerEntity owner) {
        this.world = owner.getServerWorld();
        this.ownerId = owner.getUuid();
        this.center = owner.getPos();
        this.radius = GojoConfig.get().shrineRadius;
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 2.0f, 0.5f);
        world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 2.0f, 0.5f);
    }

    public static boolean start(ServerPlayerEntity owner) {
        if (!ACTIVE.add(owner.getUuid())) return false;
        EffectManager.add(new MalevolentShrine(owner));
        return true;
    }

    public static void clearAll() {
        ACTIVE.clear();
    }

    @Override
    public boolean tick() {
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
        if (owner == null || !owner.isAlive() || owner.getServerWorld() != world || !Ultimate.isSukuna(owner)) {
            end(owner);
            return true;
        }
        GojoConfig c = GojoConfig.get();
        long now = world.getServer().getTicks();

        if (age < CAST) {
            GojoMod.state(owner).channelUntil = now + 3;
            owner.setVelocity(0, Math.min(owner.getVelocity().y, 0), 0);
            owner.velocityModified = true;
            double t = age / (double) CAST;
            Fx.ring(world, center.add(0, 0.1, 0), radius * t, (int) (20 + 40 * t), Fx.CRIMSON);
            world.spawnParticles(Fx.BLACK, owner.getX(), owner.getY() + 1, owner.getZ(), 10, 0.5, 0.9, 0.5, 0.01);
            if (age % 6 == 0) {
                world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 2.0f, 0.5f + (float) t);
            }
        } else {
            if (age == CAST) {
                GojoMod.state(owner).channelUntil = 0;
                world.spawnParticles(ParticleTypes.FLASH, center.x, center.y + 1, center.z, 3, 1, 1, 1, 0);
                world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 4.0f, 0.5f);
                Fx.broadcastTitle(world, center, 64, Text.literal("DOMAIN EXPANSION").formatted(Formatting.DARK_RED, Formatting.BOLD),
                        Text.literal("Malevolent Shrine").formatted(Formatting.WHITE));
            }
            if (age >= CAST + Math.max(20, c.shrineDurationTicks)) {
                world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 3.0f, 0.6f);
                end(owner);
                return true;
            }
            shrine();
            slashes();
            int interval = Math.max(2, c.shrineIntervalTicks);
            if ((age - CAST) % interval == interval - 1) damageTick(owner, c);
        }
        age++;
        return false;
    }

    /** The shrine itself: four pillars, tiered roofs, a spire and a ground ring, drawn in particles. */
    private void shrine() {
        if (age % 2 != 0) return;
        double s = 3.5;
        double[][] corners = {{s, s}, {s, -s}, {-s, s}, {-s, -s}};
        for (double[] k : corners) {
            Fx.line(world, center.add(k[0], 0, k[1]), center.add(k[0], 7, k[1]), 0.7, Fx.CRIMSON);
        }
        for (int tier = 0; tier < 3; tier++) {
            double r = 6.0 - tier * 1.8;
            double y = 7 + tier * 1.2;
            Fx.ring(world, center.add(0, y, 0), r, (int) (r * 7), tier == 1 ? Fx.BONE : Fx.CRIMSON);
        }
        Fx.line(world, center.add(0, 9.4, 0), center.add(0, 12, 0), 0.5, Fx.BONE);
        Fx.ring(world, center.add(0, 0.1, 0), radius, 70, Fx.CRIMSON);
        Fx.ring(world, center.add(0, 0.1, 0), radius * 0.55, 40, Fx.BLACK);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + 0.5, center.z, 4, radius * 0.4, 0.2, radius * 0.4, 0.01);
    }

    private void slashes() {
        for (int i = 0; i < 8; i++) {
            double a = world.getRandom().nextDouble() * Math.PI * 2;
            double d = Math.sqrt(world.getRandom().nextDouble()) * radius;
            Vec3d p = center.add(Math.cos(a) * d, 0.3 + world.getRandom().nextDouble() * 4.0, Math.sin(a) * d);
            double b = world.getRandom().nextDouble() * Math.PI;
            Vec3d half = new Vec3d(Math.cos(b) * 1.8, (world.getRandom().nextDouble() - 0.5) * 2.0, Math.sin(b) * 1.8);
            Fx.line(world, p.subtract(half), p.add(half), 0.4, Fx.CRIMSON);
            if (i < 3) world.spawnParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    /** One damage interval. Each target appears once in the list, so it is hit once per interval. */
    private void damageTick(ServerPlayerEntity owner, GojoConfig c) {
        for (LivingEntity t : Fx.around(owner, center.add(0, 1, 0), radius)) {
            if (t instanceof ServerPlayerEntity sp && sp.isCreative()) continue;
            CombatUtil.damage(owner, t, c.shrineDamage, true);
            Vec3d at = t.getPos().add(0, t.getHeight() * 0.5, 0);
            world.spawnParticles(Fx.CRIMSON, at.x, at.y, at.z, 20, 0.4, 0.5, 0.4, 0);
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.3, 0.3, 0.3, 0);
        }
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 2.0f, 0.5f);
    }

    private void end(ServerPlayerEntity owner) {
        ACTIVE.remove(ownerId);
        if (owner != null) GojoMod.state(owner).channelUntil = 0;
    }
}
