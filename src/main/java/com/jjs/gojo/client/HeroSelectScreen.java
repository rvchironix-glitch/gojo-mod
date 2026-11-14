package com.jjs.gojo.client;

import com.jjs.gojo.GojoNet;
import com.jjs.gojo.Heroes;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Sorcerer character selection (Gojo / Yuji). Separate from the permanent Sorcerer/Human role.
 * Cannot be dismissed with Esc until a character has been chosen; afterwards J reopens it to switch
 * (the server refuses switches while transformed). Sukuna is a transformation, not a choice here.
 */
public class HeroSelectScreen extends Screen {
    private static final int CARD_W = 200;
    private static final int CARD_H = 180;
    private static final int GAP = 30;

    private int picked = 0;
    private int sentAtTick = -1000;

    public HeroSelectScreen() {
        super(Text.literal("Choose your sorcerer"));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return ClientState.hero != 0;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private int gojoX() {
        return width / 2 - CARD_W - GAP / 2;
    }

    private int yujiX() {
        return width / 2 + GAP / 2;
    }

    private int cardY() {
        return height / 2 - CARD_H / 2 + 10;
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void tick() {
        // close once the server confirms our pick
        if (picked != 0 && ClientState.hero == picked) {
            close();
        }
        // server refused (e.g. transformed): allow another try
        if (picked != 0 && ClientState.tick - sentAtTick > 40) {
            picked = 0;
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);

        var ms = ctx.getMatrices();
        ms.push();
        ms.translate(width / 2f, cardY() - 38, 0);
        ms.scale(2f, 2f, 1f);
        ctx.drawCenteredTextWithShadow(textRenderer, "CHOOSE YOUR SORCERER", 0, 0, 0xFFFFFF);
        ms.pop();
        String sub = ClientState.hero == 0 ? "Pick who you fight as." : "Switch anytime, except while transformed.";
        ctx.drawCenteredTextWithShadow(textRenderer, sub, width / 2, cardY() - 16, 0xAAAAAA);

        drawCard(ctx, gojoX(), cardY(), "GOJO",
                "The strongest sorcerer. Lapis Blue, Reversal Red, punches and kicks.",
                "Ultimate: Limitless Awakened (2:00)",
                0xFF0B1F4D, 0xFF1D5BFF, 0xFF6FA8FF, 0xFFBBD6FF,
                ClientState.hero == Heroes.GOJO, in(mouseX, mouseY, gojoX(), cardY(), CARD_W, CARD_H));
        drawCard(ctx, yujiX(), cardY(), "YUJI",
                "Raw fists and cursed energy. Divergent Fist, Black Flash and more.",
                "Ultimate: Binding Vow - Sukuna (1:00)",
                0xFF3A0F0F, 0xFFB83A1A, 0xFFFF8A4A, 0xFFFFD0A8,
                ClientState.hero == Heroes.YUJI, in(mouseX, mouseY, yujiX(), cardY(), CARD_W, CARD_H));
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawCard(DrawContext ctx, int x, int y, String title, String desc, String ult,
                          int top, int bottom, int accent, int titleColor, boolean current, boolean hovered) {
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

        int ly = y + 60;
        for (OrderedText line : textRenderer.wrapLines(Text.literal(desc), CARD_W - 24)) {
            ctx.drawTextWithShadow(textRenderer, line, x + 12, ly, 0xFFFFFF);
            ly += 11;
        }
        ly += 6;
        for (OrderedText line : textRenderer.wrapLines(Text.literal(ult), CARD_W - 24)) {
            ctx.drawTextWithShadow(textRenderer, line, x + 12, ly, accent);
            ly += 11;
        }
        String foot = current ? "Current choice" : (hovered ? "> Click to select <" : "Click to select");
        ctx.drawCenteredTextWithShadow(textRenderer, foot, x + CARD_W / 2, y + CARD_H - 18, hovered ? 0xFFFFFF : 0xAAAAAA);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && picked == 0) {
            if (in(mx, my, gojoX(), cardY(), CARD_W, CARD_H)) {
                pick(Heroes.GOJO);
                return true;
            }
            if (in(mx, my, yujiX(), cardY(), CARD_W, CARD_H)) {
                pick(Heroes.YUJI);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void pick(int hero) {
        if (hero == ClientState.hero) {
            close();
            return;
        }
        picked = hero;
        sentAtTick = ClientState.tick;
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeByte(hero);
        ClientPlayNetworking.send(GojoNet.HERO, buf);
    }
}
