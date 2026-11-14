package com.jjs.gojo.client;

import com.jjs.gojo.Ability;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Touch Ability Pad: big tappable ability buttons, laid out per the player's saved touch layout.
 *
 * Java Edition gives mods no multi-touch input while the game is running, and the launcher owns the
 * movement joystick and camera drag. So the pad is a screen you open with one tap (bind a launcher button
 * to the "Ability Pad" key); tapping an ability fires it and closes the pad. Nothing here can interfere with
 * the joystick or the camera, because nothing is drawn over gameplay. All abilities are still validated by
 * the server exactly like keyboard presses.
 */
public class AbilityPadScreen extends Screen {
    public AbilityPadScreen() {
        super(Text.literal("Ability Pad"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x70000000);
        ctx.drawCenteredTextWithShadow(textRenderer, "Tap an ability  -  tap anywhere else to close",
                width / 2, 8, 0xFFFFFFFF);

        GojoHud.drawExhausted(ctx, textRenderer, width, height);
        HudLayout.Data layout = HudLayout.get();
        GojoHud.Entry[] list = GojoHud.touchEntries();
        for (int i = 0; i < list.length; i++) {
            HudLayout.Item it = layout.touchButtons.getOrDefault(list[i].id(), new HudLayout.Item());
            int[] r = GojoHud.touchRect(width, height, i, it);
            // keep buttons clearly visible on the pad even if the player faded them in the HUD
            GojoHud.drawSlot(ctx, textRenderer, list[i], r[0], r[1], r[2], Math.max(0.6f, it.alpha), true);
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        HudLayout.Data layout = HudLayout.get();
        GojoHud.Entry[] list = GojoHud.touchEntries();
        for (int i = list.length - 1; i >= 0; i--) {
            HudLayout.Item it = layout.touchButtons.getOrDefault(list[i].id(), new HudLayout.Item());
            int[] r = GojoHud.touchRect(width, height, i, it);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3] + 12) {
                activate(list[i]);
                return true;
            }
        }
        close(); // tap on empty space
        return true;
    }

    private void activate(GojoHud.Entry e) {
        if (ClientState.exhausted() && e.ability() != Ability.RCT) return; // burnout: only RCT (server enforces too)
        if (e.ability() == null) { // Block toggles (a held key is not possible with a tap)
            GojoClient.touchBlock = !GojoClient.touchBlock;
            close();
            return;
        }
        if (e.ability() == Ability.R_SPECIAL && !ClientState.awakened()) {
            return; // locked until Limitless Awakened; stay open
        }
        GojoClient.sendAbility(GojoClient.requestFor(e), 0);
        close();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (GojoClient.PAD.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
