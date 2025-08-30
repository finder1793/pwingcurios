package com.example.pwingcurios.neoforge;

import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import io.netty.buffer.Unpooled;

@Mod(value = "pwingcurios_example_neoforge")
public class ExampleNeoForgeMod {
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

    public ExampleNeoForgeMod() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::clientSetup);
        modBus.addListener(ExampleNeoForgeMod::onRegisterKeyMappings);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(new ClientHandlers());
    }

    private void clientSetup(FMLClientSetupEvent evt) {
        // No-op; key mappings registered via event above
    }

    private static void sendUtfPacket(String utf, String... extra) {
        var mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(utf);
        for (String s : extra) buf.writeUtf(s);
        CustomPacketPayload payload = new UtfPayload(buf);
        mc.getConnection().send(new ServerboundCustomPayloadPacket(payload));
    }

    private static void sendUtfUtfInt(String cmd, String utf2, int value) {
        var mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(cmd);
        buf.writeUtf(utf2);
        buf.writeInt(value);
        CustomPacketPayload payload = new UtfPayload(buf);
        mc.getConnection().send(new ServerboundCustomPayloadPacket(payload));
    }

    private static final class UtfPayload implements CustomPacketPayload {
        private final FriendlyByteBuf data;
        private UtfPayload(FriendlyByteBuf data) { this.data = data; }
        @Override public ResourceLocation id() { return PC_CHANNEL; }
        @Override public void write(FriendlyByteBuf out) { out.writeBytes(data.copy()); }
    }

    private static class ClientHandlers {
        @SubscribeEvent
        public void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
            LOGGER.info("[PwingCuriosExampleNeo] Client logging in: querying SLOTS");
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
        OPEN_GUI = new KeyMapping("key.pwingcurios_example_neoforge.open", GLFW.GLFW_KEY_O, "key.categories.misc");
        EQUIP_HAND_TO_RING = new KeyMapping("key.pwingcurios_example_neoforge.equip_ring", GLFW.GLFW_KEY_P, "key.categories.misc");
        UNEQUIP_RING = new KeyMapping("key.pwingcurios_example_neoforge.unequip_ring", GLFW.GLFW_KEY_L, "key.categories.misc");
        GET_EQUIPPED = new KeyMapping("key.pwingcurios_example_neoforge.get_equipped", GLFW.GLFW_KEY_K, "key.categories.misc");
        GET_RULES = new KeyMapping("key.pwingcurios_example_neoforge.get_rules", GLFW.GLFW_KEY_J, "key.categories.misc");
        SWAP_RING = new KeyMapping("key.pwingcurios_example_neoforge.swap_ring", GLFW.GLFW_KEY_H, "key.categories.misc");
        EQUIP_INV_RING0 = new KeyMapping("key.pwingcurios_example_neoforge.equip_inv_ring0", GLFW.GLFW_KEY_G, "key.categories.misc");
        CLEAR_RING = new KeyMapping("key.pwingcurios_example_neoforge.clear_ring", GLFW.GLFW_KEY_C, "key.categories.misc");
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
