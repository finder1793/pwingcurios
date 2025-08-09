package site.pwing.pwingcurios.integration.direct;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Soft, reflection-based internal support for MMOItems/MythicLib.
 *
 * This class uses best-effort reflection and gracefully no-ops if
 * the expected APIs are not available. It targets common MythicLib/MMOItems
 * stat names and attempts to apply aggregated Curio item stats to the player.
 */
public class MmoItemsDirectIntegration implements DirectIntegration {
    private static final String SOURCE_KEY = "PwingCurios";

    // Supported stat keys as commonly used by MythicLib/MMOItems
    private static final String[] SUPPORTED_STATS = new String[] {
            "CRITICAL_STRIKE_CHANCE",
            "MANA", "MAX_MANA", "MANA_REGENERATION", "MANA_REGEN", // allow a couple variants
            "SKILL_DAMAGE",
            "PHYSICAL_DAMAGE", "MAGIC_DAMAGE", "ELEMENTAL_DAMAGE",
            "DEFENSE", "TOUGHNESS", "MAX_HEALTH", "HEALTH_REGENERATION", "HEALTH_REGEN"
    };

    @Override
    public void apply(Player player, Map<String, ItemStack> equipped) {
        if (player == null || equipped == null || equipped.isEmpty()) return;
        try {
            // Aggregate stats from all Curio items via MMOItems Stat API if available
            Map<String, Double> totals = new HashMap<>();
            for (ItemStack item : equipped.values()) {
                if (item == null) continue;
                Map<String, Double> itemStats = readItemStatsViaReflection(item);
                if (itemStats == null) continue;
                for (Map.Entry<String, Double> e : itemStats.entrySet()) {
                    if (e.getValue() == null) continue;
                    String key = e.getKey().toUpperCase(Locale.ROOT);
                    if (!isSupported(key)) continue;
                    totals.merge(key, e.getValue(), Double::sum);
                }
            }
            if (totals.isEmpty()) {
                // Nothing to apply
                return;
            }
            applyToPlayerViaMythicLib(player, totals);
        } catch (Throwable ignored) {
            // Silently ignore integration errors
        }
    }

    @Override
    public void clear(Player player) {
        try {
            clearFromPlayerViaMythicLib(player);
        } catch (Throwable ignored) {
            // Silently ignore integration errors
        }
    }

    @Override
    public String getName() {
        return "MMOItems";
    }

    private boolean isSupported(String key) {
        for (String s : SUPPORTED_STATS) if (s.equalsIgnoreCase(key)) return true;
        return false;
    }

    // Attempts to read MMOItems stats from an ItemStack using MMOItems API reflectively
    @SuppressWarnings("unchecked")
    private Map<String, Double> readItemStatsViaReflection(ItemStack stack) {
        try {
            // Try: net.Indyuce.mmoitems.MMOItems#readStats(ItemStack) -> Map<String, Double>
            Class<?> mmoItemsClazz = Class.forName("net.Indyuce.mmoitems.MMOItems");
            Method getInstance = mmoItemsClazz.getMethod("getInstance");
            Object mmoItems = getInstance.invoke(null);
            // Some versions expose getStatAPI() or getLanguage() etc; try a generic reader method first
            for (Method m : mmoItemsClazz.getMethods()) {
                if (m.getName().toLowerCase(Locale.ROOT).contains("read") &&
                    m.getParameterCount() == 1 &&
                    Map.class.isAssignableFrom(m.getReturnType()) &&
                    m.getParameterTypes()[0].isAssignableFrom(ItemStack.class)) {
                    Object res = m.invoke(mmoItems, stack);
                    if (res instanceof Map) {
                        return (Map<String, Double>) res;
                    }
                }
            }
        } catch (Throwable ignored) { }

        // Fallback: none found
        return Collections.emptyMap();
    }

    // Apply aggregated stats to the player via MythicLib's PlayerData/StatMap if available
    private void applyToPlayerViaMythicLib(Player player, Map<String, Double> totals) {
        try {
            Class<?> pdClazz = Class.forName("io.lumine.mythiclib.api.player.PlayerData");
            Method get = null;
            try { get = pdClazz.getMethod("get", Player.class); } catch (NoSuchMethodException ignored) {}
            if (get == null) {
                // Try static get(UUID)
                get = pdClazz.getMethod("get", java.util.UUID.class);
                Object pd = get.invoke(null, player.getUniqueId());
                applyToStatMap(pd, totals);
                return;
            }
            Object pd = get.invoke(null, player);
            applyToStatMap(pd, totals);
        } catch (Throwable ignored) { }
    }

    private void clearFromPlayerViaMythicLib(Player player) {
        try {
            Class<?> pdClazz = Class.forName("io.lumine.mythiclib.api.player.PlayerData");
            Method get = null;
            try { get = pdClazz.getMethod("get", Player.class); } catch (NoSuchMethodException ignored) {}
            Object pd;
            if (get == null) {
                get = pdClazz.getMethod("get", java.util.UUID.class);
                pd = get.invoke(null, player.getUniqueId());
            } else {
                pd = get.invoke(null, player);
            }
            Object statMap = pd.getClass().getMethod("getStatMap").invoke(pd);
            // Try common removal methods
            Method rem = null;
            try { rem = statMap.getClass().getMethod("removeModifiers", String.class); } catch (NoSuchMethodException ignored) {}
            if (rem == null) try { rem = statMap.getClass().getMethod("removeAllModifiers", String.class); } catch (NoSuchMethodException ignored) {}
            if (rem == null) try { rem = statMap.getClass().getMethod("removeSource", String.class); } catch (NoSuchMethodException ignored) {}
            if (rem != null) {
                rem.invoke(statMap, SOURCE_KEY);
                // Try to push an update if available
                try { statMap.getClass().getMethod("updateStats").invoke(statMap); } catch (Throwable ignored2) {}
            }
        } catch (Throwable ignored) { }
    }

    private void applyToStatMap(Object playerData, Map<String, Double> totals) {
        try {
            Object statMap = playerData.getClass().getMethod("getStatMap").invoke(playerData);
            // First clear previous from our source
            try {
                Method rem = null;
                try { rem = statMap.getClass().getMethod("removeModifiers", String.class); } catch (NoSuchMethodException ignored) {}
                if (rem == null) try { rem = statMap.getClass().getMethod("removeAllModifiers", String.class); } catch (NoSuchMethodException ignored) {}
                if (rem == null) try { rem = statMap.getClass().getMethod("removeSource", String.class); } catch (NoSuchMethodException ignored) {}
                if (rem != null) rem.invoke(statMap, SOURCE_KEY);
            } catch (Throwable ignored) {}

            // Try a few common signatures to add modifiers
            Method addModifierStr = null; // (String stat, String source, double value)
            Method addModifierEnum = null; // (Enum stat, String source, double value)

            for (Method m : statMap.getClass().getMethods()) {
                if (!m.getName().toLowerCase(Locale.ROOT).contains("add") || !m.getName().toLowerCase(Locale.ROOT).contains("modifier"))
                    continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length == 3 && pts[0] == String.class && pts[1] == String.class && (pts[2] == double.class || pts[2] == Double.class)) {
                    addModifierStr = m; break;
                }
            }
            if (addModifierStr == null) {
                for (Method m : statMap.getClass().getMethods()) {
                    if (!m.getName().toLowerCase(Locale.ROOT).contains("add") || !m.getName().toLowerCase(Locale.ROOT).contains("modifier"))
                        continue;
                    Class<?>[] pts = m.getParameterTypes();
                    if (pts.length == 3 && pts[1] == String.class && (pts[2] == double.class || pts[2] == Double.class) && pts[0].isEnum()) {
                        addModifierEnum = m; break;
                    }
                }
            }

            if (addModifierStr == null && addModifierEnum == null) return; // cannot apply

            for (Map.Entry<String, Double> e : totals.entrySet()) {
                if (e.getValue() == null || e.getValue() == 0.0) continue;
                String statKey = e.getKey();
                double val = e.getValue();
                try {
                    if (addModifierStr != null) {
                        addModifierStr.invoke(statMap, statKey, SOURCE_KEY, val);
                    } else if (addModifierEnum != null) {
                        // Attempt to resolve enum by name in MythicLib stat enum (if any)
                        // e.g., io.lumine.mythiclib.api.stat.Attribute? (depends on version)
                        Enum<?> statEnum = resolveStatEnumByName(statKey);
                        if (statEnum != null) {
                            addModifierEnum.invoke(statMap, statEnum, SOURCE_KEY, val);
                        }
                    }
                } catch (Throwable ignored) {}
            }
            // Try to push an update if available
            try { statMap.getClass().getMethod("updateStats").invoke(statMap); } catch (Throwable ignored) {}
        } catch (Throwable ignored) { }
    }

    private Enum<?> resolveStatEnumByName(String name) {
        // Try a few likely enum containers
        String[] candidates = new String[] {
                "io.lumine.mythiclib.api.stat.StatType",
                "io.lumine.mythiclib.api.stats.StatType",
                "io.lumine.mythiclib.api.stat.Attribute"
        };
        for (String fqn : candidates) {
            try {
                Class<?> ec = Class.forName(fqn);
                if (!ec.isEnum()) continue;
                Object[] consts = ec.getEnumConstants();
                for (Object c : consts) {
                    if (((Enum<?>) c).name().equalsIgnoreCase(name)) return (Enum<?>) c;
                }
            } catch (Throwable ignored) { }
        }
        return null;
    }
}
