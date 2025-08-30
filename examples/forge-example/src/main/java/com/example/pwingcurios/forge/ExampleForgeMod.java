package com.example.pwingcurios.forge;

import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import io.netty.buffer.Unpooled;

@Mod(value = "pwingcurios_example")
public class ExampleForgeMod {
    public static final String MODID = "pwingcurios_example";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation PC_CHANNEL = new ResourceLocation("pwingcurios", "api");

    private static KeyMapping OPEN_GUI;
    private static KeyMapping EQUIP_HAND_TO_RING;
    private static KeyMapping UNEQUIP_RING;
    private static KeyMapping GET_EQUIPPED;
    private static KeyMapping GET_RULES;
    private static KeyMapping SWAP_RING;
    private static KeyMapping EQUIP_INV_RING0;
    private static KeyMapping CLEAR_RING;

    public ExampleForgeMod() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::clientSetup);
    }

    private void clientSetup(FMLClientSetupEvent evt) {
        // Register keys on client bus
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ClientHandlers());
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ExampleForgeMod::onRegisterKeyMappings);
    }

    private static void sendUtfPacket(String utf, String... extra) {
        var mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(utf);
        for (String s : extra) buf.writeUtf(s);
        mc.getConnection().send(new ServerboundCustomPayloadPacket(PC_CHANNEL, buf));
    }

    private static void sendUtfUtfInt(String cmd, String utf2, int value) {
        var mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(cmd);
        buf.writeUtf(utf2);
        buf.writeInt(value);
        mc.getConnection().send(new ServerboundCustomPayloadPacket(PC_CHANNEL, buf));
    }

    private static class ClientHandlers {
        @SubscribeEvent
        public void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
            LOGGER.info("[PwingCuriosExample] Client logging in: registering channel pwingcurios:api");
            // Optionally request the slots right after join
            sendUtfPacket("SLOTS");
        }

        @SubscribeEvent
        public void onKey(InputEvent.Key event) {
            if (OPEN_GUI != null && OPEN_GUI.consumeClick()) {
                sendUtfPacket("OPEN");
                toast("Sent OPEN to pwingcurios:api");
            }
            if (EQUIP_HAND_TO_RING != null && EQUIP_HAND_TO_RING.consumeClick()) {
                sendUtfPacket("EQUIP_HAND", "ring_1");
                toast("Sent EQUIP_HAND ring_1");
            }
            if (UNEQUIP_RING != null && UNEQUIP_RING.consumeClick()) {
                sendUtfPacket("UNEQUIP", "ring_1");
                toast("Sent UNEQUIP ring_1");
            }
            if (GET_EQUIPPED != null && GET_EQUIPPED.consumeClick()) {
                sendUtfPacket("GET_EQUIPPED");
                toast("Requested GET_EQUIPPED");
            }
            if (GET_RULES != null && GET_RULES.consumeClick()) {
                sendUtfPacket("GET_SLOT_RULES");
                toast("Requested GET_SLOT_RULES");
            }
            if (SWAP_RING != null && SWAP_RING.consumeClick()) {
                sendUtfPacket("SWAP", "ring_1");
                toast("Sent SWAP ring_1");
            }
            if (EQUIP_INV_RING0 != null && EQUIP_INV_RING0.consumeClick()) {
                sendUtfUtfInt("EQUIP_INV", "ring_1", 0);
                toast("Sent EQUIP_INV ring_1 from inv 0");
            }
            if (CLEAR_RING != null && CLEAR_RING.consumeClick()) {
                sendUtfPacket("CLEAR_SLOT", "ring_1");
                toast("Sent CLEAR_SLOT ring_1");
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent e) {
        OPEN_GUI = new KeyMapping("key.pwingcurios_example.open", GLFW.GLFW_KEY_O, "key.categories.misc");
        EQUIP_HAND_TO_RING = new KeyMapping("key.pwingcurios_example.equip_ring", GLFW.GLFW_KEY_P, "key.categories.misc");
        UNEQUIP_RING = new KeyMapping("key.pwingcurios_example.unequip_ring", GLFW.GLFW_KEY_L, "key.categories.misc");
        GET_EQUIPPED = new KeyMapping("key.pwingcurios_example.get_equipped", GLFW.GLFW_KEY_K, "key.categories.misc");
        GET_RULES = new KeyMapping("key.pwingcurios_example.get_rules", GLFW.GLFW_KEY_J, "key.categories.misc");
        SWAP_RING = new KeyMapping("key.pwingcurios_example.swap_ring", GLFW.GLFW_KEY_H, "key.categories.misc");
        EQUIP_INV_RING0 = new KeyMapping("key.pwingcurios_example.equip_inv_ring0", GLFW.GLFW_KEY_G, "key.categories.misc");
        CLEAR_RING = new KeyMapping("key.pwingcurios_example.clear_ring", GLFW.GLFW_KEY_C, "key.categories.misc");
        e.register(OPEN_GUI);
        e.register(EQUIP_HAND_TO_RING);
        e.register(UNEQUIP_RING);
        e.register(GET_EQUIPPED);
        e.register(GET_RULES);
        e.register(SWAP_RING);
        e.register(EQUIP_INV_RING0);
        e.register(CLEAR_RING);
    }

    private static void toast(String msg) {
        var mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(msg), true);
    }
}
