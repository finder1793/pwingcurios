package site.pwing.pwingcurios.manager;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import site.pwing.pwingcurios.Pwingcurios;

public class PlayerDataListener implements Listener {
    private final Pwingcurios plugin;
    private final AccessoryManager accessoryManager;

    public PlayerDataListener(Pwingcurios plugin, AccessoryManager accessoryManager) {
        this.plugin = plugin;
        this.accessoryManager = accessoryManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        accessoryManager.loadPlayer(event.getPlayer().getUniqueId());
        accessoryManager.ensureElytraState(event.getPlayer());
        accessoryManager.applyEffects(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        accessoryManager.savePlayer(event.getPlayer().getUniqueId());
        // Notify external integrations to clear their stats for this player
        try {
            site.pwing.pwingcurios.api.event.CurioExternalStatsClearEvent clearEvt = new site.pwing.pwingcurios.api.event.CurioExternalStatsClearEvent(event.getPlayer());
            org.bukkit.Bukkit.getPluginManager().callEvent(clearEvt);
        } catch (Throwable ignored) {}
        accessoryManager.cleanupPlayer(event.getPlayer());
    }
}
