package com.jjs.gojo.effect;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Shared particle and hit-detection helpers for the Yuji / Sukuna moves. */
public final class Fx {
    public static final DustParticleEffect BLACK = new DustParticleEffect(new Vector3f(0.05f, 0.0f, 0.08f), 1.6f);
    public static final DustParticleEffect CRIMSON = new DustParticleEffect(new Vector3f(0.85f, 0.05f, 0.1f), 1.5f);
    public static final DustParticleEffect ORANGE = new DustParticleEffect(new Vector3f(1.0f, 0.55f, 0.1f), 1.3f);
    public static final DustParticleEffect BONE = new DustParticleEffect(new Vector3f(0.95f, 0.9f, 0.85f), 1.0f);

    private Fx() {}

    /** Roughly where the player's fist is. */
    public static Vec3d fist(ServerPlayerEntity p) {
        return p.getEyePos().add(p.getRotationVec(1.0f).multiply(1.4)).subtract(0, 0.35, 0);
    }

    public static void ring(ServerWorld w, Vec3d c, double r, int n, ParticleEffect e) {
        for (int i = 0; i < n; i++) {
            double a = i * (Math.PI * 2 / n);
            w.spawnParticles(e, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    public static void line(ServerWorld w, Vec3d a, Vec3d b, double step, ParticleEffect e) {
        Vec3d d = b.subtract(a);
        int n = Math.max(1, (int) Math.ceil(d.length() / step));
        for (int i = 0; i <= n; i++) {
            Vec3d p = a.add(d.multiply((double) i / n));
            w.spawnParticles(e, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    /** Living entities (not the owner) whose centre is within {@code r} of {@code c}. */
    public static List<LivingEntity> around(ServerPlayerEntity owner, Vec3d c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : owner.getWorld().getOtherEntities(owner, Box.of(c, r * 2, r * 2, r * 2),
                x -> x instanceof LivingEntity && x.isAlive() && !x.isSpectator())) {
            if (e.getPos().add(0, e.getHeight() / 2.0, 0).distanceTo(c) <= r + e.getWidth() / 2.0) {
                out.add((LivingEntity) e);
            }
        }
        return out;
    }

    /** Living entities (not the owner) within {@code radius} of the segment a-b. */
    public static List<LivingEntity> alongSegment(ServerPlayerEntity owner, Vec3d a, Vec3d b, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        Vec3d ab = b.subtract(a);
        double len2 = ab.lengthSquared();
        Box box = new Box(a, b).expand(radius + 1.0);
        for (Entity e : owner.getWorld().getOtherEntities(owner, box,
                x -> x instanceof LivingEntity && x.isAlive() && !x.isSpectator())) {
            Vec3d c = e.getPos().add(0, e.getHeight() / 2.0, 0);
            double t = len2 < 1.0E-6 ? 0 : Math.max(0, Math.min(1, c.subtract(a).dotProduct(ab) / len2));
            if (c.distanceTo(a.add(ab.multiply(t))) <= radius + e.getWidth() / 2.0) {
                out.add((LivingEntity) e);
            }
        }
        return out;
    }

    /** Players who should see body markings / eyes (everyone nearby except the owner). */
    public static List<ServerPlayerEntity> viewers(ServerWorld w, ServerPlayerEntity owner) {
        return w.getPlayers(p -> p != owner && p.squaredDistanceTo(owner) < 48 * 48);
    }
}
