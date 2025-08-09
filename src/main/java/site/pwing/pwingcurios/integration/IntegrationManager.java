package site.pwing.pwingcurios.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import site.pwing.pwingcurios.Pwingcurios;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class IntegrationManager {
    private final Pwingcurios plugin;
    private final List<String> activeHooks = new ArrayList<>();

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
}
