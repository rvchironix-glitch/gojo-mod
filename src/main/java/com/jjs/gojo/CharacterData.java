package com.jjs.gojo;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-world, per-player persistent data stored with the overworld
 * (saved in the world folder under data/gojomoveset_characters.dat).
 * Survives death, respawn, rejoin and restarts.
 */
public class CharacterData extends PersistentState {
    private final Map<UUID, Integer> types = new HashMap<>();
    private final Map<UUID, Long> rctReady = new HashMap<>();
    private final Map<UUID, Float> meters = new HashMap<>();
    private final Map<UUID, Integer> heroes = new HashMap<>();
    private final Map<UUID, Long> exhausted = new HashMap<>();
    private final Map<UUID, Long> cursedUntil = new HashMap<>();
    private final Map<UUID, Long> cursedReady = new HashMap<>();

    public static CharacterData get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager()
                .getOrCreate(CharacterData::fromNbt, CharacterData::new, "gojomoveset_characters");
    }

    public static CharacterData fromNbt(NbtCompound nbt) {
        CharacterData d = new CharacterData();
        NbtCompound players = nbt.getCompound("players");
        for (String key : players.getKeys()) {
            try {
                UUID id = UUID.fromString(key);
                NbtCompound p = players.getCompound(key);
                d.types.put(id, p.getInt("type"));
                if (p.contains("rctReady")) d.rctReady.put(id, p.getLong("rctReady"));
                if (p.contains("meter")) d.meters.put(id, p.getFloat("meter"));
                if (p.contains("hero")) d.heroes.put(id, p.getInt("hero"));
                if (p.contains("exhaustedUntil")) d.exhausted.put(id, p.getLong("exhaustedUntil"));
                if (p.contains("cursedUntil")) d.cursedUntil.put(id, p.getLong("cursedUntil"));
                if (p.contains("cursedReady")) d.cursedReady.put(id, p.getLong("cursedReady"));
            } catch (IllegalArgumentException ignored) {
                // skip malformed entries
            }
        }
        return d;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound players = new NbtCompound();
        Set<UUID> ids = new HashSet<>(types.keySet());
        ids.addAll(rctReady.keySet());
        ids.addAll(meters.keySet());
        ids.addAll(heroes.keySet());
        ids.addAll(exhausted.keySet());
        ids.addAll(cursedUntil.keySet());
        ids.addAll(cursedReady.keySet());
        for (UUID id : ids) {
            NbtCompound p = new NbtCompound();
            p.putInt("type", types.getOrDefault(id, Characters.NONE));
            if (rctReady.containsKey(id)) p.putLong("rctReady", rctReady.get(id));
            if (meters.containsKey(id)) p.putFloat("meter", meters.get(id));
            if (heroes.containsKey(id)) p.putInt("hero", heroes.get(id));
            if (exhausted.containsKey(id)) p.putLong("exhaustedUntil", exhausted.get(id));
            if (cursedUntil.containsKey(id)) p.putLong("cursedUntil", cursedUntil.get(id));
            if (cursedReady.containsKey(id)) p.putLong("cursedReady", cursedReady.get(id));
            players.put(id.toString(), p);
        }
        nbt.put("players", players);
        return nbt;
    }

    public int type(UUID id) {
        return types.getOrDefault(id, Characters.NONE);
    }

    /** @return true if the choice was saved; false if the player had already chosen. */
    public boolean setTypeIfUnset(UUID id, int type) {
        if (type(id) != Characters.NONE) return false;
        types.put(id, type);
        markDirty();
        return true;
    }

    public long rctReadyAt(UUID id) {
        return rctReady.getOrDefault(id, 0L);
    }

    public void setRctReadyAt(UUID id, long worldTime) {
        rctReady.put(id, worldTime);
        markDirty();
    }

    /** Ultimate charge, 0..100. */
    public float meter(UUID id) {
        return meters.getOrDefault(id, 0f);
    }

    public void setMeter(UUID id, float value) {
        meters.put(id, Math.max(0f, Math.min(100f, value)));
        markDirty();
    }

    /** Selected sorcerer character (see {@link Heroes}); 0 = not chosen yet. */
    public int hero(UUID id) {
        return heroes.getOrDefault(id, 0);
    }

    /** Overworld game time at which exhaustion ends (0 = not exhausted). */
    public long exhaustedUntil(UUID id) {
        return exhausted.getOrDefault(id, 0L);
    }

    public void setExhaustedUntil(UUID id, long worldTime) {
        exhausted.put(id, worldTime);
        markDirty();
    }

    /** Overworld game time at which Yuji's Cursed Energy ends / may be used again. */
    public long cursedUntil(UUID id) {
        return cursedUntil.getOrDefault(id, 0L);
    }

    public long cursedReadyAt(UUID id) {
        return cursedReady.getOrDefault(id, 0L);
    }

    public void setCursed(UUID id, long until, long readyAt) {
        cursedUntil.put(id, until);
        cursedReady.put(id, readyAt);
        markDirty();
    }

    public void setHero(UUID id, int hero) {
        heroes.put(id, hero);
        markDirty();
    }
}
