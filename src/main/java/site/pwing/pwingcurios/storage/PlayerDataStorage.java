package site.pwing.pwingcurios.storage;

import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

public interface PlayerDataStorage {
    void init() throws Exception;
    void close();

    void savePlayer(UUID uuid, Map<String, ItemStack> map) throws Exception;
    Map<String, ItemStack> loadPlayer(UUID uuid) throws Exception;

    void saveAll(Map<UUID, Map<String, ItemStack>> all) throws Exception;
}
