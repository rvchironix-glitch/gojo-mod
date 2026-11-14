package com.jjs.gojo.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * CUSTOMIZE CONTROLS: move, resize and fade the PC HUD (ability column, ultimate meter) and the touch HUD
 * (every Ability Pad button + meter). Layouts are saved per player and per mode (PC / touch).
 *
 * This screen never fires abilities: Edit Mode ON drags/resizes, Edit Mode OFF is a view-only preview.
 */
public class CustomizeControlsScreen extends Screen {
    private int mode; // 0 = PC HUD, 1 = touch HUD
    private boolean edit = true;
    private String selected;
    private boolean dragging;

    private ButtonWidget pcTab;
    private ButtonWidget touchTab;
    private ButtonWidget editBtn;
    private ButtonWidget touchModeBtn;

    public CustomizeControlsScreen() {
        super(Text.literal("CUSTOMIZE CONTROLS"));
        this.mode = TouchMode.active() ? 1 : 0;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void init() {
        int gap = 2;
        int bw = Math.min(96, (width - 16) / 6 - gap);
        int total = 6 * (bw + gap) - gap;
        int x0 = (width - total) / 2;
        int y1 = height - 50;
        int y2 = height - 26;

        pcTab = addDrawableChild(ButtonWidget.builder(Text.literal("PC HUD"), b -> setMode(0))
                .dimensions(x0, y1, bw, 20).build());
        touchTab = addDrawableChild(ButtonWidget.builder(Text.literal("TOUCH HUD"), b -> setMode(1))
                .dimensions(x0 + (bw + gap), y1, bw, 20).build());
        editBtn = addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
            edit = !edit;
            dragging = false;
            refreshLabels();
        }).dimensions(x0 + 2 * (bw + gap), y1, bw, 20).build());
        touchModeBtn = addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
            HudLayout.Data d = HudLayout.get();
            d.touchMode = (d.touchMode + 1) % 3;
            HudLayout.save();
            refreshLabels();
        }).dimensions(x0 + 3 * (bw + gap), y1, bw * 2 + gap, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Size -"), b -> resize(-0.1f))
                .dimensions(x0, y2, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Size +"), b -> resize(0.1f))
                .dimensions(x0 + (bw + gap), y2, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Opacity -"), b -> fade(-0.1f))
                .dimensions(x0 + 2 * (bw + gap), y2, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Opacity +"), b -> fade(0.1f))
                .dimensions(x0 + 3 * (bw + gap), y2, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset Layout"), b -> {
            if (mode == 0) HudLayout.resetPc(); else HudLayout.resetTouch();
            selected = null;
        }).dimensions(x0 + 4 * (bw + gap), y2, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(x0 + 5 * (bw + gap), y2, bw, 20).build());
        refreshLabels();
    }

    private void setMode(int m) {
        mode = m;
        selected = null;
        dragging = false;
        refreshLabels();
    }

    private void refreshLabels() {
        pcTab.setMessage(Text.literal(mode == 0 ? "[PC HUD]" : "PC HUD"));
        touchTab.setMessage(Text.literal(mode == 1 ? "[TOUCH HUD]" : "TOUCH HUD"));
        editBtn.setMessage(Text.literal("Edit Mode: " + (edit ? "ON" : "OFF")));
        int tm = HudLayout.get().touchMode;
        String name = tm == 0 ? "AUTO (" + (TouchMode.launcherDetected() ? "mobile detected" : "not detected") + ")"
                : tm == 1 ? "ON" : "OFF";
        touchModeBtn.setMessage(Text.literal("Use touch HUD: " + name));
    }

    private HudLayout.Item itemFor(String id) {
        HudLayout.Data d = HudLayout.get();
        return switch (id) {
            case "pc_column" -> d.pcColumn;
            case "pc_meter" -> d.pcMeter;
            case "touch_meter" -> d.touchMeter;
            default -> HudLayout.touchButton(id);
        };
    }

    private void resize(float delta) {
        if (!edit || selected == null) return;
        HudLayout.Item it = itemFor(selected);
        it.scale += delta;
        it.clampValues();
        HudLayout.save();
    }

    private void fade(float delta) {
        if (!edit || selected == null) return;
        HudLayout.Item it = itemFor(selected);
        it.alpha += delta;
        it.clampValues();
        HudLayout.save();
    }

    // ------------------------------------------------------------------ geometry / hit testing

    private int[] rectOf(String id) {
        HudLayout.Data d = HudLayout.get();
        switch (id) {
            case "pc_meter":
                return GojoHud.meterRect(client, width, height, d.pcMeter);
            case "touch_meter":
                return GojoHud.meterRect(client, width, height, d.touchMeter);
            case "pc_column": {
                GojoHud.Entry[] list = GojoHud.entries();
                int[] c = GojoHud.columnRect(width, height, list.length, d.pcColumn);
                return new int[]{c[0] - 34, c[1], c[2] + 34, c[3]}; // include the key labels on the left
            }
            default: {
                GojoHud.Entry[] list = GojoHud.touchEntries();
                for (int i = 0; i < list.length; i++) {
                    if (list[i].id().equals(id)) {
                        int[] r = GojoHud.touchRect(width, height, i, HudLayout.touchButton(id));
                        return new int[]{r[0], r[1], r[2], r[3] + 12};
                    }
                }
                return new int[]{0, 0, 0, 0};
            }
        }
    }

    private static boolean in(double mx, double my, int[] r) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    private String hit(double mx, double my) {
        if (mode == 0) {
            if (in(mx, my, rectOf("pc_meter"))) return "pc_meter";
            if (in(mx, my, rectOf("pc_column"))) return "pc_column";
            return null;
        }
        if (in(mx, my, rectOf("touch_meter"))) return "touch_meter";
        GojoHud.Entry[] list = GojoHud.touchEntries();
        for (int i = list.length - 1; i >= 0; i--) {
            if (in(mx, my, rectOf(list[i].id()))) return list[i].id();
        }
        return null;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        HudLayout.Data d = HudLayout.get();

        if (mode == 0) {
            GojoHud.drawMeter(ctx, textRenderer, GojoHud.meterRect(client, width, height, d.pcMeter),
                    d.pcMeter.alpha, delta, true);
            GojoHud.Entry[] list = GojoHud.entries();
            GojoHud.drawColumn(ctx, textRenderer, list,
                    GojoHud.columnRect(width, height, list.length, d.pcColumn), d.pcColumn.alpha);
        } else {
            GojoHud.drawMeter(ctx, textRenderer, GojoHud.meterRect(client, width, height, d.touchMeter),
                    d.touchMeter.alpha, delta, true);
            GojoHud.Entry[] list = GojoHud.touchEntries();
            for (int i = 0; i < list.length; i++) {
                HudLayout.Item it = HudLayout.touchButton(list[i].id());
                int[] r = GojoHud.touchRect(width, height, i, it);
                GojoHud.drawSlot(ctx, textRenderer, list[i], r[0], r[1], r[2], it.alpha, true);
            }
        }

        if (edit) {
            String hover = hit(mouseX, mouseY);
            if (hover != null && !hover.equals(selected)) outline(ctx, rectOf(hover), 0x80FFFFFF);
            if (selected != null) outline(ctx, rectOf(selected), 0xFFFFD83A);
        }

        ctx.drawCenteredTextWithShadow(textRenderer, "CUSTOMIZE CONTROLS", width / 2, 8, 0xFFFFFFFF);
        String hint;
        if (!edit) {
            hint = "Edit Mode is OFF - preview only. Nothing here activates abilities.";
        } else if (selected == null) {
            hint = "Edit Mode: click an element, then drag it. Scroll or use Size / Opacity to adjust.";
        } else {
            HudLayout.Item it = itemFor(selected);
            hint = "Selected: " + selected.replace('_', ' ') + "   size " + String.format("%.1f", it.scale)
                    + "x   opacity " + Math.round(it.alpha * 100) + "%";
        }
        ctx.drawCenteredTextWithShadow(textRenderer, hint, width / 2, 22, 0xFFAAAAAA);
        if (mode == 1) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "Touch buttons appear on the Ability Pad (key: "
                            + GojoClient.PAD.getBoundKeyLocalizedText().getString() + ")",
                    width / 2, 34, 0xFF888888);
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    private static void outline(DrawContext ctx, int[] r, int color) {
        int x1 = r[0] - 2, y1 = r[1] - 2, x2 = r[0] + r[2] + 2, y2 = r[1] + r[3] + 2;
        ctx.fill(x1, y1, x2, y1 + 1, color);
        ctx.fill(x1, y2 - 1, x2, y2, color);
        ctx.fill(x1, y1, x1 + 1, y2, color);
        ctx.fill(x2 - 1, y1, x2, y2, color);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true; // buttons first
        if (!edit || button != 0) return false;
        selected = hit(mx, my);
        dragging = selected != null;
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (edit && dragging && selected != null && button == 0) {
            HudLayout.Item it = itemFor(selected);
            it.dx += (float) (dx / width);
            it.dy += (float) (dy / height);
            it.clampValues();
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging) {
            dragging = false;
            HudLayout.save();
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (edit && selected != null) {
            resize(amount > 0 ? 0.1f : -0.1f);
            return true;
        }
        return super.mouseScrolled(mx, my, amount);
    }

    @Override
    public void removed() {
        HudLayout.save();
    }
}
