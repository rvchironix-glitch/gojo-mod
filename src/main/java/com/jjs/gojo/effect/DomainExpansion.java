package com.jjs.gojo.effect;

import com.jjs.gojo.Exhaustion;
import com.jjs.gojo.GojoConfig;
import com.jjs.gojo.GojoMod;
import com.jjs.gojo.PlayerState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 0.2 Domain (Unlimited Void style). Gojo stands still during a short cast, then every valid mob and player in
 * range (not Gojo, not spectators, not creative players) is stunned for exactly 15 seconds. The void shell stays
 * for the whole stun. If Gojo disconnects, dies or changes dimension, every stun is cancelled at once.
 * Gojo is EXHAUSTED when the domain ends (not when he dies).
 */
public class DomainExpansion implements ServerEffect {
    private static final int CAST = 40;
    private static final DustParticleEffect WHITE = new DustParticleEffect(new Vector3f(0.95f, 0.95f, 1.0f), 2.0f);
    private static final DustParticleEffect VOID = new DustParticleEffect(new Vector3f(0.25f, 0.05f, 0.45f), 2.2f);
    private static final Set<UUID> ACTIVE = new HashSet<>();

    private final ServerWorld world;
    private final UUID ownerId;
    private final Set<UUID> stunned = new HashSet<>();
    private final Vec3d center;
    private final double radius;
    private int age;

    private DomainExpansion(ServerPlayerEntity owner) {
        this.world = owner.getServerWorld();
        this.ownerId = owner.getUuid();
        this.center = owner.getPos().add(0, 1.0, 0);
        this.radius = GojoConfig.get().domainRadius;
        world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 3.0f, 0.7f);
    }

    public static boolean isActive(UUID id) {
        return ACTIVE.contains(id);
    }

    public static boolean start(ServerPlayerEntity owner) {
        if (!ACTIVE.add(owner.getUuid())) return false;
        EffectManager.add(new DomainExpansion(owner));
        return true;
    }

    public static void clearAll() {
        ACTIVE.clear();
    }

    @Override
    public boolean tick() {
        MinecraftServer server = world.getServer();
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(ownerId);
        if (owner == null || !owner.isAlive() || owner.getServerWorld() != world) {
            finish(owner, owner != null && owner.isAlive());
            return true;
        }
        PlayerState st = GojoMod.state(owner);
        long now = server.getTicks();

        if (age < CAST) {
            st.channelUntil = now + 3; // casting: no other ability
            owner.setVelocity(0, Math.min(owner.getVelocity().y, 0), 0);
            owner.velocityModified = true;
            double t = age / (double) CAST;
            // hand-sign aura + expanding shell
            world.spawnParticles(WHITE, owner.getX(), owner.getY() + 1, owner.getZ(), 12, 0.6, 0.9, 0.6, 0);
            shell(radius * t, VOID, 40);
            if (age % 8 == 0) {
                world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 3.0f, 0.5f + (float) t);
            }
        } else if (age == CAST) {
            st.channelUntil = 0;
            expand(owner);
        } else {
            // keep the void shell + sparkles on the stunned for the full stun
            if (age % 2 == 0) shell(radius, VOID, 30);
            if (age % 4 == 0) {
                for (UUID id : stunned) {
                    Entity e = world.getEntity(id);
                    if (e != null) {
                        world.spawnParticles(WHITE, e.getX(), e.getBodyY(0.5), e.getZ(), 4, 0.3, 0.5, 0.3, 0);
                    }
                }
            }
            // a stunned target that left this dimension is released
            stunned.removeIf(id -> {
                ServerPlayerEntity sp = server.getPlayerManager().getPlayer(id);
                if (sp != null && sp.getServerWorld() != world) {
                    Stun.cancel(server, id);
                    return true;
                }
                return false;
            });
            if (age >= CAST + Stun.DURATION_TICKS) {
                finish(owner, true);
                return true;
            }
        }
        age++;
        return false;
    }

    private void expand(ServerPlayerEntity owner) {
        MinecraftServer server = world.getServer();
        world.spawnParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 4, 1, 1, 1, 0);
        for (double r = 3; r <= radius; r += 3) Fx.ring(world, center, r, (int) (r * 6), WHITE);
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 4.0f, 0.6f);
        Fx.broadcastTitle(world, center, 64, Text.literal("DOMAIN EXPANSION").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
                Text.literal("0.2 Unlimited Void").formatted(Formatting.WHITE));
        for (Entity e : world.getOtherEntities(owner, Box.of(center, radius * 2, radius * 2, radius * 2),
                x -> x instanceof LivingEntity && x.isAlive() && !x.isSpectator())) {
            if (e instanceof ServerPlayerEntity sp && sp.isCreative()) continue;
            if (e.getPos().distanceTo(center) > radius) continue;
            Stun.apply(server, (LivingEntity) e, Stun.DURATION_TICKS);
            stunned.add(e.getUuid());
        }
    }

    private void shell(double r, DustParticleEffect e, int n) {
        if (r < 0.5) return;
        for (int i = 0; i < n; i++) {
            Vec3d v = new Vec3d(world.getRandom().nextGaussian(), world.getRandom().nextGaussian(), world.getRandom().nextGaussian());
            if (v.lengthSquared() < 1.0E-4) continue;
            Vec3d p = center.add(v.normalize().multiply(r));
            world.spawnParticles(e, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    private void finish(ServerPlayerEntity owner, boolean exhaust) {
        MinecraftServer server = world.getServer();
        for (UUID id : stunned) Stun.cancel(server, id);
        stunned.clear();
        ACTIVE.remove(ownerId);
        if (owner != null) GojoMod.state(owner).channelUntil = 0;
        // Disconnecting must not dodge the penalty; dying does not add one.
        boolean offline = owner == null;
        if (exhaust || offline) Exhaustion.apply(server, ownerId);
        world.spawnParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 2, 1, 1, 1, 0);
    }
}
