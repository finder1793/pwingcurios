package site.pwing.pwingcurios.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import site.pwing.pwingcurios.manager.SlotDefinition;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API for other plugins to integrate with PwingCurios.
 * Obtain via Bukkit Services:
 * Bukkit.getServicesManager().load(PwingCuriosAPI.class)
 */
public interface PwingCuriosAPI {
    /**
     * @return immutable snapshot of current slot definitions.
     */
    List<SlotDefinition> getSlots();

    /**
     * @return map of slotId -> equipped item for the given player (may be empty).
     */
    Map<String, ItemStack> getEquipped(Player player);

    Map<String, ItemStack> getEquipped(UUID playerId);

    /**
     * Attempts to equip an item into the specified slot.
     * Fires CurioEquipEvent (cancellable) before applying.
     * @return true if equipped
     */
    boolean equip(Player player, ItemStack item, String slotId);

    /**
     * Attempts to unequip the item at the specified slot.
     * Fires CurioUnequipEvent (cancellable) before removing.
     */
    Optional<ItemStack> unequip(Player player, String slotId);

    /**
     * Finds the first empty compatible slot according to configured criteria.
     */
    Optional<String> firstEmptyCompatibleSlot(Player player, ItemStack item);

    /**
     * Opens the Curios GUI for the player.
     */
    void openGui(Player player);
}
