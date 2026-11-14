package com.jjs.gojo;

import com.jjs.gojo.effect.BlackFlash;
import com.jjs.gojo.effect.DomainExpansion;
import com.jjs.gojo.effect.HollowNuke;
import com.jjs.gojo.effect.MaximumBlue;
import com.jjs.gojo.effect.MaximumRed;
import com.jjs.gojo.effect.Stun;
import com.jjs.gojo.effect.BlueOrb;
import com.jjs.gojo.effect.CrushingBlow;
import com.jjs.gojo.effect.Dismantle;
import com.jjs.gojo.effect.DivergentFist;
import com.jjs.gojo.effect.FlameArrow;
import com.jjs.gojo.effect.MalevolentPressure;
import com.jjs.gojo.effect.ServerEffect;
import com.jjs.gojo.effect.SukunaCleave;
import com.jjs.gojo.effect.YujiStrikes;
import com.jjs.gojo.effect.BlueTrap;
import com.jjs.gojo.effect.EffectManager;
import com.jjs.gojo.effect.HollowPurple;
import com.jjs.gojo.effect.RapidPunches;
import com.jjs.gojo.effect.RctAura;
import com.jjs.gojo.effect.RedProjectile;
import com.jjs.gojo.effect.TwofoldKick;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GojoMod implements ModInitializer {
    public static final String MOD_ID = "gojomoveset";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Set only for the duration of one fixed-damage call (CombatUtil.damageExact): blocking and charging are skipped. */
    public static boolean bypassGuard;

    private static final Map<UUID, PlayerState> STATES = new ConcurrentHashMap<>();

    public static PlayerState state(ServerPlayerEntity p) {
        return STATES.computeIfAbsent(p.getUuid(), id -> new PlayerState());
    }

    /** Null when the player has no live state (e.g. disconnected). */
    public static PlayerState stateOrNull(UUID id) {
        return STATES.get(id);
    }

    /** Starts a cooldown and tells the client (used by combos that trigger another move). */
    public static void startCooldown(ServerPlayerEntity p, Ability a, int ticks) {
        state(p).startCooldown(a, p.getServer().getTicks(), ticks);
        Characters.sendCooldown(p, a, ticks, ticks);
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
                s.blocking = wants && Characters.isSorcerer(player) && player.isAlive()
                        && !player.isSpectator() && !s.isGuardBroken(now)
                        && !Exhaustion.isExhausted(player) && !Stun.isStunned(player);
            });
        });

        // Permanent character choice: accepted exactly once per player per world.
        ServerPlayNetworking.registerGlobalReceiver(GojoNet.CHOOSE, (server, player, handler, buf, sender) -> {
            int wanted = buf.readByte();
            server.execute(() -> Characters.choose(server, player, wanted));
        });

        ServerPlayNetworking.registerGlobalReceiver(GojoNet.HERO, (server, player, handler, buf, sender) -> {
            int hero = buf.readByte();
            server.execute(() -> Characters.chooseHero(server, player, hero));
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.player;
            Characters.applyHealth(p);
            p.setNoGravity(false); // a Hollow Nuke interrupted by a disconnect must not leave Gojo floating
            Characters.syncAll(p);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            Characters.applyHealth(newPlayer);
            newPlayer.setNoGravity(false);
            if (!alive && Characters.isSorcerer(newPlayer)) {
                newPlayer.setHealth(newPlayer.getMaxHealth());
            }
            Characters.syncAll(newPlayer);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.player.getUuid()));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            EffectManager.tick();
            Stun.tick(server);
            Exhaustion.tick(server);
            SixEyes.tick(server);
        });
        // Defeating an enemy grants extra ultimate charge.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getAttacker() instanceof ServerPlayerEntity killer && killer != entity) {
                Ultimate.charge(killer, GojoConfig.get().chargePerKill);
            }
        });

        // A transformation never survives a server stop: end it so the meter resets.
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (Ultimate.isAwakened(p)) Ultimate.end(server, p.getUuid());
            }
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            EffectManager.clear();
            BlueTrap.clear();
            Stun.clear();
            MaximumRed.clearAll();
            HollowNuke.clearAll();
            DomainExpansion.clearAll();
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

        // Fall damage immunity after dashing + blocking + trapped attackers deal no damage
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(GojoMod::allowDamage);
    }

    private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        boolean allowed = allowDamageInner(entity, source, amount);
        if (allowed && !bypassGuard && amount > 0f && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            chargeFromDamage(entity, source, amount);
        }
        return allowed;
    }

    /** Dealing damage and taking damage both fill the ultimate meter (not while in invulnerability frames). */
    private static void chargeFromDamage(LivingEntity victim, DamageSource source, float amount) {
        if (victim.timeUntilRegen > 10) return;
        GojoConfig c = GojoConfig.get();
        if (source.getAttacker() instanceof ServerPlayerEntity attacker && attacker != victim) {
            Ultimate.charge(attacker, Math.min(amount, victim.getHealth()) * c.chargePerDamageDealt);
        }
        if (victim instanceof ServerPlayerEntity hurt) {
            Ultimate.charge(hurt, amount * c.chargePerDamageTaken);
        }
    }

    private static boolean allowDamageInner(LivingEntity entity, DamageSource source, float amount) {
        MinecraftServer srv = entity.getServer();
        if (srv == null) return true;
        if (bypassGuard) return true; // fixed-damage moves (Maximum Blue / Hollow Nuke) cannot be blocked
        long now = srv.getTicks();

        // Anything caught in Lapis Blue is helpless: it cannot hurt anyone.
        Entity attacker = source.getAttacker();
        if (attacker != null && (BlueTrap.isTrapped(attacker, now) || Stun.isStunned(attacker))) {
            return false;
        }

        if (!(entity instanceof ServerPlayerEntity player)) return true;
        PlayerState s = STATES.get(player.getUuid());
        if (s == null) return true;

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

        // Server-side restriction: only Sorcerers may use any ability.
        if (!Characters.isSorcerer(player)) return;

        // The client only sends move slots; the server decides what they mean for this character.
        int hero = CharacterData.get(player.getServer()).hero(player.getUuid());
        if (hero == 0) return; // must pick Gojo or Yuji first
        ability = Kits.resolve(hero, Ultimate.isAwakened(player), ability);
        if (ability == null) return;

        if (ability == Ability.RCT) {
            if (Exhaustion.isExhausted(player) || Stun.isStunned(player)) return;
            doRct(player);
            return;
        }

        // Exhausted (after Hollow Nuke / Domain) or stunned: no abilities at all. Checked here on the server.
        if (Exhaustion.isExhausted(player) || Stun.isStunned(player)) {
            if (Exhaustion.isExhausted(player)) player.sendMessage(Text.literal("EXHAUSTED"), true);
            return;
        }

        // Pressing Maximum Red again while it is running: combo (only valid during the charge window).
        if (ability == Ability.MAXIMUM_RED && MaximumRed.isActive(player.getUuid())) {
            MaximumRed.handleSecondPress(player);
            return;
        }

        if (ability == Ability.ULTIMATE) {
            Ultimate.tryActivate(player);
            return;
        }

        PlayerState s = state(player);
        long now = player.getServer().getTicks();

        // Busy with a channelled move (Maximum Red charge, Hollow Nuke, Domain cast): nothing else may start.
        if (now < s.channelUntil) return;

        // Hollow Purple is ultimate-only.
        if (ability == Ability.R_SPECIAL && !Ultimate.isGojoAwakened(player)) {
            player.sendMessage(Text.literal("Hollow Purple requires Limitless Awakened"), true);
            return;
        }
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
            case MAXIMUM_BLUE -> {
                EffectManager.add(new MaximumBlue(player));
                yield true;
            }
            case MAXIMUM_RED -> MaximumRed.start(player);
            case HOLLOW_NUKE -> HollowNuke.start(player);
            case DOMAIN -> DomainExpansion.start(player);
            case DIVERGENT_FIST -> launch(new DivergentFist(player));
            case BLACK_FLASH -> launch(new BlackFlash(player));
            case CRUSHING_BLOW -> launch(new CrushingBlow(player));
            case RAPID_STRIKES -> launch(new YujiStrikes(player));
            case CLEAVE -> launch(new SukunaCleave(player));
            case DISMANTLE -> launch(new Dismantle(player));
            case FLAME_ARROW -> launch(new FlameArrow(player));
            case MALEVOLENT_PRESSURE -> launch(new MalevolentPressure(player));
            default -> false;
        };

        if (fired) {
            int cd = ability.cooldownTicks();
            if (ability != Ability.R_SPECIAL && !ability.isGojoUltimateMove() && Ultimate.isGojoAwakened(player)) {
                cd = Math.max(1, Math.round(cd * GojoConfig.get().awakenedCooldownMult));
            }
            s.startCooldown(ability, now, cd);
            Characters.sendCooldown(player, ability, cd, cd);
        }
    }

    private static boolean launch(ServerEffect effect) {
        EffectManager.add(effect);
        return true;
    }

    /** Reverse Cursed Technique. Cooldown is stored in the world's save data so death/relog cannot reset it. */
    private static void doRct(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        CharacterData data = CharacterData.get(server);
        long now = server.getOverworld().getTime();
        if (now < data.rctReadyAt(player.getUuid())) return;

        if (player.getHealth() >= player.getMaxHealth()) {
            player.sendMessage(Text.literal("Already at full health"), true);
            return;
        }

        player.heal(GojoConfig.get().rctHealAmount); // LivingEntity.heal never exceeds max health
        int cd = Ability.RCT.cooldownTicks();
        data.setRctReadyAt(player.getUuid(), now + cd);
        Characters.sendCooldown(player, Ability.RCT, cd, cd);
        EffectManager.add(new RctAura(player));
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
