package site.pwing.pwingcurios.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import site.pwing.pwingcurios.Pwingcurios;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class YamlPlayerDataStorage implements PlayerDataStorage {
    private final Pwingcurios plugin;
    private File dataFile;
    private YamlConfiguration dataCfg;

    public YamlPlayerDataStorage(Pwingcurios plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() throws Exception {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        dataCfg = YamlConfiguration.loadConfiguration(dataFile);
    }

    @Override
    public void close() { /* no-op */ }

    private void saveFile() throws IOException {
        dataCfg.save(dataFile);
    }

    @Override
    public void savePlayer(UUID uuid, Map<String, ItemStack> map) throws Exception {
        String base = "players." + uuid;
        if (map == null || map.isEmpty()) {
            dataCfg.set(base, null);
            saveFile();
            return;
        }
        for (Map.Entry<String, ItemStack> e : map.entrySet()) {
            dataCfg.set(base + "." + e.getKey(), e.getValue());
        }
        saveFile();
    }

    @Override
    public Map<String, ItemStack> loadPlayer(UUID uuid) throws Exception {
        String base = "players." + uuid;
        Map<String, ItemStack> map = new HashMap<>();
        if (dataCfg.getConfigurationSection(base) == null) {
            return map;
        }
        Set<String> keys = dataCfg.getConfigurationSection(base).getKeys(false);
        for (String key : keys) {
            ItemStack it = dataCfg.getItemStack(base + "." + key);
            if (it != null) {
                map.put(key, it);
            }
        }
        return map;
    }

    @Override
    public void saveAll(Map<UUID, Map<String, ItemStack>> all) throws Exception {
        if (all == null) return;
        for (Map.Entry<UUID, Map<String, ItemStack>> e : all.entrySet()) {
            savePlayer(e.getKey(), e.getValue());
        }
    }
}
