package com.jjs.gojo;

import com.jjs.gojo.effect.BlueOrb;
import com.jjs.gojo.effect.EffectManager;
import com.jjs.gojo.effect.HollowPurple;
import com.jjs.gojo.effect.RapidPunches;
import com.jjs.gojo.effect.RedProjectile;
import com.jjs.gojo.effect.TwofoldKick;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GojoMod implements ModInitializer {
    public static final String MOD_ID = "gojomoveset";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Map<UUID, PlayerState> STATES = new ConcurrentHashMap<>();

    public static PlayerState state(ServerPlayerEntity p) {
        return STATES.computeIfAbsent(p.getUuid(), id -> new PlayerState());
    }

    @Override
    public void onInitialize() {
        GojoConfig.load();

        // Ability requests from clients. Everything is validated and executed on the server.
        ServerPlayNetworking.registerGlobalReceiver(GojoNet.ABILITY, (server, player, handler, buf, sender) -> {
            int abilityId = buf.readByte();
            int dir = buf.readByte();
            server.execute(() -> handleAbility(player, abilityId, dir));
        });

        ServerPlayNetworking.registerGlobalReceiver(GojoNet.BLOCK, (server, player, handler, buf, sender) -> {
            boolean wants = buf.readBoolean();
            server.execute(() -> {
                PlayerState s = state(player);
                long now = server.getTicks();
                s.blocking = wants && player.isAlive() && !player.isSpectator() && !s.isGuardBroken(now);
            });
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.player.getUuid()));

        ServerTickEvents.END_SERVER_TICK.register(server -> EffectManager.tick());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            EffectManager.clear();
            STATES.clear();
        });

        // Guard regeneration
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            GojoConfig c = GojoConfig.get();
            if (server.getTicks() % Math.max(1, c.guardRegenIntervalTicks) != 0) return;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                PlayerState s = STATES.get(p.getUuid());
                if (s != null && !s.blocking && s.guard < c.maxGuard && !s.isGuardBroken(server.getTicks())) {
                    s.guard++;
                }
            }
        });

        // Fall damage immunity after dashing + blocking
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(GojoMod::allowDamage);
    }

    private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayerEntity player)) return true;
        PlayerState s = STATES.get(player.getUuid());
        if (s == null) return true;
        long now = player.getServer().getTicks();

        if (source.isOf(DamageTypes.FALL) && now < s.fallImmuneUntil) {
            return false;
        }

        if (!s.blocking || source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.isOf(DamageTypes.FALL)) {
            return true;
        }

        // Only block hits coming from the front
        Entity src = source.getSource() != null ? source.getSource() : source.getAttacker();
        if (src != null) {
            Vec3d toAttacker = src.getPos().subtract(player.getPos()).multiply(1, 0, 1);
            Vec3d look = player.getRotationVec(1.0f).multiply(1, 0, 1);
            if (toAttacker.lengthSquared() > 1.0E-4 && look.lengthSquared() > 1.0E-4
                    && toAttacker.normalize().dotProduct(look.normalize()) < 0.2) {
                return true;
            }
        }

        // Successful block: negate the hit, drain guard
        GojoConfig c = GojoConfig.get();
        s.guard -= (int) Math.ceil(amount * c.guardCostPerDamage);
        var world = player.getServerWorld();
        Vec3d front = player.getPos().add(player.getRotationVec(1.0f).multiply(1, 0, 1).normalize().multiply(0.6)).add(0, 1.2, 0);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, front.x, front.y, front.z, 14, 0.25, 0.25, 0.25, 0.15);
        world.spawnParticles(ParticleTypes.CRIT, front.x, front.y, front.z, 8, 0.2, 0.2, 0.2, 0.2);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0f, 1.2f);

        if (s.guard <= 0) {
            s.guard = 0;
            s.blocking = false;
            s.guardBrokenUntil = now + c.guardBreakTicks;
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_SHIELD_BREAK, SoundCategory.PLAYERS, 1.0f, 0.9f);
        }
        return false;
    }

    private static void handleAbility(ServerPlayerEntity player, int abilityId, int dir) {
        Ability ability = Ability.fromId(abilityId);
        if (ability == null || !player.isAlive() || player.isSpectator()) return;

        PlayerState s = state(player);
        long now = player.getServer().getTicks();
        if (!s.isReady(ability, now)) return;

        boolean fired = switch (ability) {
            case DASH -> doDash(player, s, dir, now);
            case LAPIS_BLUE -> {
                EffectManager.add(new BlueOrb(player));
                yield true;
            }
            case REVERSAL_RED -> {
                EffectManager.add(new RedProjectile(player));
                yield true;
            }
            case RAPID_PUNCHES -> {
                EffectManager.add(new RapidPunches(player));
                yield true;
            }
            case TWOFOLD_KICK -> {
                EffectManager.add(new TwofoldKick(player));
                yield true;
            }
            case R_SPECIAL -> {
                EffectManager.add(new HollowPurple(player));
                yield true;
            }
            default -> false;
        };

        if (fired) s.startCooldown(ability, now);
    }

    private static boolean doDash(ServerPlayerEntity player, PlayerState s, int dir, long now) {
        GojoConfig c = GojoConfig.get();
        double yawRad = Math.toRadians(player.getYaw());
        Vec3d forward = new Vec3d(-Math.sin(yawRad), 0, Math.cos(yawRad));
        Vec3d right = new Vec3d(-forward.z, 0, forward.x);

        Vec3d wish = switch (dir) {
            case 1 -> forward.negate();
            case 2 -> right.negate();
            case 3 -> right;
            default -> forward;
        };

        Vec3d vel = wish.multiply(c.dashSpeed);
        double y = player.isOnGround() ? 0.12 : Math.min(player.getVelocity().y, 0.1);
        player.setVelocity(vel.x, y, vel.z);
        player.velocityModified = true;
        player.fallDistance = 0;
        s.fallImmuneUntil = now + c.dashFallImmunityTicks;

        var world = player.getServerWorld();
        world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 14, 0.3, 0.05, 0.3, 0.03);
        world.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.2, 0.4, 0.2, 0.05);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 1.5f);
        return true;
    }
}
