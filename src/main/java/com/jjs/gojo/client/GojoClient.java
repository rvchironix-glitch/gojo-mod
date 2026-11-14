package com.jjs.gojo.client;

import com.jjs.gojo.Ability;
import com.jjs.gojo.GojoNet;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import org.lwjgl.glfw.GLFW;

public class GojoClient implements ClientModInitializer {
    private static final String CAT = "key.categories.gojomoveset";

    public static KeyBinding DASH, BLOCK, SPECIAL, BLUE, RED, PUNCHES, KICK, RCT, ULT, CHARACTER, PAD, CUSTOMIZE;

    /** Touch players toggle blocking with the Ability Pad (a tap cannot be held). */
    public static boolean touchBlock;

    // double-tap tracking: 0 fwd, 1 back, 2 left, 3 right
    private static final boolean[] prevDown = new boolean[4];
    private static final int[] lastPress = {-100, -100, -100, -100};
    private static int clientTick = 0;
    private static final int DOUBLE_TAP_WINDOW = 7;
    private static boolean lastBlockSent = false;

    private static KeyBinding reg(String name, int key) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(name, InputUtil.Type.KEYSYM, key, CAT));
    }

    @Override
    public void onInitializeClient() {
        DASH = reg("key.gojomoveset.dash", GLFW.GLFW_KEY_G);
        BLOCK = reg("key.gojomoveset.block", GLFW.GLFW_KEY_B);
        SPECIAL = reg("key.gojomoveset.special", GLFW.GLFW_KEY_R);
        BLUE = reg("key.gojomoveset.blue", GLFW.GLFW_KEY_Z);
        RED = reg("key.gojomoveset.red", GLFW.GLFW_KEY_X);
        // Rapid Punches moved from C to H so Reverse Cursed Technique can default to C.
        PUNCHES = reg("key.gojomoveset.punches", GLFW.GLFW_KEY_H);
        // Twofold Kick moved from V to N so the Ultimate can default to V.
        KICK = reg("key.gojomoveset.kick", GLFW.GLFW_KEY_N);
        ULT = reg("key.gojomoveset.ultimate", GLFW.GLFW_KEY_V);
        CHARACTER = reg("key.gojomoveset.character", GLFW.GLFW_KEY_J);
        PAD = reg("key.gojomoveset.pad", GLFW.GLFW_KEY_O);
        CUSTOMIZE = reg("key.gojomoveset.customize", GLFW.GLFW_KEY_K);
        RCT = reg("key.gojomoveset.rct", GLFW.GLFW_KEY_C);

        ClientPlayNetworking.registerGlobalReceiver(GojoNet.SYNC, (client, handler, buf, responseSender) -> {
            int type = buf.readByte();
            client.execute(() -> {
                ClientState.type = type;
                ClientState.synced = true;
                if (type != 0 && client.currentScreen instanceof CharacterSelectScreen) {
                    client.setScreen(null);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(GojoNet.ULT, (client, handler, buf, responseSender) -> {
            int hero = buf.readByte();
            float meter = buf.readFloat();
            int remaining = buf.readInt();
            int total = buf.readInt();
            client.execute(() -> ClientState.setUltimate(hero, meter, remaining, total));
        });

        ClientPlayNetworking.registerGlobalReceiver(GojoNet.COOLDOWN, (client, handler, buf, responseSender) -> {
            int id = buf.readByte();
            int remaining = buf.readInt();
            int total = buf.readInt();
            client.execute(() -> ClientState.setCooldown(id, remaining, total));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientState.reset();
            lastBlockSent = false;
            touchBlock = false;
        });

        HudRenderCallback.EVENT.register((ctx, tickDelta) -> GojoHud.render(ctx, tickDelta));
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        ClientState.reset();
    }

    private void onTick(MinecraftClient mc) {
        clientTick++;
        ClientState.tick++;
        ClientState.animate();

        if (mc.player == null) {
            prevDown[0] = prevDown[1] = prevDown[2] = prevDown[3] = false;
            lastBlockSent = false;
            return;
        }

        // Server told us this player has not chosen yet: show the selection screen.
        if (ClientState.synced && ClientState.type == 0 && mc.currentScreen == null) {
            mc.setScreen(new CharacterSelectScreen());
        }

        // Sorcerer who has not picked Gojo/Yuji yet: show the character screen.
        if (ClientState.synced && ClientState.heroSynced && ClientState.type == 2
                && ClientState.hero == 0 && mc.currentScreen == null) {
            mc.setScreen(new HeroSelectScreen());
        }
        while (CHARACTER.wasPressed()) {
            if (ClientState.type == 2 && ClientState.heroSynced && !ClientState.awakened()
                    && mc.currentScreen == null) {
                mc.setScreen(new HeroSelectScreen());
            }
        }

        while (PAD.wasPressed()) {
            if (ClientState.type == 2 && ClientState.hero != 0 && mc.currentScreen == null) {
                mc.setScreen(new AbilityPadScreen());
            }
        }
        while (CUSTOMIZE.wasPressed()) {
            if (mc.currentScreen != null) continue;
            if (ClientState.type == 2 && ClientState.hero != 0) {
                mc.setScreen(new CustomizeControlsScreen());
            } else {
                mc.player.sendMessage(net.minecraft.text.Text.literal("Customize Controls is for Sorcerers"), true);
            }
        }

        if (mc.currentScreen != null) {
            prevDown[0] = prevDown[1] = prevDown[2] = prevDown[3] = false;
            sendBlock(false);
            return;
        }

        // Double-tap dash
        KeyBinding[] moves = {mc.options.forwardKey, mc.options.backKey, mc.options.leftKey, mc.options.rightKey};
        for (int i = 0; i < 4; i++) {
            boolean down = moves[i].isPressed();
            if (down && !prevDown[i]) {
                if (clientTick - lastPress[i] <= DOUBLE_TAP_WINDOW) {
                    sendAbility(Ability.DASH, i);
                    lastPress[i] = -100;
                } else {
                    lastPress[i] = clientTick;
                }
            }
            prevDown[i] = down;
        }

        // Keybind dash: uses currently held direction, forward by default
        while (DASH.wasPressed()) {
            int dir = 0;
            if (mc.options.backKey.isPressed()) dir = 1;
            else if (mc.options.leftKey.isPressed()) dir = 2;
            else if (mc.options.rightKey.isPressed()) dir = 3;
            sendAbility(Ability.DASH, dir);
        }

        while (BLUE.wasPressed()) sendAbility(Ability.LAPIS_BLUE, 0);
        while (RED.wasPressed()) sendAbility(Ability.REVERSAL_RED, 0);
        while (PUNCHES.wasPressed()) sendAbility(Ability.RAPID_PUNCHES, 0);
        while (KICK.wasPressed()) sendAbility(Ability.TWOFOLD_KICK, 0);
        while (SPECIAL.wasPressed()) sendAbility(Ability.R_SPECIAL, 0);
        while (RCT.wasPressed()) sendAbility(Ability.RCT, 0);
        while (ULT.wasPressed()) sendAbility(Ability.ULTIMATE, 0);

        sendBlock(isBlocking());
    }

    public static boolean isBlocking() {
        return BLOCK != null && (BLOCK.isPressed() || touchBlock);
    }

    /** The move-slot / ability id the server expects for a HUD button (the server maps slots per character). */
    public static Ability requestFor(GojoHud.Entry e) {
        KeyBinding k = e.key();
        if (k == BLUE) return Ability.LAPIS_BLUE;
        if (k == RED) return Ability.REVERSAL_RED;
        if (k == PUNCHES) return Ability.RAPID_PUNCHES;
        if (k == KICK) return Ability.TWOFOLD_KICK;
        if (k == SPECIAL) return Ability.R_SPECIAL;
        if (k == RCT) return Ability.RCT;
        if (k == ULT) return Ability.ULTIMATE;
        return Ability.DASH;
    }

    public static void sendAbility(Ability a, int dir) {
        // Display-side gate only; the server enforces the real restriction.
        if (ClientState.type != 2 || ClientState.hero == 0) return;
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeByte(a.id);
        buf.writeByte(dir);
        ClientPlayNetworking.send(GojoNet.ABILITY, buf);
    }

    private static void sendBlock(boolean wantsBlock) {
        boolean blocking = wantsBlock && ClientState.type == 2;
        if (blocking == lastBlockSent) return;
        lastBlockSent = blocking;
        if (!ClientPlayNetworking.canSend(GojoNet.BLOCK)) return;
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeBoolean(blocking);
        ClientPlayNetworking.send(GojoNet.BLOCK, buf);
    }
}
