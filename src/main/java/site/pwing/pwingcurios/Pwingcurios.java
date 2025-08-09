package site.pwing.pwingcurios;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import site.pwing.pwingcurios.command.CuriosCommand;
import site.pwing.pwingcurios.gui.AccessoriesGUI;
import site.pwing.pwingcurios.integration.IntegrationManager;
import site.pwing.pwingcurios.manager.AccessoryManager;

public final class Pwingcurios extends JavaPlugin {
    private static Pwingcurios instance;

    private AccessoryManager accessoryManager;
    private AccessoriesGUI accessoriesGUI;
    private IntegrationManager integrationManager;
    private site.pwing.pwingcurios.storage.PlayerDataStorage storage;

    private NamespacedKey accessoryKey; // used to tag accessory items

    public static Pwingcurios getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        accessoryKey = new NamespacedKey(this, "accessory");

        // Storage backend
        String type = getConfig().getString("storage.type", "YAML").toUpperCase();
        try {
            if ("SQLITE".equals(type) || "SQL".equals(type)) {
                java.io.File db = new java.io.File(getDataFolder(), getConfig().getString("storage.sqlite.file", "data.db"));
                storage = new site.pwing.pwingcurios.storage.SqlPlayerDataStorage(this, db);
            } else {
                storage = new site.pwing.pwingcurios.storage.YamlPlayerDataStorage(this);
            }
            storage.init();
        } catch (Exception e) {
            getLogger().warning("Failed to initialize storage, falling back to YAML: " + e.getMessage());
            storage = new site.pwing.pwingcurios.storage.YamlPlayerDataStorage(this);
            try { storage.init(); } catch (Exception ex) { getLogger().severe("Failed to init fallback YAML storage: " + ex.getMessage()); }
        }

        // Managers
        accessoryManager = new AccessoryManager(this, storage);
        accessoriesGUI = new AccessoriesGUI(this, accessoryManager);
        integrationManager = new IntegrationManager(this);

        // Register public API as a Bukkit service
        org.bukkit.plugin.ServicePriority pri = org.bukkit.plugin.ServicePriority.Normal;
        org.bukkit.Bukkit.getServicesManager().register(site.pwing.pwingcurios.api.PwingCuriosAPI.class,
                new site.pwing.pwingcurios.api.internal.PwingCuriosAPIImpl(accessoryManager, accessoriesGUI), this, pri);

        // Detect optional integrations
        integrationManager.detect();

        // Register command
        if (getCommand("curios") != null) {
            getCommand("curios").setExecutor(new CuriosCommand(this, accessoryManager, accessoriesGUI));
        } else {
            getLogger().warning("Command 'curios' is not defined in plugin.yml");
        }

        // Register listeners (GUI click handling lives inside AccessoriesGUI)
        Bukkit.getPluginManager().registerEvents(accessoriesGUI, this);
        Bukkit.getPluginManager().registerEvents(new site.pwing.pwingcurios.manager.PlayerDataListener(this, accessoryManager), this);

        // Mod API (plugin messaging channel)
        this.getServer().getMessenger().registerIncomingPluginChannel(this, site.pwing.pwingcurios.mod.ModApiMessenger.CHANNEL,
                new site.pwing.pwingcurios.mod.ModApiMessenger(this));
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, site.pwing.pwingcurios.mod.ModApiMessenger.CHANNEL);

        getLogger().info("PwingCurios enabled. Optional hooks: " + integrationManager.getActiveHooksSummary());
    }

    @Override
    public void onDisable() {
        // Clean up any runtime state
        if (accessoryManager != null) {
            accessoryManager.saveAll();
            accessoryManager.clearAll();
        }
        if (storage != null) {
            storage.close();
        }
        // Unregister services and channels
        try {
            org.bukkit.Bukkit.getServicesManager().unregisterAll(this);
        } catch (Exception ignored) {}
        try {
            this.getServer().getMessenger().unregisterIncomingPluginChannel(this, site.pwing.pwingcurios.mod.ModApiMessenger.CHANNEL);
            this.getServer().getMessenger().unregisterOutgoingPluginChannel(this, site.pwing.pwingcurios.mod.ModApiMessenger.CHANNEL);
        } catch (Exception ignored) {}
    }

    public AccessoryManager getAccessoryManager() {
        return accessoryManager;
    }

    public AccessoriesGUI getAccessoriesGUI() {
        return accessoriesGUI;
    }

    public IntegrationManager getIntegrationManager() {
        return integrationManager;
    }

    public NamespacedKey getAccessoryKey() {
        return accessoryKey;
    }
}
