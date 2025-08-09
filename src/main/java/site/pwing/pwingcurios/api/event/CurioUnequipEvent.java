package site.pwing.pwingcurios.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

public class CurioUnequipEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String slotId;
    private final ItemStack item;
    private boolean cancelled;

    public CurioUnequipEvent(Player player, String slotId, ItemStack item) {
        this.player = player;
        this.slotId = slotId;
        this.item = item;
    }

    public Player getPlayer() { return player; }
    public String getSlotId() { return slotId; }
    public ItemStack getItem() { return item; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
