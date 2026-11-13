package com.jjs.gojo.client;

import com.jjs.gojo.Ability;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;

/** Ability bar (bottom right): icon, keybind, cooldown countdown and a green READY bar. Sorcerers only. */
public final class GojoHud {
    private record Entry(Ability ability, String glyph, int color) {}

    private static final Entry[] ENTRIES = {
            new Entry(Ability.LAPIS_BLUE, "B", 0xFF3A7BFF),
            new Entry(Ability.REVERSAL_RED, "R", 0xFFFF3A3A),
            new Entry(Ability.RAPID_PUNCHES, "P", 0xFFFFA83A),
            new Entry(Ability.TWOFOLD_KICK, "K", 0xFFFFD83A),
            new Entry(Ability.R_SPECIAL, "R*", 0xFFB04AFF),
            new Entry(Ability.RCT, "+", 0xFF4AFF8A),
            new Entry(Ability.DASH, ">>", 0xFFCCCCCC),
    };

    private static final int SIZE = 32;
    private static final int GAP = 4;

    private GojoHud() {}

    private static KeyBinding key(Ability a) {
        return switch (a) {
            case LAPIS_BLUE -> GojoClient.BLUE;
            case REVERSAL_RED -> GojoClient.RED;
            case RAPID_PUNCHES -> GojoClient.PUNCHES;
            case TWOFOLD_KICK -> GojoClient.KICK;
            case R_SPECIAL -> GojoClient.SPECIAL;
            case RCT -> GojoClient.RCT;
            case DASH -> GojoClient.DASH;
        };
    }

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || ClientState.type != 2) return;

        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int total = ENTRIES.length * (SIZE + GAP) - GAP;
        int x0 = sw - total - 10;
        int y = sh - SIZE - 30;

        for (int i = 0; i < ENTRIES.length; i++) {
            Entry e = ENTRIES[i];
            int x = x0 + i * (SIZE + GAP);
            int id = e.ability().id;
            int remaining = ClientState.remaining(id);
            boolean ready = remaining <= 0;

            // frame + background
            ctx.fill(x - 1, y - 1, x + SIZE + 1, y + SIZE + 1, ready ? 0xFF55FF55 : 0xFF555555);
            ctx.fill(x, y, x + SIZE, y + SIZE, 0xCC101020);
            ctx.fill(x + 2, y + 2, x + SIZE - 2, y + 5, ready ? e.color() : 0xFF444444);

            // glyph icon
            var ms = ctx.getMatrices();
            ms.push();
            ms.translate(x + SIZE / 2f, y + 9, 0);
            ms.scale(1.6f, 1.6f, 1f);
            ctx.drawCenteredTextWithShadow(tr, e.glyph(), 0, 0, ready ? e.color() : 0xFF777777);
            ms.pop();

            if (ready) {
                ctx.fill(x + 3, y + SIZE - 5, x + SIZE - 3, y + SIZE - 3, 0xFF55FF55);
            } else {
                float frac = Math.min(1f, remaining / (float) ClientState.total(id));
                int h = (int) ((SIZE - 2) * frac);
                ctx.fill(x + 1, y + 1, x + SIZE - 1, y + 1 + h, 0xB0000000);
                String secs = String.format("%.1f", remaining / 20f);
                ctx.drawCenteredTextWithShadow(tr, secs, x + SIZE / 2, y + SIZE / 2 - 4, 0xFFFFFF);
            }

            // keybind label
            String keyName = key(e.ability()).getBoundKeyLocalizedText().getString();
            if (tr.getWidth(keyName) > SIZE + GAP) keyName = tr.trimToWidth(keyName, SIZE + GAP);
            ctx.drawCenteredTextWithShadow(tr, keyName, x + SIZE / 2, y + SIZE + 3, 0xFFFFFF);
        }
    }
}
