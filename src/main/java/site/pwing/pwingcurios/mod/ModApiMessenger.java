package site.pwing.pwingcurios.mod;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import site.pwing.pwingcurios.Pwingcurios;
import site.pwing.pwingcurios.manager.SlotDefinition;

import java.nio.charset.StandardCharsets;

/**
 * Simple plugin messaging based mod API on channel "pwingcurios:api".
 *
 * Requests from client mods (sent to server on the channel):
 * - OPEN: opens the GUI for the sending player
 * - SLOTS: server replies with SLOTS;<count> then repeated [slotId,index,name]
 */
public class ModApiMessenger implements PluginMessageListener {
    public static final String CHANNEL = "pwingcurios:api";

    private final Pwingcurios plugin;

    public ModApiMessenger(Pwingcurios plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equalsIgnoreCase(channel)) return;
        if (player == null) return;

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String cmd = in.readUTF();
        if ("OPEN".equalsIgnoreCase(cmd)) {
            plugin.getAccessoriesGUI().open(player);
            return;
        }
        if ("SLOTS".equalsIgnoreCase(cmd)) {
            // Reply with slot definitions
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("SLOTS");
            out.writeInt(plugin.getAccessoryManager().getSlots().size());
            for (SlotDefinition def : plugin.getAccessoryManager().getSlots()) {
                out.writeUTF(def.getId());
                out.writeInt(def.getIndex());
                out.writeUTF(def.getName() == null ? def.getId() : def.getName());
                // Include optional placeholder visualization info so client mods can texture slots using server RP
                String phMat = def.getPlaceholderMaterial();
                Integer phCmd = def.getPlaceholderCustomModelData();
                String phName = def.getPlaceholderName();
                boolean hasPh = phMat != null || phCmd != null || phName != null;
                out.writeBoolean(hasPh);
                if (hasPh) {
                    out.writeBoolean(phMat != null);
                    if (phMat != null) out.writeUTF(phMat);
                    out.writeBoolean(phCmd != null);
                    if (phCmd != null) out.writeInt(phCmd);
                    out.writeBoolean(phName != null);
                    if (phName != null) out.writeUTF(phName);
                }
            }
            player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
            return;
        }
        if ("GET_EQUIPPED".equalsIgnoreCase(cmd)) {
            java.util.Map<String, org.bukkit.inventory.ItemStack> map = plugin.getAccessoryManager().getEquipped(player);
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("GET_EQUIPPED");
            out.writeInt(map.size());
            for (java.util.Map.Entry<String, org.bukkit.inventory.ItemStack> e : map.entrySet()) {
                String slotId = e.getKey();
                org.bukkit.inventory.ItemStack item = e.getValue();
                out.writeUTF(slotId);
                if (item == null || item.getType() == org.bukkit.Material.AIR) {
                    out.writeBoolean(false);
                } else {
                    out.writeBoolean(true);
                    out.writeUTF(item.getType().name());
                    String name = null;
                    try {
                        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                        name = (meta != null && meta.hasDisplayName()) ? meta.getDisplayName() : null;
                    } catch (Throwable ignored) {}
                    out.writeBoolean(name != null);
                    if (name != null) out.writeUTF(name);
                }
            }
            player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
            return;
        }
        if ("FIRST_EMPTY_HAND".equalsIgnoreCase(cmd)) {
            org.bukkit.inventory.ItemStack hand = player.getInventory().getItemInMainHand();
            java.util.Optional<String> slot = plugin.getAccessoryManager().firstEmptyCompatibleSlot(player, hand);
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("FIRST_EMPTY_HAND");
            out.writeBoolean(slot.isPresent());
            if (slot.isPresent()) out.writeUTF(slot.get());
            player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
            return;
        }
        if ("IS_ALLOWED_HAND".equalsIgnoreCase(cmd)) {
            String slotId = in.readUTF();
            org.bukkit.inventory.ItemStack hand = player.getInventory().getItemInMainHand();
            boolean allowed = plugin.getAccessoryManager().isAccessory(hand) && plugin.getAccessoryManager().isAllowedInSlot(hand, slotId);
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("IS_ALLOWED_HAND");
            out.writeUTF(slotId);
            out.writeBoolean(allowed);
            player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
            return;
        }
        if ("EQUIP_HAND".equalsIgnoreCase(cmd)) {
            String slotId = in.readUTF();
            org.bukkit.inventory.ItemStack hand = player.getInventory().getItemInMainHand();
            String reason = null;
            boolean success = false;
            if (hand == null || hand.getType() == org.bukkit.Material.AIR) {
                reason = "NO_ITEM";
            } else if (!plugin.getAccessoryManager().isAccessory(hand)) {
                reason = "NOT_ACCESSORY";
            } else if (!plugin.getAccessoryManager().isAllowedInSlot(hand, slotId)) {
                reason = "NOT_ALLOWED";
            } else {
                success = plugin.getAccessoryManager().equip(player, hand, slotId);
                if (success) {
                    // consume one from hand
                    if (hand.getAmount() > 1) {
                        hand.setAmount(hand.getAmount() - 1);
                    } else {
                        player.getInventory().setItemInMainHand(new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR));
                    }
                } else {
                    reason = "EQUIP_FAILED";
                }
            }
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("EQUIP_HAND");
            out.writeUTF(slotId);
            out.writeBoolean(success);
            out.writeBoolean(reason != null);
            if (reason != null) out.writeUTF(reason);
            player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
            return;
        }
        if ("UNEQUIP".equalsIgnoreCase(cmd)) {
            String slotId = in.readUTF();
            java.util.Optional<org.bukkit.inventory.ItemStack> removed = plugin.getAccessoryManager().unequip(player, slotId);
            boolean success = removed.isPresent();
            if (success) {
                org.bukkit.inventory.ItemStack item = removed.get();
                // try to give back to inventory, otherwise drop
                java.util.Map<Integer, org.bukkit.inventory.ItemStack> excess = player.getInventory().addItem(item);
                if (!excess.isEmpty()) {
                    for (org.bukkit.inventory.ItemStack ex : excess.values()) {
                        if (ex != null && ex.getType() != org.bukkit.Material.AIR) {
                            player.getWorld().dropItemNaturally(player.getLocation(), ex);
                        }
                    }
                }
            }
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("UNEQUIP");
            out.writeUTF(slotId);
            out.writeBoolean(success);
            player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
            return;
        }
        if ("TAG_HAND".equalsIgnoreCase(cmd)) {
            // Convenience: tag the item in hand as an accessory (server-side safeguard)
            org.bukkit.inventory.ItemStack hand = player.getInventory().getItemInMainHand();
            boolean success = false;
            if (hand != null && hand.getType() != org.bukkit.Material.AIR) {
                try {
                    plugin.getAccessoryManager().tagAsAccessory(hand);
                    success = true;
                } catch (Throwable ignored) {}
            }
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("TAG_HAND");
            out.writeBoolean(success);
            player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
            return;
        }
        if ("EQUIP_INV".equalsIgnoreCase(cmd)) {
            String slotId = in.readUTF();
            int invIndex = in.readInt();
            org.bukkit.inventory.PlayerInventory inv = player.getInventory();
            boolean success = false;
            String reason = null;
            if (invIndex < 0 || invIndex >= 36) { // limit to main inventory (0..35)
                reason = "BAD_INDEX";
            } else {
                org.bukkit.inventory.ItemStack stack = inv.getItem(invIndex);
                if (stack == null || stack.getType() == org.bukkit.Material.AIR) {
                    reason = "NO_ITEM";
                } else if (!plugin.getAccessoryManager().isAccessory(stack)) {
                    reason = "NOT_ACCESSORY";
                } else if (!plugin.getAccessoryManager().isAllowedInSlot(stack, slotId)) {
                    reason = "NOT_ALLOWED";
                } else {
                    success = plugin.getAccessoryManager().equip(player, stack, slotId);
                    if (success) {
                        if (stack.getAmount() > 1) {
                            stack.setAmount(stack.getAmount() - 1);
                            inv.setItem(invIndex, stack);
                        } else {
                            inv.setItem(invIndex, new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR));
                        }
                    } else {
                        reason = "EQUIP_FAILED";
                    }
                }
            }
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("EQUIP_INV");
            out.writeUTF(slotId);
            out.writeInt(invIndex);
            out.writeBoolean(success);
            out.writeBoolean(reason != null);
            if (reason != null) out.writeUTF(reason);
            player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
            return;
        }
        if ("SWAP".equalsIgnoreCase(cmd)) {
            String slotId = in.readUTF();
            org.bukkit.inventory.PlayerInventory inv = player.getInventory();
            org.bukkit.inventory.ItemStack hand = inv.getItemInMainHand();
            java.util.Map<String, org.bukkit.inventory.ItemStack> eq = plugin.getAccessoryManager().getEquipped(player);
            org.bukkit.inventory.ItemStack current = eq.get(slotId);
            boolean success = false;
            String reason = null;
            // Case 1: hand empty, slot has item -> move from slot to hand
            if ((hand == null || hand.getType() == org.bukkit.Material.AIR)) {
                if (current == null || current.getType() == org.bukkit.Material.AIR) {
                    reason = "NOTHING_TO_SWAP";
                } else {
                    java.util.Optional<org.bukkit.inventory.ItemStack> removed = plugin.getAccessoryManager().unequip(player, slotId);
                    if (removed.isPresent()) {
                        inv.setItemInMainHand(removed.get());
                        success = true;
                    } else {
                        reason = "UNEQUIP_FAILED";
                    }
                }
            } else {
                // Case 2: hand has item -> try equip hand into slot
                if (!plugin.getAccessoryManager().isAccessory(hand)) {
                    reason = "NOT_ACCESSORY";
                } else if (!plugin.getAccessoryManager().isAllowedInSlot(hand, slotId)) {
                    reason = "NOT_ALLOWED";
                } else {
                    // If there's an existing item, unequip first and remember it
                    org.bukkit.inventory.ItemStack prev = null;
                    if (current != null && current.getType() != org.bukkit.Material.AIR) {
                        java.util.Optional<org.bukkit.inventory.ItemStack> removed = plugin.getAccessoryManager().unequip(player, slotId);
                        if (removed.isPresent()) prev = removed.get();
                    }
                    // Equip one from hand
                    success = plugin.getAccessoryManager().equip(player, hand, slotId);
                    if (success) {
                        if (hand.getAmount() > 1) {
                            hand.setAmount(hand.getAmount() - 1);
                            inv.setItemInMainHand(prev == null ? hand : prev);
                        } else {
                            inv.setItemInMainHand(prev == null ? new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR) : prev);
                        }
                    } else {
                        // put back the unequipped item if any
                        if (prev != null) inv.addItem(prev);
                        reason = "EQUIP_FAILED";
                    }
                }
            }
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("SWAP");
            out.writeUTF(slotId);
            out.writeBoolean(success);
            out.writeBoolean(reason != null);
            if (reason != null) out.writeUTF(reason);
            player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
            return;
        }
        if ("CLEAR_SLOT".equalsIgnoreCase(cmd)) {
            String slotId = in.readUTF();
            java.util.Optional<org.bukkit.inventory.ItemStack> removed = plugin.getAccessoryManager().unequip(player, slotId);
            boolean success = removed.isPresent();
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("CLEAR_SLOT");
            out.writeUTF(slotId);
            out.writeBoolean(success);
            player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
            return;
        }
        if ("GET_SLOT_RULES".equalsIgnoreCase(cmd)) {
            java.util.List<site.pwing.pwingcurios.manager.SlotDefinition> defs = plugin.getAccessoryManager().getSlots();
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("GET_SLOT_RULES");
            out.writeInt(defs.size());
            for (site.pwing.pwingcurios.manager.SlotDefinition def : defs) {
                out.writeUTF(def.getId());
                out.writeInt(def.getIndex());
                out.writeUTF(def.getName() == null ? def.getId() : def.getName());
                java.util.List<String> mats = def.getMaterials();
                java.util.List<String> kws = def.getKeywords();
                out.writeInt(mats == null ? 0 : mats.size());
                if (mats != null) for (String m : mats) out.writeUTF(m);
                out.writeInt(kws == null ? 0 : kws.size());
                if (kws != null) for (String k : kws) out.writeUTF(k);
                // Append optional placeholder visualization info
                String phMat = def.getPlaceholderMaterial();
                Integer phCmd = def.getPlaceholderCustomModelData();
                String phName = def.getPlaceholderName();
                boolean hasPh = phMat != null || phCmd != null || phName != null;
                out.writeBoolean(hasPh);
                if (hasPh) {
                    out.writeBoolean(phMat != null);
                    if (phMat != null) out.writeUTF(phMat);
                    out.writeBoolean(phCmd != null);
                    if (phCmd != null) out.writeInt(phCmd);
                    out.writeBoolean(phName != null);
                    if (phName != null) out.writeUTF(phName);
                }
            }
            player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
            return;
        }
    }
}
