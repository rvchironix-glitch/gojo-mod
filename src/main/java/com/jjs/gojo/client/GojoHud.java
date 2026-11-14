package com.jjs.gojo.client;

import com.jjs.gojo.Ability;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.math.MathHelper;

/**
 * PC HUD:
 *  - ability column on the right edge of the screen (never near the hotbar, health, hunger, armor or XP),
 *  - ultimate meter directly above the vanilla armor row (separate element; vanilla icons are untouched).
 * Everything is derived from the scaled window size, so it adapts to any GUI scale and resolution.
 */
public final class GojoHud {
    private record Entry(Ability ability, String glyph, int color, KeyBinding key) {}

    private GojoHud() {}

    private static Entry[] entries() {
        Entry rct = new Entry(Ability.RCT, "+", 0xFF4AFF8A, GojoClient.RCT);
        Entry dash = new Entry(Ability.DASH, ">>", 0xFFCCCCCC, GojoClient.DASH);
        Entry ult = new Entry(Ability.ULTIMATE, "U", 0xFF6FE3FF, GojoClient.ULT);
        if (ClientState.hero == 2 && ClientState.awakened()) { // Sukuna
            return new Entry[]{
                    new Entry(Ability.CLEAVE, "CL", 0xFFFF3A3A, GojoClient.BLUE),
                    new Entry(Ability.DISMANTLE, "DS", 0xFFFF7A7A, GojoClient.RED),
                    new Entry(Ability.FLAME_ARROW, "FA", 0xFFFFA83A, GojoClient.PUNCHES),
                    new Entry(Ability.MALEVOLENT_PRESSURE, "MP", 0xFFB04AFF, GojoClient.KICK),
                    rct, dash, ult};
        }
        if (ClientState.hero == 2) { // Yuji
            return new Entry[]{
                    new Entry(Ability.DIVERGENT_FIST, "DF", 0xFFFFA83A, GojoClient.BLUE),
                    new Entry(Ability.BLACK_FLASH, "BF", 0xFFB04AFF, GojoClient.RED),
                    new Entry(Ability.CRUSHING_BLOW, "CB", 0xFFFFD83A, GojoClient.PUNCHES),
                    new Entry(Ability.RAPID_STRIKES, "RS", 0xFFFF6A3A, GojoClient.KICK),
                    rct, dash, ult};
        }
        return new Entry[]{ // Gojo
                new Entry(Ability.LAPIS_BLUE, "B", 0xFF3A7BFF, GojoClient.BLUE),
                new Entry(Ability.REVERSAL_RED, "R", 0xFFFF3A3A, GojoClient.RED),
                new Entry(Ability.RAPID_PUNCHES, "P", 0xFFFFA83A, GojoClient.PUNCHES),
                new Entry(Ability.TWOFOLD_KICK, "K", 0xFFFFD83A, GojoClient.KICK),
                new Entry(Ability.R_SPECIAL, "HP", 0xFFB04AFF, GojoClient.SPECIAL),
                rct, dash, ult};
    }

    public static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || ClientState.type != 2 || ClientState.hero == 0) return;
        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        renderMeter(ctx, mc, tr, sw, sh, tickDelta);
        if (mc.currentScreen == null) {
            renderColumn(ctx, tr, sw, sh);
        }
    }

    // ---------------------------------------------------------------- ability column

    private static void renderColumn(DrawContext ctx, TextRenderer tr, int sw, int sh) {
        Entry[] list = entries();
        int n = list.length;
        int gap = 2;
        int size = MathHelper.clamp((sh - 100) / n - gap, 14, 24);
        int total = n * (size + gap) - gap;
        int x = sw - size - 4;
        int y = Math.max(40, (sh - total) / 2); // stays below vanilla status-effect icons
        float glyphScale = size / 24f * 1.3f;

        for (int i = 0; i < n; i++) {
            Entry e = list[i];
            int sy = y + i * (size + gap);
            int id = e.ability().id;
            boolean locked = e.ability() == Ability.R_SPECIAL && !ClientState.awakened();
            boolean ultimate = e.ability() == Ability.ULTIMATE;

            int remaining = ultimate ? 0 : ClientState.remaining(id);
            boolean ready = ultimate ? (ClientState.meter >= 100f && !ClientState.awakened())
                    : (!locked && remaining <= 0);
            boolean active = ultimate && ClientState.awakened();

            int frame = active ? 0xFFFFD83A : ready ? 0xFF55FF55 : 0xFF555555;
            ctx.fill(x - 1, sy - 1, x + size + 1, sy + size + 1, frame);
            ctx.fill(x, sy, x + size, sy + size, 0xCC101020);

            var ms = ctx.getMatrices();
            ms.push();
            ms.translate(x + size / 2f, sy + size / 2f - 4 * glyphScale, 0);
            ms.scale(glyphScale, glyphScale, 1f);
            int col = (ready || active) ? e.color() : 0xFF777777;
            ctx.drawCenteredTextWithShadow(tr, e.glyph(), 0, 0, col);
            ms.pop();

            if (active) {
                int secs = (ClientState.awakenedRemaining() + 19) / 20;
                float frac = ClientState.awakenedRemaining() / (float) ClientState.awakenedTotal();
                int h = (int) ((size - 2) * (1f - frac));
                ctx.fill(x + 1, sy + 1, x + size - 1, sy + 1 + h, 0xB0000000);
                drawSmall(ctx, tr, secs + "s", x + size / 2, sy + size - 8, 0xFFFFD83A);
            } else if (locked) {
                ctx.fill(x + 1, sy + 1, x + size - 1, sy + size - 1, 0x90000000);
            } else if (remaining > 0) {
                float frac = Math.min(1f, remaining / (float) ClientState.total(id));
                int h = (int) ((size - 2) * frac);
                ctx.fill(x + 1, sy + 1, x + size - 1, sy + 1 + h, 0xB0000000);
                drawSmall(ctx, tr, String.format("%.1f", remaining / 20f), x + size / 2, sy + size - 8, 0xFFFFFFFF);
            } else if (ready) {
                ctx.fill(x + 2, sy + size - 3, x + size - 2, sy + size - 1, 0xFF55FF55);
            }

            // keybind label to the left of the slot, so it never covers the icon
            String keyName = e.key().getBoundKeyLocalizedText().getString();
            if (tr.getWidth(keyName) > 40) keyName = tr.trimToWidth(keyName, 40);
            ctx.drawTextWithShadow(tr, keyName, x - 4 - tr.getWidth(keyName), sy + size / 2 - 4, 0xFFDDDDDD);
        }
    }

    private static void drawSmall(DrawContext ctx, TextRenderer tr, String text, int cx, int y, int color) {
        var ms = ctx.getMatrices();
        ms.push();
        ms.translate(cx, y, 0);
        ms.scale(0.7f, 0.7f, 1f);
        ctx.drawCenteredTextWithShadow(tr, text, 0, 0, color);
        ms.pop();
    }

    // ---------------------------------------------------------------- ultimate meter

    private static void renderMeter(DrawContext ctx, MinecraftClient mc, TextRenderer tr,
                                    int sw, int sh, float tickDelta) {
        // Same row maths as vanilla's health/armor layout so the meter sits directly above the armor icons
        // even when 40 HP pushes the rows up.
        float maxHealth = mc.player.getMaxHealth();
        int absorption = MathHelper.ceil(mc.player.getAbsorptionAmount());
        int rows = MathHelper.ceil((maxHealth + absorption) / 2.0f / 10.0f);
        int spacing = Math.max(10 - (rows - 2), 3);
        int healthY = sh - 39;
        int armorY = healthY - (rows - 1) * spacing - 10;

        int w = 81;
        int h = 7;
        int x = sw / 2 - 91;
        int y = armorY - 3 - h - 2;

        boolean awakened = ClientState.awakened();
        boolean full = ClientState.meter >= 100f && !awakened;
        float time = ClientState.tick + tickDelta;
        float pulse = (float) (0.5 + 0.5 * Math.sin(time * 0.35));

        int frame = awakened ? 0xFFFFD83A : full ? lerp(0xFF3A7BFF, 0xFFFFFFFF, pulse) : 0xFF3A3A55;
        if (full) {
            int glow = 1 + (int) (pulse * 2);
            ctx.fill(x - 1 - glow, y - 1 - glow, x + w + 1 + glow, y + h + 1 + glow, (0x50 << 24) | 0x6FE3FF);
        }
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, frame);
        ctx.fill(x, y, x + w, y + h, 0xFF0A0A18);

        String label;
        if (awakened) {
            float frac = ClientState.awakenedRemaining() / (float) ClientState.awakenedTotal();
            ctx.fillGradient(x, y, x + (int) (w * frac), y + h, 0xFFFFE680, 0xFFC08A00);
            int secs = (ClientState.awakenedRemaining() + 19) / 20;
            label = (ClientState.hero == 2 ? "SUKUNA " : "AWAKENED ") + (secs / 60) + ":" + String.format("%02d", secs % 60);
        } else {
            float frac = MathHelper.clamp(ClientState.shownMeter / 100f, 0f, 1f);
            int fillColorB = full ? lerp(0xFF3A7BFF, 0xFFBBF3FF, pulse) : 0xFF3A7BFF;
            ctx.fillGradient(x, y, x + (int) (w * frac), y + h, 0xFF9FD0FF, fillColorB);
            label = full ? "READY - " + GojoClient.ULT.getBoundKeyLocalizedText().getString()
                    : (int) ClientState.meter + "%";
        }

        var ms = ctx.getMatrices();
        ms.push();
        ms.translate(x + w / 2f, y + 0.5f, 0);
        ms.scale(0.7f, 0.7f, 1f);
        ctx.drawCenteredTextWithShadow(tr, label, 0, 0, 0xFFFFFFFF);
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
