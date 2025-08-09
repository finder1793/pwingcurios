package site.pwing.pwingcurios.integration.direct;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Internal integration contract for optional plugins.
 * Implementations should be safe no-ops if their target plugin or API is missing.
 */
public interface DirectIntegration {
    /**
     * Apply external stats/effects for the given player based on equipped Curios.
     */
    void apply(Player player, Map<String, ItemStack> equipped);

    /**
     * Clear any previously applied external stats/effects for the player.
     */
    void clear(Player player);

    /**
     * @return human-readable name of the integration (e.g., "MMOItems").
     */
    String getName();
}
