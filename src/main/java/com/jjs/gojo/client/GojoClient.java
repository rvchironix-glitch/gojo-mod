package com.jjs.gojo.client;

import com.jjs.gojo.Ability;
import com.jjs.gojo.GojoNet;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import io.netty.buffer.Unpooled;
import org.lwjgl.glfw.GLFW;

public class GojoClient implements ClientModInitializer {
    private static final String CAT = "key.categories.gojomoveset";

    public static KeyBinding DASH, BLOCK, SPECIAL, BLUE, RED, PUNCHES, KICK;

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
        PUNCHES = reg("key.gojomoveset.punches", GLFW.GLFW_KEY_C);
        KICK = reg("key.gojomoveset.kick", GLFW.GLFW_KEY_V);

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient mc) {
        clientTick++;
        if (mc.player == null || mc.currentScreen != null) {
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

        sendBlock(BLOCK.isPressed());
    }

    private static void sendAbility(Ability a, int dir) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeByte(a.id);
        buf.writeByte(dir);
        ClientPlayNetworking.send(GojoNet.ABILITY, buf);
    }

    private static void sendBlock(boolean blocking) {
        if (blocking == lastBlockSent) return;
        lastBlockSent = blocking;
        if (!ClientPlayNetworking.canSend(GojoNet.BLOCK)) return;
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeBoolean(blocking);
        ClientPlayNetworking.send(GojoNet.BLOCK, buf);
    }
}
