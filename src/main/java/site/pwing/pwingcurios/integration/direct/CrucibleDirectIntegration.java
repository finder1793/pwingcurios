package site.pwing.pwingcurios.integration.direct;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Soft, reflection-based internal support for MythicCraft's Crucible plugin.
 *
 * This implementation is a safe no-op placeholder. If Crucible exposes a stable API
 * for applying item stats directly to players outside normal equipment slots,
 * we can reflect and call those methods here without adding hard dependencies.
 */
public class CrucibleDirectIntegration implements DirectIntegration {
    @Override
    public void apply(Player player, Map<String, ItemStack> equipped) {
        // Placeholder: rely on built-in AttributeModifier mirroring, plus external event hooks.
    }

    @Override
    public void clear(Player player) {
        // No persistent state to clear in this soft integration.
    }

    @Override
    public String getName() {
        return "Crucible";
    }
}
