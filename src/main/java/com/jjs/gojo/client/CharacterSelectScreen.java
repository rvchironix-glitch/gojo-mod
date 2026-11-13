package com.jjs.gojo.client;

import com.jjs.gojo.GojoNet;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/** First-join character selection with a confirmation step. Cannot be closed with Esc. */
public class CharacterSelectScreen extends Screen {
    private static final int CARD_W = 190;
    private static final int CARD_H = 170;
    private static final int GAP = 30;

    private static final int SORCERER = 2;
    private static final int HUMAN = 1;

    private int confirming = 0;
    private boolean sent = false;

    public CharacterSelectScreen() {
        super(Text.literal("Choose your path"));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private int sorcererX() {
        return width / 2 - CARD_W - GAP / 2;
    }

    private int humanX() {
        return width / 2 + GAP / 2;
    }

    private int cardY() {
        return height / 2 - CARD_H / 2 + 10;
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);

        var ms = ctx.getMatrices();
        ms.push();
        ms.translate(width / 2f, cardY() - 38, 0);
        ms.scale(2f, 2f, 1f);
        ctx.drawCenteredTextWithShadow(textRenderer, "CHOOSE YOUR PATH", 0, 0, 0xFFFFFF);
        ms.pop();
        ctx.drawCenteredTextWithShadow(textRenderer, "This can only be chosen once.", width / 2, cardY() - 16, 0xAAAAAA);

        boolean locked = confirming != 0;
        drawCard(ctx, sorcererX(), cardY(), "SORCERER",
                "Master cursed techniques and wield Gojo's abilities.",
                0xFF0B1F4D, 0xFF1D5BFF, 0xFF6FA8FF, 0xFFBBD6FF,
                !locked && in(mouseX, mouseY, sorcererX(), cardY(), CARD_W, CARD_H));
        drawCard(ctx, humanX(), cardY(), "HUMAN",
                "Experience Minecraft without cursed techniques.",
                0xFF202020, 0xFF4A4A4A, 0xFFAAAAAA, 0xFFE0E0E0,
                !locked && in(mouseX, mouseY, humanX(), cardY(), CARD_W, CARD_H));

        if (confirming != 0) {
            drawConfirm(ctx, mouseX, mouseY);
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawCard(DrawContext ctx, int x, int y, String title, String desc,
                          int top, int bottom, int accent, int titleColor, boolean hovered) {
        ctx.fillGradient(x, y, x + CARD_W, y + CARD_H, top, bottom);
        int b = hovered ? 0xFFFFFFFF : accent;
        int t = hovered ? 3 : 2;
        ctx.fill(x, y, x + CARD_W, y + t, b);
        ctx.fill(x, y + CARD_H - t, x + CARD_W, y + CARD_H, b);
        ctx.fill(x, y, x + t, y + CARD_H, b);
        ctx.fill(x + CARD_W - t, y, x + CARD_W, y + CARD_H, b);

        var ms = ctx.getMatrices();
        ms.push();
        ms.translate(x + CARD_W / 2f, y + 20, 0);
        ms.scale(2f, 2f, 1f);
        ctx.drawCenteredTextWithShadow(textRenderer, title, 0, 0, titleColor);
        ms.pop();

        List<OrderedText> lines = textRenderer.wrapLines(Text.literal(desc), CARD_W - 24);
        int ly = y + 62;
        for (OrderedText line : lines) {
            ctx.drawTextWithShadow(textRenderer, line, x + 12, ly, 0xFFFFFF);
            ly += 11;
        }
        ctx.drawCenteredTextWithShadow(textRenderer, hovered ? "> Click to select <" : "Click to select",
                x + CARD_W / 2, y + CARD_H - 18, hovered ? 0xFFFFFF : 0xAAAAAA);
    }

    private int dialogX() {
        return width / 2 - 160;
    }

    private int dialogY() {
        return height / 2 - 65;
    }

    private void drawConfirm(DrawContext ctx, int mouseX, int mouseY) {
        ctx.fill(0, 0, width, height, 0xC0000000);
        int dx = dialogX();
        int dy = dialogY();
        int dw = 320;
        int dh = 130;
        ctx.fill(dx - 2, dy - 2, dx + dw + 2, dy + dh + 2, confirming == SORCERER ? 0xFF6FA8FF : 0xFFAAAAAA);
        ctx.fill(dx, dy, dx + dw, dy + dh, 0xFF12121C);

        String name = confirming == SORCERER ? "SORCERER" : "HUMAN";
        ctx.drawCenteredTextWithShadow(textRenderer, "Become a " + name + "?", width / 2, dy + 12, 0xFFFFFF);
        List<OrderedText> warn = textRenderer.wrapLines(
                Text.literal("Your choice is permanent and cannot be changed in this world."), dw - 40);
        int wy = dy + 34;
        for (OrderedText line : warn) {
            int w = textRenderer.getWidth(line);
            ctx.drawTextWithShadow(textRenderer, line, width / 2 - w / 2, wy, 0xFF5555);
            wy += 11;
        }

        int by = dy + dh - 34;
        boolean hc = in(mouseX, mouseY, dx + 20, by, 130, 24);
        boolean hb = in(mouseX, mouseY, dx + dw - 150, by, 130, 24);
        ctx.fill(dx + 20, by, dx + 150, by + 24, hc ? 0xFF2FBF5F : 0xFF1F8F45);
        ctx.fill(dx + dw - 150, by, dx + dw - 20, by + 24, hb ? 0xFF6A6A6A : 0xFF4A4A4A);
        ctx.drawCenteredTextWithShadow(textRenderer, "Confirm", dx + 85, by + 8, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, "Back", dx + dw - 85, by + 8, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        if (confirming != 0) {
            int dx = dialogX();
            int by = dialogY() + 130 - 34;
            if (in(mx, my, dx + 20, by, 130, 24)) {
                send(confirming);
            } else if (in(mx, my, dx + 320 - 150, by, 130, 24)) {
                confirming = 0;
            }
            return true;
        }

        if (in(mx, my, sorcererX(), cardY(), CARD_W, CARD_H)) {
            confirming = SORCERER;
            return true;
        }
        if (in(mx, my, humanX(), cardY(), CARD_W, CARD_H)) {
            confirming = HUMAN;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void send(int type) {
        if (sent) return;
        sent = true;
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeByte(type);
        ClientPlayNetworking.send(GojoNet.CHOOSE, buf);
        // The screen closes when the server answers with the saved choice (see GojoClient).
    }
}
