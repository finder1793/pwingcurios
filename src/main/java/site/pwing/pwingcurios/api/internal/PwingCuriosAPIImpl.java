package site.pwing.pwingcurios.api.internal;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import site.pwing.pwingcurios.api.PwingCuriosAPI;
import site.pwing.pwingcurios.api.event.CurioEquipEvent;
import site.pwing.pwingcurios.api.event.CurioUnequipEvent;
import site.pwing.pwingcurios.gui.AccessoriesGUI;
import site.pwing.pwingcurios.manager.AccessoryManager;
import site.pwing.pwingcurios.manager.SlotDefinition;

import java.util.*;

public class PwingCuriosAPIImpl implements PwingCuriosAPI {
    private final AccessoryManager manager;
    private final AccessoriesGUI gui;

    public PwingCuriosAPIImpl(AccessoryManager manager, AccessoriesGUI gui) {
        this.manager = manager;
        this.gui = gui;
    }

    @Override
    public List<SlotDefinition> getSlots() {
        return Collections.unmodifiableList(manager.getSlots());
    }

    @Override
    public Map<String, ItemStack> getEquipped(Player player) {
        return Collections.unmodifiableMap(new HashMap<>(manager.getEquipped(player)));
    }

    @Override
    public Map<String, ItemStack> getEquipped(UUID playerId) {
        Player p = Bukkit.getPlayer(playerId);
        if (p == null) return Collections.emptyMap();
        return getEquipped(p);
    }

    @Override
    public boolean equip(Player player, ItemStack item, String slotId) {
        CurioEquipEvent pre = new CurioEquipEvent(player, slotId, item);
        Bukkit.getPluginManager().callEvent(pre);
        if (pre.isCancelled()) return false;
        return manager.equip(player, item, slotId);
    }

    @Override
    public Optional<ItemStack> unequip(Player player, String slotId) {
        ItemStack current = manager.getEquipped(player).get(slotId);
        CurioUnequipEvent pre = new CurioUnequipEvent(player, slotId, current);
        Bukkit.getPluginManager().callEvent(pre);
        if (pre.isCancelled()) return Optional.empty();
        return manager.unequip(player, slotId);
    }

    @Override
    public Optional<String> firstEmptyCompatibleSlot(Player player, ItemStack item) {
        return manager.firstEmptyCompatibleSlot(player, item);
    }

    @Override
    public void openGui(Player player) {
        gui.open(player);
    }
}
