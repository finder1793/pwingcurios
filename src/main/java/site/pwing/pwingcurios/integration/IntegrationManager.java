package site.pwing.pwingcurios.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.inventory.ItemStack;
import site.pwing.pwingcurios.Pwingcurios;
import site.pwing.pwingcurios.integration.direct.CrucibleDirectIntegration;
import site.pwing.pwingcurios.integration.direct.DirectIntegration;
import site.pwing.pwingcurios.integration.direct.MmoItemsDirectIntegration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class IntegrationManager {
    private final Pwingcurios plugin;
    private final List<String> activeHooks = new ArrayList<>();
    private final List<DirectIntegration> directIntegrations = new ArrayList<>();

    public IntegrationManager(Pwingcurios plugin) {
        this.plugin = plugin;
    }

    public void detect() {
        check("MMOItems");
        check("Crucible");
        check("Nexo");
    }

    private void check(String name) {
        Plugin p = Bukkit.getPluginManager().getPlugin(name);
        if (p != null && p.isEnabled()) {
            activeHooks.add(name);
            // Register internal direct integration if we have one
            if ("MMOItems".equalsIgnoreCase(name)) {
                directIntegrations.add(new MmoItemsDirectIntegration());
            } else if ("Crucible".equalsIgnoreCase(name)) {
                directIntegrations.add(new CrucibleDirectIntegration());
            }
            plugin.getLogger().info("Hooked into " + name + " (detected)");
        } else {
            plugin.getLogger().info(name + " not detected");
        }
    }

    public boolean isHookActive(String name) {
        return activeHooks.contains(name);
    }

    public String getActiveHooksSummary() {
        if (activeHooks.isEmpty()) return "none";
        return activeHooks.stream().collect(Collectors.joining(", "));
    }

    public void clearDirect(Player player) {
        for (DirectIntegration di : directIntegrations) {
            try { di.clear(player); } catch (Throwable ignored) {}
        }
    }

    public void applyDirect(Player player, Map<String, ItemStack> equipped) {
        for (DirectIntegration di : directIntegrations) {
            try { di.apply(player, equipped); } catch (Throwable ignored) {}
        }
    }
}
