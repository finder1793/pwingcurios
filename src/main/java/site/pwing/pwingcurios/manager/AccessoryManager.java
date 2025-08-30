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
import java.lang.reflect.Method;

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
    private final Map<UUID, List<NamespacedKey>> mirroredModifierKeys = new HashMap<>();
    // Curio attribute support (MMOItems/Crucible): track transient modifiers applied from Curio items
    private final Map<UUID, List<UUID>> curioModifierIds = new HashMap<>();
    private final Map<UUID, List<NamespacedKey>> curioModifierKeys = new HashMap<>();

    public AccessoryManager(Pwingcurios plugin, site.pwing.pwingcurios.storage.PlayerDataStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.accessoryKey = plugin.getAccessoryKey();
        this.slots = loadSlotsFromConfig();
        this.indexToSlotId = this.slots.stream().collect(Collectors.toMap(SlotDefinition::getIndex, SlotDefinition::getId));
    }

    private static class ModRef {
        final AttributeModifier modifier;
        final UUID uuid;
        final NamespacedKey key;
        ModRef(AttributeModifier modifier, UUID uuid, NamespacedKey key) {
            this.modifier = modifier;
            this.uuid = uuid;
            this.key = key;
        }
    }

    private ModRef createModifierCompat(String baseKey, double amount, AttributeModifier.Operation op) {
        // Prefer modern API: AttributeModifier(NamespacedKey, double, Operation)
        try {
            NamespacedKey key = new NamespacedKey(plugin, baseKey + "-" + UUID.randomUUID());
            try {
                // Try direct constructor (modern API available in 1.21+)
                AttributeModifier mod = new AttributeModifier(key, amount, op);
                return new ModRef(mod, null, key);
            } catch (NoSuchMethodError err) {
                // Fallthrough to reflection in case of shading peculiarities
            }
        } catch (Throwable ignored) {
            // In case NamespacedKey path fails (very old server), fall back below
        }
        // Legacy fallback: UUID-based constructor via reflection to avoid deprecation warnings
        UUID id = UUID.randomUUID();
        try {
            java.lang.reflect.Constructor<AttributeModifier> ctor = AttributeModifier.class.getConstructor(UUID.class, String.class, double.class, AttributeModifier.Operation.class);
            AttributeModifier mod = ctor.newInstance(id, baseKey, amount, op);
            return new ModRef(mod, id, null);
        } catch (Throwable e) {
            // Could not construct legacy modifier; return empty ref to skip safely
            return new ModRef(null, null, null);
        }
    }

    private Collection<AttributeInstance> getAllAttributeInstances(Player player) {
        // Prefer modern API: player.getAttributes()
        try {
            Method m = player.getClass().getMethod("getAttributes");
            Object res = m.invoke(player);
            if (res instanceof Collection) {
                @SuppressWarnings("unchecked")
                Collection<AttributeInstance> col = (Collection<AttributeInstance>) res;
                return col;
            }
        } catch (Throwable ignored) {}
        // Fallback: iterate over known attributes without calling deprecated Attribute.values() directly
        List<AttributeInstance> list = new ArrayList<>();
        try {
            // Use reflection to avoid compile-time deprecation warnings
            Method values = Attribute.class.getDeclaredMethod("values");
            Object arr = values.invoke(null);
            if (arr instanceof Object[]) {
                for (Object o : (Object[]) arr) {
                    if (o instanceof Attribute) {
                        AttributeInstance inst = player.getAttribute((Attribute) o);
                        if (inst != null) list.add(inst);
                    }
                }
            }
        } catch (Throwable ignored) {}
        return list;
    }

    private boolean removeByKey(AttributeInstance inst, NamespacedKey key) {
        if (key == null) return false;
        try {
            // Modern API
            Method m = inst.getClass().getMethod("removeModifier", NamespacedKey.class);
            m.invoke(inst, key);
            return true;
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean removeByUUID(AttributeInstance inst, UUID id) {
        if (id == null) return false;
        try {
            // Legacy API via reflection (avoid deprecation warning)
            Method m = inst.getClass().getMethod("removeModifier", UUID.class);
            m.invoke(inst, id);
            return true;
        } catch (Throwable ignored) {}
        return false;
    }

    public List<SlotDefinition> getSlots() {
        return slots;
    }

    /**
     * Check if the given item is allowed in the specified slot (by id), without mutating state.
     */
    public boolean isAllowedInSlot(ItemStack item, String slotId) {
        if (item == null) return false;
        SlotDefinition def = slots.stream().filter(s -> s.getId().equals(slotId)).findFirst().orElse(null);
        if (def == null) return false;
        return isItemAllowedInSlot(item, def);
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
        UUID pu = player.getUniqueId();
        List<UUID> ids = mirroredModifierIds.remove(pu);
        List<NamespacedKey> keys = mirroredModifierKeys.remove(pu);
        if ((ids == null || ids.isEmpty()) && (keys == null || keys.isEmpty())) return;
        for (AttributeInstance inst : getAllAttributeInstances(player)) {
            if (inst == null) continue;
            if (keys != null) for (NamespacedKey key : new ArrayList<>(keys)) removeByKey(inst, key);
            if (ids != null) for (UUID id : new ArrayList<>(ids)) removeByUUID(inst, id);
        }
    }

    private void mirrorChestplateStats(Player player, ItemStack chest) {
        clearMirroredModifiers(player);
        if (chest == null) return;
        ItemMeta meta = chest.getItemMeta();
        if (meta == null) return;
        if (meta.getAttributeModifiers(EquipmentSlot.CHEST) == null) return;
        List<UUID> ids = new ArrayList<>();
        List<NamespacedKey> keys = new ArrayList<>();
        meta.getAttributeModifiers(EquipmentSlot.CHEST).forEach((attribute, modifier) -> {
            AttributeInstance inst = player.getAttribute(attribute);
            if (inst == null) return;
            ModRef mr = createModifierCompat("curios_mirror", modifier.getAmount(), modifier.getOperation());
            inst.addModifier(mr.modifier);
            if (mr.key != null) keys.add(mr.key);
            if (mr.uuid != null) ids.add(mr.uuid);
        });
        if (!ids.isEmpty()) mirroredModifierIds.put(player.getUniqueId(), ids);
        if (!keys.isEmpty()) mirroredModifierKeys.put(player.getUniqueId(), keys);
    }

    private void clearCurioAttributeModifiers(Player player) {
        UUID pu = player.getUniqueId();
        List<UUID> ids = curioModifierIds.remove(pu);
        List<NamespacedKey> keys = curioModifierKeys.remove(pu);
        if ((ids == null || ids.isEmpty()) && (keys == null || keys.isEmpty())) return;
        for (AttributeInstance inst : getAllAttributeInstances(player)) {
            if (inst == null) continue;
            if (keys != null) for (NamespacedKey key : new ArrayList<>(keys)) removeByKey(inst, key);
            if (ids != null) for (UUID id : new ArrayList<>(ids)) removeByUUID(inst, id);
        }
    }

    private void applyCurioAttributesFromEquipped(Player player) {
        clearCurioAttributeModifiers(player);
        Map<String, ItemStack> map = getEquipped(player);
        if (map == null || map.isEmpty()) return;
        // Only apply if MMOItems or Crucible is detected
        boolean enabled = false;
        try {
            site.pwing.pwingcurios.integration.IntegrationManager im = plugin.getIntegrationManager();
            if (im != null && (im.isHookActive("MMOItems") || im.isHookActive("Crucible"))) {
                enabled = true;
            }
        } catch (Throwable ignored) {}
        if (!enabled) return;
        List<UUID> appliedIds = new ArrayList<>();
        List<NamespacedKey> appliedKeys = new ArrayList<>();
        for (ItemStack item : map.values()) {
            if (item == null) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;
            try {
                if (meta.getAttributeModifiers() != null) {
                    meta.getAttributeModifiers().forEach((attribute, modifier) -> {
                        AttributeInstance inst = player.getAttribute(attribute);
                        if (inst == null) return;
                        ModRef mr = createModifierCompat("curios_attr", modifier.getAmount(), modifier.getOperation());
                        if (mr.modifier == null) return;
                        inst.addModifier(mr.modifier);
                        if (mr.key != null) appliedKeys.add(mr.key);
                        if (mr.uuid != null) appliedIds.add(mr.uuid);
                    });
                }
            } catch (Throwable ignored) {}
        }
        if (!appliedIds.isEmpty()) curioModifierIds.put(player.getUniqueId(), appliedIds);
        if (!appliedKeys.isEmpty()) curioModifierKeys.put(player.getUniqueId(), appliedKeys);
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
        // Signal external integrations to clear previously applied stats first
        try {
            site.pwing.pwingcurios.api.event.CurioExternalStatsClearEvent clearEvt = new site.pwing.pwingcurios.api.event.CurioExternalStatsClearEvent(player);
            org.bukkit.Bukkit.getPluginManager().callEvent(clearEvt);
            if (clearEvt.isCancelled()) {
                // If cancelled, we still proceed with our internal effects, but integrations may choose to keep their stats.
            }
        } catch (Throwable ignored) {}

        // Built-in example effect management
        player.removePotionEffect(PotionEffectType.SPEED);
        Map<String, ItemStack> map = getEquipped(player);
        if (map == null) map = java.util.Collections.emptyMap();
        boolean hasSwift = map.values().stream().filter(Objects::nonNull).anyMatch(i -> {
            ItemMeta meta = i.getItemMeta();
            return meta != null && meta.hasDisplayName() && meta.getDisplayName().toLowerCase(java.util.Locale.ROOT).contains("swift");
        });
        if (hasSwift) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 60 * 60, 0, true, false, true));
        }

        // Apply vanilla AttributeModifiers from Curio items (MMOItems/Crucible compatible embeddings)
        applyCurioAttributesFromEquipped(player);

        // Let external plugins (MMOItems, Crucible, etc.) apply their own stat systems based on equipped items
        try {
            site.pwing.pwingcurios.api.event.CurioExternalStatsApplyEvent applyEvt = new site.pwing.pwingcurios.api.event.CurioExternalStatsApplyEvent(player, map);
            org.bukkit.Bukkit.getPluginManager().callEvent(applyEvt);
        } catch (Throwable ignored) {}

        // Finally, invoke our internal direct integrations if their plugins are present
        try {
            site.pwing.pwingcurios.integration.IntegrationManager im = plugin.getIntegrationManager();
            if (im != null) {
                im.clearDirect(player);
                im.applyDirect(player, map);
            }
        } catch (Throwable ignored) {}
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

    public void cleanupPlayer(Player player) {
        clearCurioAttributeModifiers(player);
        clearMirroredModifiers(player);
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
