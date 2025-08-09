package site.pwing.pwingcurios.manager;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import site.pwing.pwingcurios.Pwingcurios;

import java.util.*;
import java.util.stream.Collectors;

public class AccessoryManager {
    private final Pwingcurios plugin;
    private final NamespacedKey accessoryKey;

    // Dynamic slot definitions loaded from config
    private final List<SlotDefinition> slots;
    private final Map<Integer, String> indexToSlotId;

    // Runtime equipped accessories per player (slotId -> item)
    private final Map<UUID, Map<String, ItemStack>> equipped = new HashMap<>();

    // Storage backend
    private final site.pwing.pwingcurios.storage.PlayerDataStorage storage;

    // Elytra support: preserve chestplate and mirrored attribute modifier UUIDs
    private final Map<UUID, ItemStack> preservedChestplate = new HashMap<>();
    private final Map<UUID, List<UUID>> mirroredModifierIds = new HashMap<>();

    public AccessoryManager(Pwingcurios plugin, site.pwing.pwingcurios.storage.PlayerDataStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.accessoryKey = plugin.getAccessoryKey();
        this.slots = loadSlotsFromConfig();
        this.indexToSlotId = this.slots.stream().collect(Collectors.toMap(SlotDefinition::getIndex, SlotDefinition::getId));
    }

    public List<SlotDefinition> getSlots() {
        return slots;
    }

    public boolean isAccessory(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        // Identify via persistent data or lore keyword fallback
        boolean tagged = meta.getPersistentDataContainer().has(accessoryKey, org.bukkit.persistence.PersistentDataType.BYTE);
        if (tagged) return true;
        if (meta.hasLore()) {
            for (String line : meta.getLore()) {
                if (line != null && line.toLowerCase(Locale.ROOT).contains("accessory")) return true;
            }
        }
        return false;
    }

    public void tagAsAccessory(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(accessoryKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        List<String> lore = meta.hasLore() ? new ArrayList<>(Objects.requireNonNull(meta.getLore())) : new ArrayList<>();
        lore.add("§7Accessory");
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private List<SlotDefinition> loadSlotsFromConfig() {
        List<SlotDefinition> list = new ArrayList<>();
        // New preferred format: curios.slots: - { id, index, name, keywords: [..], placeholder: {...} }
        List<Map<?, ?>> raw = plugin.getConfig().getMapList("curios.slots");
        if (raw == null || raw.isEmpty()) {
            // Fallback to old key: curio-slots
            raw = plugin.getConfig().getMapList("curio-slots");
        }
        if (raw != null && !raw.isEmpty()) {
            int i = 0;
            for (Map<?, ?> m : raw) {
                Object idObj = m.get("id");
                String id = idObj == null ? ("slot_" + i) : String.valueOf(idObj);
                Object idxObj = m.get("index");
                int index = 10;
                if (idxObj != null) {
                    try { index = Integer.parseInt(String.valueOf(idxObj)); } catch (NumberFormatException ignored) {}
                }
                Object nameObj = m.get("name");
                String name = nameObj == null ? id : String.valueOf(nameObj);
                Object kwObj = m.get("keywords");
                List<String> kws = new ArrayList<>();
                if (kwObj instanceof List) {
                    for (Object o : (List<?>) kwObj) if (o != null) kws.add(String.valueOf(o));
                } else if (kwObj instanceof String) {
                    kws.add((String) kwObj);
                }
                // optional materials block
                Object matObj = m.get("materials");
                List<String> mats = new ArrayList<>();
                if (matObj instanceof List) {
                    for (Object o : (List<?>) matObj) if (o != null) mats.add(String.valueOf(o));
                } else if (matObj instanceof String) {
                    mats.add((String) matObj);
                }
                // optional placeholder block
                String phMat = null;
                String phName = null;
                Integer phCmd = null;
                Object phObj = m.get("placeholder");
                if (phObj instanceof Map) {
                    Map<?, ?> ph = (Map<?, ?>) phObj;
                    Object mMat = ph.get("material");
                    if (mMat != null) phMat = String.valueOf(mMat);
                    Object mName = ph.get("name");
                    if (mName != null) phName = String.valueOf(mName);
                    Object mCmd = ph.get("custom-model-data");
                    if (mCmd != null) {
                        try { phCmd = Integer.parseInt(String.valueOf(mCmd)); } catch (NumberFormatException ignored) {}
                    }
                }
                list.add(new SlotDefinition(id, index, name, kws, mats, phMat, phName, phCmd));
                i++;
            }
            return list;
        }
        // Backward-compat default 4 slots from gui.slots.slot1..slot4
        int s1 = plugin.getConfig().getInt("gui.slots.slot1", 10);
        int s2 = plugin.getConfig().getInt("gui.slots.slot2", 12);
        int s3 = plugin.getConfig().getInt("gui.slots.slot3", 14);
        int s4 = plugin.getConfig().getInt("gui.slots.slot4", 16);
        list.add(new SlotDefinition("slot1", s1, "Slot 1", Collections.emptyList()));
        list.add(new SlotDefinition("slot2", s2, "Slot 2", Collections.emptyList()));
        list.add(new SlotDefinition("slot3", s3, "Slot 3", Collections.emptyList()));
        list.add(new SlotDefinition("slot4", s4, "Slot 4", Collections.emptyList()));
        return list;
    }

    public Map<String, ItemStack> getEquipped(Player player) {
        return equipped.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
    }

    public Optional<String> firstEmptyCompatibleSlot(Player player, ItemStack item) {
        for (SlotDefinition def : slots) {
            if (!getEquipped(player).containsKey(def.getId()) || getEquipped(player).get(def.getId()) == null) {
                if (isItemAllowedInSlot(item, def)) return Optional.of(def.getId());
            }
        }
        return Optional.empty();
    }

    private boolean isItemAllowedInSlot(ItemStack item, SlotDefinition def) {
        // First, check explicit material whitelist if present
        List<String> mats = def.getMaterials();
        if (mats != null && !mats.isEmpty()) {
            String matName = item.getType().name();
            for (String m : mats) {
                if (m != null && matName.equalsIgnoreCase(m)) return true;
            }
        }
        // Next, check lore keywords if present
        List<String> kws = def.getKeywords();
        if (kws == null || kws.isEmpty()) {
            // No materials and no keywords specified => allow any item
            return mats == null || mats.isEmpty();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return false;
        List<String> lore = meta.getLore();
        if (lore == null) return false;
        List<String> lowered = new ArrayList<>();
        for (String line : lore) {
            if (line == null) continue;
            String ll = line.toLowerCase(Locale.ROOT);
            lowered.add(ll);
        }
        for (String kw : kws) {
            if (kw == null || kw.isEmpty()) continue;
            String target = kw.toLowerCase(Locale.ROOT);
            for (String line : lowered) {
                if (line.contains(target)) return true;
            }
        }
        return false;
    }

    private boolean isElytra(ItemStack item) {
        return item != null && item.getType() == Material.ELYTRA;
    }

    private void clearMirroredModifiers(Player player) {
        List<UUID> ids = mirroredModifierIds.remove(player.getUniqueId());
        if (ids == null) return;
        for (Attribute attr : Attribute.values()) {
            AttributeInstance inst = player.getAttribute(attr);
            if (inst == null) continue;
            for (UUID id : new ArrayList<>(ids)) {
                try { inst.removeModifier(id); } catch (Exception ignored) {}
            }
        }
    }

    private void mirrorChestplateStats(Player player, ItemStack chest) {
        clearMirroredModifiers(player);
        if (chest == null) return;
        ItemMeta meta = chest.getItemMeta();
        if (meta == null) return;
        if (meta.getAttributeModifiers(EquipmentSlot.CHEST) == null) return;
        List<UUID> ids = new ArrayList<>();
        meta.getAttributeModifiers(EquipmentSlot.CHEST).forEach((attribute, modifier) -> {
            AttributeInstance inst = player.getAttribute(attribute);
            if (inst == null) return;
            AttributeModifier mirror = new AttributeModifier(UUID.randomUUID(), "curios_mirror", modifier.getAmount(), modifier.getOperation());
            inst.addModifier(mirror);
            ids.add(mirror.getUniqueId());
        });
        mirroredModifierIds.put(player.getUniqueId(), ids);
    }

    public boolean equip(Player player, ItemStack item, String slotId) {
        SlotDefinition def = slots.stream().filter(s -> s.getId().equals(slotId)).findFirst().orElse(null);
        if (def == null) return false;
        if (!isItemAllowedInSlot(item, def)) return false;
        // Fire pre-equip event
        site.pwing.pwingcurios.api.event.CurioEquipEvent pre = new site.pwing.pwingcurios.api.event.CurioEquipEvent(player, slotId, item);
        org.bukkit.Bukkit.getPluginManager().callEvent(pre);
        if (pre.isCancelled()) return false;
        getEquipped(player).put(slotId, item.clone());

        if (isElytra(item)) {
            ItemStack currentChest = player.getInventory().getChestplate();
            if (currentChest != null && currentChest.getType() != Material.AIR && currentChest.getType() != Material.ELYTRA) {
                preservedChestplate.put(player.getUniqueId(), currentChest.clone());
                mirrorChestplateStats(player, currentChest);
            }
            player.getInventory().setChestplate(item.clone());
        }

        applyEffects(player);
        savePlayer(player.getUniqueId());
        return true;
    }

    public Optional<ItemStack> unequip(Player player, String slotId) {
        ItemStack current = getEquipped(player).get(slotId);
        // Fire pre-unequip event
        site.pwing.pwingcurios.api.event.CurioUnequipEvent pre = new site.pwing.pwingcurios.api.event.CurioUnequipEvent(player, slotId, current);
        org.bukkit.Bukkit.getPluginManager().callEvent(pre);
        if (pre.isCancelled()) return java.util.Optional.empty();
        ItemStack removed = getEquipped(player).remove(slotId);

        if (isElytra(removed)) {
            ItemStack stored = preservedChestplate.remove(player.getUniqueId());
            clearMirroredModifiers(player);
            ItemStack armorChest = player.getInventory().getChestplate();
            if (stored != null) {
                if (armorChest != null && armorChest.getType() == Material.ELYTRA) {
                    player.getInventory().setChestplate(stored);
                } else {
                    player.getInventory().addItem(stored);
                }
            }
        }

        applyEffects(player);
        savePlayer(player.getUniqueId());
        return Optional.ofNullable(removed);
    }

    public void clearAll() {
        equipped.clear();
    }

    public void applyEffects(Player player) {
        player.removePotionEffect(PotionEffectType.SPEED);
        Map<String, ItemStack> map = getEquipped(player);
        if (map == null) return;
        boolean hasSwift = map.values().stream().filter(Objects::nonNull).anyMatch(i -> {
            ItemMeta meta = i.getItemMeta();
            return meta != null && meta.hasDisplayName() && meta.getDisplayName().toLowerCase(Locale.ROOT).contains("swift");
        });
        if (hasSwift) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 60 * 60, 0, true, false, true));
        }
    }

    public ItemStack createTestAccessorySwift() {
        ItemStack item = new ItemStack(Material.GOLDEN_CARROT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§bSwift Charm");
            List<String> lore = new ArrayList<>();
            lore.add("§7Accessory");
            lore.add("§7Grants §fSpeed I §7while equipped.");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        tagAsAccessory(item);
        return item;
    }

    public void savePlayer(UUID uuid) {
        Map<String, ItemStack> map = equipped.get(uuid);
        try {
            storage.savePlayer(uuid, map == null ? new HashMap<>() : map);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save player " + uuid + ": " + e.getMessage());
        }
    }

    public void loadPlayer(UUID uuid) {
        try {
            Map<String, ItemStack> loaded = storage.loadPlayer(uuid);
            Map<String, ItemStack> filtered = new HashMap<>();
            Set<String> validIds = slots.stream().map(SlotDefinition::getId).collect(Collectors.toSet());
            // Migrate legacy enum-based keys if present
            if (loaded != null) {
                for (Map.Entry<String, ItemStack> e : loaded.entrySet()) {
                    String key = e.getKey();
                    if (validIds.contains(key)) {
                        filtered.put(key, e.getValue());
                    } else if (key.startsWith("SLOT_")) {
                        // Map SLOT_1.. to positional slot if exists
                        try {
                            int idx = Integer.parseInt(key.substring(5));
                            if (idx >= 1 && idx <= slots.size()) {
                                filtered.put(slots.get(idx - 1).getId(), e.getValue());
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            equipped.put(uuid, filtered);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load player " + uuid + ": " + e.getMessage());
            equipped.remove(uuid);
        }
    }

    public void saveAll() {
        try {
            storage.saveAll(equipped);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save all players: " + e.getMessage());
        }
    }

    public void ensureElytraState(Player player) {
        Map<String, ItemStack> map = getEquipped(player);
        boolean hasElytraCurio = map.values().stream().filter(Objects::nonNull).anyMatch(this::isElytra);
        if (!hasElytraCurio) {
            clearMirroredModifiers(player);
            preservedChestplate.remove(player.getUniqueId());
            return;
        }
        ItemStack currentChest = player.getInventory().getChestplate();
        if (currentChest == null || currentChest.getType() != Material.ELYTRA) {
            if (currentChest != null && currentChest.getType() != Material.AIR) {
                preservedChestplate.put(player.getUniqueId(), currentChest.clone());
                mirrorChestplateStats(player, currentChest);
            }
            ItemStack anyElytra = map.values().stream().filter(Objects::nonNull).filter(this::isElytra).findFirst().orElse(new ItemStack(Material.ELYTRA));
            player.getInventory().setChestplate(anyElytra.clone());
        } else {
            ItemStack preserved = preservedChestplate.get(player.getUniqueId());
            if (preserved != null) {
                mirrorChestplateStats(player, preserved);
            }
        }
    }

    public Map<Integer, String> getIndexToSlotId() {
        return indexToSlotId;
    }
}
