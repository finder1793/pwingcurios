package site.pwing.pwingcurios.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Fired after Curios effects are applied, allowing external plugins (e.g., MMOItems, Crucible)
 * to apply their own stat systems (critical chance, mana, skill damage, etc.) based on
 * equipped Curio items. This event is cancellable: cancel to prevent external stats application.
 */
public class CurioExternalStatsApplyEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Map<String, ItemStack> equipped;
    private boolean cancelled;

    public CurioExternalStatsApplyEvent(Player player, Map<String, ItemStack> equipped) {
        this.player = player;
        this.equipped = Collections.unmodifiableMap(new HashMap<>(equipped == null ? new HashMap<>() : equipped));
    }

    public Player getPlayer() {
        return player;
    }

    /**
     * @return immutable snapshot of equipped Curio items (slotId -> item).
     */
    public Map<String, ItemStack> getEquipped() {
        return equipped;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
