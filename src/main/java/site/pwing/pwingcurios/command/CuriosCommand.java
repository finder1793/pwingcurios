package site.pwing.pwingcurios.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import site.pwing.pwingcurios.Pwingcurios;
import site.pwing.pwingcurios.gui.AccessoriesGUI;
import site.pwing.pwingcurios.manager.AccessoryManager;

public class CuriosCommand implements CommandExecutor {

    private final Pwingcurios plugin;
    private final AccessoryManager accessoryManager;
    private final AccessoriesGUI accessoriesGUI;

    public CuriosCommand(Pwingcurios plugin, AccessoryManager accessoryManager, AccessoriesGUI accessoriesGUI) {
        this.plugin = plugin;
        this.accessoryManager = accessoryManager;
        this.accessoriesGUI = accessoriesGUI;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0 || args[0].equalsIgnoreCase("open")) {
            accessoriesGUI.open(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("givetest")) {
            player.getInventory().addItem(accessoryManager.createTestAccessorySwift());
            player.sendMessage("§aGiven a §bSwift Charm§a.");
            return true;
        }

        sender.sendMessage("§eUsage: /" + label + " [open|give]");
        return true;
    }
}
