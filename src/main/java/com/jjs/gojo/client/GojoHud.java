package com.jjs.gojo.client;

import com.jjs.gojo.Ability;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.math.MathHelper;

/**
 * Ability HUD.
 *  PC:    vertical ability column on the right edge + ultimate meter directly above the vanilla armor row.
 *  Touch: only the meter (and a hint) is shown passively; the buttons live on the Ability Pad screen.
 * Positions come from {@link HudLayout}, so players can move / resize / fade every element.
 * Everything is derived from the scaled window size, so it adapts to any GUI scale and resolution.
 */
public final class GojoHud {
    /** One ability button. {@code ability == null} is the Block button (hold-to-block, no cooldown). */
    public record Entry(String id, Ability ability, String glyph, String label, int color, KeyBinding key) {}

    private GojoHud() {}

    // ------------------------------------------------------------------ entries

    /** Buttons of the PC column (no Block button: it is a held key). */
    public static Entry[] entries() {
        Entry rct = new Entry("RCT", Ability.RCT, "+", "RCT", 0xFF4AFF8A, GojoClient.RCT);
        Entry dash = new Entry("DASH", Ability.DASH, ">>", "Dash", 0xFFCCCCCC, GojoClient.DASH);
        Entry ult = new Entry("ULTIMATE", Ability.ULTIMATE, "U", "Ultimate", 0xFF6FE3FF, GojoClient.ULT);
        if (ClientState.hero == 2 && ClientState.awakened()) { // Sukuna
            return new Entry[]{
                    new Entry("CLEAVE", Ability.CLEAVE, "CL", "Cleave", 0xFFFF3A3A, GojoClient.BLUE),
                    new Entry("DISMANTLE", Ability.DISMANTLE, "DS", "Dismantle", 0xFFFF7A7A, GojoClient.RED),
                    new Entry("FLAME_ARROW", Ability.FLAME_ARROW, "FA", "Flame", 0xFFFFA83A, GojoClient.PUNCHES),
                    new Entry("MALEVOLENT_SHRINE", Ability.MALEVOLENT_SHRINE, "MS", "Shrine", 0xFFB04AFF, GojoClient.KICK),
                    new Entry("WORLD_CUTTING_SLASH", Ability.WORLD_CUTTING_SLASH, "WS", "World Slash", 0xFFFF2A2A, GojoClient.SPECIAL),
                    rct, dash, ult};
        }
        if (ClientState.hero == 2) { // Yuji
            return new Entry[]{
                    new Entry("DIVERGENT_FIST", Ability.DIVERGENT_FIST, "DF", "Divergent", 0xFFFFA83A, GojoClient.BLUE),
                    new Entry("BLACK_FLASH", Ability.BLACK_FLASH, "BF", "Black Flash", 0xFFB04AFF, GojoClient.RED),
                    new Entry("CRUSHING_BLOW", Ability.CRUSHING_BLOW, "CB", "Crushing", 0xFFFFD83A, GojoClient.PUNCHES),
                    new Entry("RAPID_STRIKES", Ability.RAPID_STRIKES, "RS", "Strikes", 0xFFFF6A3A, GojoClient.KICK),
                    new Entry("CURSED_ENERGY", Ability.CURSED_ENERGY, "CE", "Cursed", 0xFF8A3AFF, GojoClient.SPECIAL),
                    rct, dash, ult};
        }
        if (ClientState.gojoAwakened()) { // Gojo, Limitless Awakened: the four moves are replaced
            return new Entry[]{
                    new Entry("MAXIMUM_BLUE", Ability.MAXIMUM_BLUE, "MB", "Max Blue", 0xFF3A7BFF, GojoClient.BLUE),
                    new Entry("MAXIMUM_RED", Ability.MAXIMUM_RED, "MR", "Max Red", 0xFFFF3A3A, GojoClient.RED),
                    new Entry("HOLLOW_NUKE", Ability.HOLLOW_NUKE, "HN", "Hollow Nuke", 0xFF9FE8FF, GojoClient.PUNCHES),
                    new Entry("DOMAIN", Ability.DOMAIN, "DE", "0.2 Domain", 0xFFD59CFF, GojoClient.KICK),
                    new Entry("R_SPECIAL", Ability.R_SPECIAL, "HP", "Purple", 0xFFB04AFF, GojoClient.SPECIAL),
                    rct, dash, ult};
        }
        return new Entry[]{ // Gojo
                new Entry("LAPIS_BLUE", Ability.LAPIS_BLUE, "B", "Blue", 0xFF3A7BFF, GojoClient.BLUE),
                new Entry("REVERSAL_RED", Ability.REVERSAL_RED, "R", "Red", 0xFFFF3A3A, GojoClient.RED),
                new Entry("RAPID_PUNCHES", Ability.RAPID_PUNCHES, "P", "Punches", 0xFFFFA83A, GojoClient.PUNCHES),
                new Entry("TWOFOLD_KICK", Ability.TWOFOLD_KICK, "K", "Kick", 0xFFFFD83A, GojoClient.KICK),
                new Entry("R_SPECIAL", Ability.R_SPECIAL, "HP", "Purple", 0xFFB04AFF, GojoClient.SPECIAL),
                rct, dash, ult};
    }

    /** Buttons of the touch Ability Pad: everything above plus Block. */
    public static Entry[] touchEntries() {
        Entry[] base = entries();
        Entry[] out = new Entry[base.length + 1];
        System.arraycopy(base, 0, out, 0, base.length);
        out[base.length] = new Entry("BLOCK", null, "[]", "Block", 0xFF9AB8FF, GojoClient.BLOCK);
        return out;
    }

    // ------------------------------------------------------------------ geometry (all return {x, y, w, h})

    public static int[] columnRect(int sw, int sh, int n, HudLayout.Item it) {
        int gap = 2;
        int base = MathHelper.clamp((sh - 100) / n - gap, 14, 24);
        int size = MathHelper.clamp(Math.round(base * it.scale), 10, 48);
        int total = n * (size + gap) - gap;
        int x = sw - size - 4 + Math.round(it.dx * sw);
        int y = Math.max(40, (sh - total) / 2) + Math.round(it.dy * sh); // default stays below effect icons
        x = MathHelper.clamp(x, 24, Math.max(24, sw - size));
        y = MathHelper.clamp(y, 0, Math.max(0, sh - total));
        return new int[]{x, y, size, total};
    }

    /** Slot index {@code i} inside a column rect. */
    public static int columnSlotY(int[] r, int i) {
        return r[1] + i * (r[2] + 2);
    }

    public static int[] meterRect(MinecraftClient mc, int sw, int sh, HudLayout.Item it) {
        // Same row maths as vanilla's health/armor layout so the default sits directly above the armor icons
        // even when 40 HP pushes the rows up.
        float maxHealth = mc.player != null ? mc.player.getMaxHealth() : 20f;
        int absorption = mc.player != null ? MathHelper.ceil(mc.player.getAbsorptionAmount()) : 0;
        int rows = MathHelper.ceil((maxHealth + absorption) / 2.0f / 10.0f);
        int spacing = Math.max(10 - (rows - 2), 3);
        int armorY = (sh - 39) - (rows - 1) * spacing - 10;

        int w = Math.max(40, Math.round(81 * it.scale));
        int h = Math.max(5, Math.round(7 * it.scale));
        int x = sw / 2 - 91 + Math.round(it.dx * sw);
        int y = armorY - 3 - 7 - 2 + Math.round(it.dy * sh) - (h - 7);
        x = MathHelper.clamp(x, 0, Math.max(0, sw - w));
        y = MathHelper.clamp(y, 0, Math.max(0, sh - h));
        return new int[]{x, y, w, h};
    }

    public static int[] touchRect(int sw, int sh, int index, HudLayout.Item it) {
        int base = MathHelper.clamp((int) (sh * 0.10f), 24, 56);
        int size = MathHelper.clamp(Math.round(base * it.scale), 16, 96);
        double cx = (0.88 - (index / 5) * 0.11) * sw + it.dx * sw;
        double cy = (0.22 + (index % 5) * 0.14) * sh + it.dy * sh;
        int x = MathHelper.clamp((int) (cx - size / 2.0), 0, Math.max(0, sw - size));
        int y = MathHelper.clamp((int) (cy - size / 2.0), 0, Math.max(0, sh - size));
        return new int[]{x, y, size, size};
    }

    // ------------------------------------------------------------------ passive HUD

    public static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || ClientState.type != 2 || ClientState.hero == 0) return;
        if (mc.currentScreen instanceof CustomizeControlsScreen) return; // the editor draws its own preview
        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        HudLayout.Data layout = HudLayout.get();
        drawExhausted(ctx, tr, sw, sh);

        if (TouchMode.active()) {
            HudLayout.Item mi = layout.touchMeter;
            int[] m = meterRect(mc, sw, sh, mi);
            drawMeter(ctx, tr, m, mi.alpha, tickDelta, false);
            if (mc.currentScreen == null) {
                String hint = "[" + GojoClient.PAD.getBoundKeyLocalizedText().getString() + "] Abilities";
                ctx.drawTextWithShadow(tr, hint, m[0], m[1] - 10, withAlpha(0xFFDDDDDD, mi.alpha));
            }
            return;
        }

        HudLayout.Item mi = layout.pcMeter;
        drawMeter(ctx, tr, meterRect(mc, sw, sh, mi), mi.alpha, tickDelta, false);
        if (mc.currentScreen == null) {
            Entry[] list = entries();
            HudLayout.Item ci = layout.pcColumn;
            drawColumn(ctx, tr, list, columnRect(sw, sh, list.length, ci), ci.alpha);
        }
    }

    /** Big red BURNOUT banner with the time left, centred above the hotbar area. */
    public static void drawExhausted(DrawContext ctx, TextRenderer tr, int sw, int sh) {
        if (!ClientState.exhausted()) return;
        int secs = (ClientState.exhaustedRemaining() + 19) / 20;
        String text = "BURNOUT  " + secs + "s";
        float frac = ClientState.exhaustedRemaining() / (float) ClientState.exhaustedTotal();
        int w = 110;
        int x = sw / 2 - w / 2;
        int y = sh / 2 + 24;
        ctx.fill(x - 1, y - 1, x + w + 1, y + 15, 0xFF000000);
        ctx.fill(x, y, x + w, y + 14, 0xFF2A0A0A);
        ctx.fill(x, y, x + (int) (w * frac), y + 14, 0xFF8A1A1A);
        ctx.drawCenteredTextWithShadow(tr, text, sw / 2, y + 3, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ drawing helpers (shared with the editor)

    public static void drawColumn(DrawContext ctx, TextRenderer tr, Entry[] list, int[] r, float alpha) {
        for (int i = 0; i < list.length; i++) {
            drawSlot(ctx, tr, list[i], r[0], columnSlotY(r, i), r[2], alpha, false);
        }
    }

    public static int withAlpha(int argb, float alpha) {
        int a = (int) ((argb >>> 24) * MathHelper.clamp(alpha, 0f, 1f));
        a = Math.max(a, 0x10); // text with a near-zero alpha is rendered opaque by the game, so keep a floor
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** Draws one ability button. {@code touchStyle}: label below instead of the key to the left. */
    public static void drawSlot(DrawContext ctx, TextRenderer tr, Entry e, int x, int y, int size,
                                float alpha, boolean touchStyle) {
        Ability ab = e.ability();
        boolean block = ab == null;
        boolean ultimate = ab == Ability.ULTIMATE;
        boolean locked = ab == Ability.R_SPECIAL && !ClientState.awakened();
        boolean burnout = ClientState.exhausted() && ab != Ability.RCT; // RCT stays usable during burnout
        int remaining = (block || ultimate) ? 0 : ClientState.remaining(ab.id);
        int cdTotal = (block || ultimate) ? 1 : ClientState.total(ab.id);
        // Stage 6 timers: Cursed Energy shows its 30 s duration then a 10 s cooldown; World Slash shows its 5 s prep.
        int timer = 0;
        int timerTotal = 1;
        if (ab == Ability.CURSED_ENERGY) {
            timer = ClientState.cursedRemaining();
            timerTotal = 600;
            remaining = ClientState.cursedCooldownRemaining();
            cdTotal = 200;
        } else if (ab == Ability.WORLD_CUTTING_SLASH && ClientState.prepRemaining() > 0) {
            timer = ClientState.prepRemaining();
            timerTotal = ClientState.prepTotal();
        }

        boolean active;
        boolean ready;
        if (block) {
            active = GojoClient.isBlocking();
            ready = !active && !burnout;
        } else if (ultimate) {
            active = ClientState.awakened();
            ready = ClientState.meter >= 100f && !active && !burnout;
        } else {
            active = timer > 0;
            ready = !locked && remaining <= 0 && !burnout && !active;
        }

        float gs = size / 24f * 1.3f;
        int frame = active ? 0xFFFFD83A : ready ? 0xFF55FF55 : 0xFF555555;
        ctx.fill(x - 1, y - 1, x + size + 1, y + size + 1, withAlpha(frame, alpha));
        ctx.fill(x, y, x + size, y + size, withAlpha(0xCC101020, alpha));

        var ms = ctx.getMatrices();
        ms.push();
        ms.translate(x + size / 2f, y + size / 2f - 4 * gs, 0);
        ms.scale(gs, gs, 1f);
        int col = (ready || active) ? e.color() : 0xFF777777;
        ctx.drawCenteredTextWithShadow(tr, e.glyph(), 0, 0, withAlpha(col, alpha));
        ms.pop();

        if (timer > 0) {
            int h = (int) ((size - 2) * (1f - timer / (float) timerTotal));
            ctx.fill(x + 1, y + 1, x + size - 1, y + 1 + h, withAlpha(0xB0000000, alpha));
            drawSmall(ctx, tr, ((timer + 19) / 20) + "s", x + size / 2, y + size - 8, withAlpha(0xFFFFD83A, alpha), size);
        } else if (ultimate && active) {
            int secs = (ClientState.awakenedRemaining() + 19) / 20;
            float frac = ClientState.awakenedRemaining() / (float) ClientState.awakenedTotal();
            int h = (int) ((size - 2) * (1f - frac));
            ctx.fill(x + 1, y + 1, x + size - 1, y + 1 + h, withAlpha(0xB0000000, alpha));
            drawSmall(ctx, tr, secs + "s", x + size / 2, y + size - 8, withAlpha(0xFFFFD83A, alpha), size);
        } else if (locked) {
            ctx.fill(x + 1, y + 1, x + size - 1, y + size - 1, withAlpha(0x90000000, alpha));
        } else if (remaining > 0) {
            float frac = Math.min(1f, remaining / (float) cdTotal);
            int h = (int) ((size - 2) * frac);
            ctx.fill(x + 1, y + 1, x + size - 1, y + 1 + h, withAlpha(0xB0000000, alpha));
            drawSmall(ctx, tr, String.format("%.1f", remaining / 20f), x + size / 2, y + size - 8,
                    withAlpha(0xFFFFFFFF, alpha), size);
        } else if (ready) {
            ctx.fill(x + 2, y + size - 3, x + size - 2, y + size - 1, withAlpha(0xFF55FF55, alpha));
        }

        if (burnout && !locked) {
            ctx.fill(x + 1, y + 1, x + size - 1, y + size - 1, withAlpha(0x80300000, alpha));
        }

        if (touchStyle) {
            drawSmall(ctx, tr, e.label(), x + size / 2, y + size + 3, withAlpha(0xFFFFFFFF, alpha), size);
        } else {
            String keyName = e.key().getBoundKeyLocalizedText().getString();
            if (tr.getWidth(keyName) > 40) keyName = tr.trimToWidth(keyName, 40);
            ctx.drawTextWithShadow(tr, keyName, x - 4 - tr.getWidth(keyName), y + size / 2 - 4,
                    withAlpha(0xFFDDDDDD, alpha));
        }
    }

    private static void drawSmall(DrawContext ctx, TextRenderer tr, String text, int cx, int y, int color, int size) {
        float s = MathHelper.clamp(0.7f * size / 24f, 0.5f, 1.2f);
        var ms = ctx.getMatrices();
        ms.push();
        ms.translate(cx, y, 0);
        ms.scale(s, s, 1f);
        ctx.drawCenteredTextWithShadow(tr, text, 0, 0, color);
        ms.pop();
    }

    /** @param preview draws a fixed 65% meter (used by the layout editor). */
    public static void drawMeter(DrawContext ctx, TextRenderer tr, int[] r, float alpha, float tickDelta, boolean preview) {
        int x = r[0], y = r[1], w = r[2], h = r[3];
        boolean awakened = !preview && ClientState.awakened();
        float shown = preview ? 65f : ClientState.shownMeter;
        float meter = preview ? 65f : ClientState.meter;
        boolean full = meter >= 100f && !awakened;
        float time = ClientState.tick + tickDelta;
        float pulse = (float) (0.5 + 0.5 * Math.sin(time * 0.35));

        int frame = awakened ? 0xFFFFD83A : full ? lerp(0xFF3A7BFF, 0xFFFFFFFF, pulse) : 0xFF3A3A55;
        if (full) {
            int glow = 1 + (int) (pulse * 2);
            ctx.fill(x - 1 - glow, y - 1 - glow, x + w + 1 + glow, y + h + 1 + glow,
                    withAlpha((0x50 << 24) | 0x6FE3FF, alpha));
        }
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, withAlpha(frame, alpha));
        ctx.fill(x, y, x + w, y + h, withAlpha(0xFF0A0A18, alpha));

        String label;
        if (awakened) {
            float frac = ClientState.awakenedRemaining() / (float) ClientState.awakenedTotal();
            ctx.fillGradient(x, y, x + (int) (w * frac), y + h, withAlpha(0xFFFFE680, alpha), withAlpha(0xFFC08A00, alpha));
            int secs = (ClientState.awakenedRemaining() + 19) / 20;
            label = (ClientState.hero == 2 ? "SUKUNA " : "AWAKENED ") + (secs / 60) + ":" + String.format("%02d", secs % 60);
        } else {
            float frac = MathHelper.clamp(shown / 100f, 0f, 1f);
            int endColor = full ? lerp(0xFF3A7BFF, 0xFFBBF3FF, pulse) : 0xFF3A7BFF;
            ctx.fillGradient(x, y, x + (int) (w * frac), y + h, withAlpha(0xFF9FD0FF, alpha), withAlpha(endColor, alpha));
            label = full ? "READY - " + GojoClient.ULT.getBoundKeyLocalizedText().getString() : (int) meter + "%";
        }

        float s = MathHelper.clamp(0.7f * h / 7f, 0.5f, 1.4f);
        var ms = ctx.getMatrices();
        ms.push();
        ms.translate(x + w / 2f, y + (h - 8 * s) / 2f + 0.5f, 0);
        ms.scale(s, s, 1f);
        ctx.drawCenteredTextWithShadow(tr, label, 0, 0, withAlpha(0xFFFFFFFF, alpha));
        ms.pop();
    }

    private static int lerp(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
