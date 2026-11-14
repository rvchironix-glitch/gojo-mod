package com.jjs.gojo.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jjs.gojo.GojoMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-player HUD layouts (config/gojomoveset-hud.json). The PC HUD and the touch HUD have separate layouts.
 * Positions are stored as offsets from each element's default position, as a fraction of the screen size,
 * so a saved layout adapts to any resolution or GUI scale.
 */
public final class HudLayout {
    public static final class Item {
        public float dx;
        public float dy;
        public float scale = 1.0f;
        public float alpha = 1.0f;

        public void clampValues() {
            scale = Math.max(0.5f, Math.min(2.5f, scale));
            alpha = Math.max(0.2f, Math.min(1.0f, alpha));
            dx = Math.max(-1f, Math.min(1f, dx));
            dy = Math.max(-1f, Math.min(1f, dy));
        }
    }

    public static final class Data {
        public Item pcColumn = new Item();
        public Item pcMeter = new Item();
        public Item touchMeter = new Item();
        public Map<String, Item> touchButtons = new HashMap<>();
        /** 0 = auto-detect mobile launcher, 1 = always on, 2 = off. */
        public int touchMode = 0;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<String, Data> all;

    private HudLayout() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("gojomoveset-hud.json");
    }

    private static String key() {
        try {
            String name = MinecraftClient.getInstance().getSession().getUsername();
            return name == null || name.isEmpty() ? "default" : name;
        } catch (RuntimeException e) {
            return "default";
        }
    }

    private static void ensureLoaded() {
        if (all != null) return;
        all = new HashMap<>();
        try {
            if (Files.exists(file())) {
                @SuppressWarnings("unchecked")
                Map<String, Data> loaded = GSON.fromJson(Files.readString(file()),
                        new com.google.gson.reflect.TypeToken<Map<String, Data>>() {}.getType());
                if (loaded != null) all = loaded;
            }
        } catch (IOException | RuntimeException e) {
            GojoMod.LOGGER.error("Could not read gojomoveset-hud.json, using default layout", e);
        }
    }

    /** Layout for the current account. */
    public static Data get() {
        ensureLoaded();
        Data d = all.computeIfAbsent(key(), k -> new Data());
        if (d.pcColumn == null) d.pcColumn = new Item();
        if (d.pcMeter == null) d.pcMeter = new Item();
        if (d.touchMeter == null) d.touchMeter = new Item();
        if (d.touchButtons == null) d.touchButtons = new HashMap<>();
        return d;
    }

    public static Item touchButton(String id) {
        return get().touchButtons.computeIfAbsent(id, k -> new Item());
    }

    public static void resetPc() {
        Data d = get();
        d.pcColumn = new Item();
        d.pcMeter = new Item();
        save();
    }

    public static void resetTouch() {
        Data d = get();
        d.touchButtons = new HashMap<>();
        d.touchMeter = new Item();
        save();
    }

    public static void save() {
        ensureLoaded();
        try {
            Files.writeString(file(), GSON.toJson(all));
        } catch (IOException e) {
            GojoMod.LOGGER.error("Could not save gojomoveset-hud.json", e);
        }
    }
}
