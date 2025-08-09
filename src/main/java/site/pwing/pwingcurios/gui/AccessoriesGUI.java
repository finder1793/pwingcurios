package site.pwing.pwingcurios.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import site.pwing.pwingcurios.Pwingcurios;
import site.pwing.pwingcurios.manager.AccessoryManager;
import site.pwing.pwingcurios.manager.SlotDefinition;

public class AccessoriesGUI implements Listener {

    private final AccessoryManager accessoryManager;
    private final Pwingcurios plugin;

    public AccessoriesGUI(Pwingcurios plugin, AccessoryManager accessoryManager) {
        this.plugin = plugin;
        this.accessoryManager = accessoryManager;
    }

    public Inventory open(Player player) {
        FileConfiguration cfg = plugin.getConfig();
        String title = cfg.getString("curios.gui.title", cfg.getString("gui.title", "Curios"));
        int size = cfg.getInt("curios.gui.size", cfg.getInt("gui.size", 27));
        Inventory inv = Bukkit.createInventory(new CuriosHolder(size), size, title);
        drawBackground(inv);
        // Place equipped accessories
        for (SlotDefinition def : accessoryManager.getSlots()) {
            int index = def.getIndex();
            if (index < 0 || index >= inv.getSize()) continue;
            ItemStack equipped = accessoryManager.getEquipped(player).get(def.getId());
            if (equipped != null && equipped.getType() != Material.AIR) {
                inv.setItem(index, equipped.clone());
            } else {
                inv.setItem(index, placeholder(def));
            }
        }
        player.openInventory(inv);
        return inv;
    }

    private void drawBackground(Inventory inv) {
        FileConfiguration cfg = plugin.getConfig();
        String bgMatName = cfg.getString("curios.gui.background.material", cfg.getString("gui.background.material", "GRAY_STAINED_GLASS_PANE"));
        Material bgMat = Material.matchMaterial(bgMatName);
        if (bgMat == null) bgMat = Material.GRAY_STAINED_GLASS_PANE;
        String bgName = cfg.getString("curios.gui.background.name", cfg.getString("gui.background.name", " "));

        ItemStack glass = new ItemStack(bgMat);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(bgName);
            glass.setItemMeta(meta);
        }
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, glass);
        }
        // Clear slot positions so we can show placeholders
        for (SlotDefinition def : accessoryManager.getSlots()) {
            int index = def.getIndex();
            if (index >= 0 && index < inv.getSize()) {
                inv.setItem(index, null);
            }
        }
    }

    private ItemStack placeholder(SlotDefinition def) {
        FileConfiguration cfg = plugin.getConfig();
        String matName = def.getPlaceholderMaterial();
        if (matName == null || matName.isEmpty()) {
            matName = cfg.getString("curios.gui.placeholder.material", cfg.getString("gui.placeholder.material", "LIGHT_GRAY_STAINED_GLASS_PANE"));
        }
        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = Material.LIGHT_GRAY_STAINED_GLASS_PANE;

        String name = def.getPlaceholderName();
        if (name == null) name = cfg.getString("curios.gui.placeholder.name", cfg.getString("gui.placeholder.name", "§7Empty Slot"));

        Integer cmd = def.getPlaceholderCustomModelData();
        if (cmd == null) {
            // Use -1 to mean "unset"
            int globalCmd = cfg.getInt("curios.gui.placeholder.custom-model-data", cfg.getInt("gui.placeholder.custom-model-data", -1));
            if (globalCmd >= 0) cmd = globalCmd;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) meta.setDisplayName(name);
            if (cmd != null) meta.setCustomModelData(cmd);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!(event.getInventory().getHolder() instanceof CuriosHolder)) return;

        Player player = (Player) event.getWhoClicked();
        int slotIndex = event.getRawSlot();

        if (event.getView().getTopInventory().getHolder() instanceof CuriosHolder) {
            event.setCancelled(true);
        }

        // Click in player inventory to equip into first compatible empty slot
        if (slotIndex >= event.getView().getTopInventory().getSize()) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;
            accessoryManager.firstEmptyCompatibleSlot(player, clicked).ifPresent(slotId -> {
                if (accessoryManager.equip(player, clicked, slotId)) {
                    event.getView().getBottomInventory().removeItemAnySlot(new ItemStack(clicked.getType(), 1));
                    open(player);
                }
            });
            return;
        }

        // Click in GUI on an equipped item to unequip back to player inventory
        String slotId = accessoryManager.getIndexToSlotId().get(slotIndex);
        if (slotId != null) {
            ItemStack itemAt = event.getInventory().getItem(slotIndex);
            if (itemAt == null || itemAt.getType() == Material.AIR || itemAt.getType().toString().contains("GLASS_PANE")) {
                return;
            }
            accessoryManager.unequip(player, slotId).ifPresent(unequipped -> {
                player.getInventory().addItem(unequipped);
                open(player);
            });
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        if (!(event.getInventory().getHolder() instanceof CuriosHolder)) return;
        accessoryManager.applyEffects((Player) event.getPlayer());
    }

    private static class CuriosHolder implements InventoryHolder {
        private final int size;
        private CuriosHolder(int size) { this.size = size; }
        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, size);
        }
    }
}
