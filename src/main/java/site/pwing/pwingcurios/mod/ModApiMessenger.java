package site.pwing.pwingcurios.mod;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import site.pwing.pwingcurios.Pwingcurios;
import site.pwing.pwingcurios.manager.SlotDefinition;

import java.nio.charset.StandardCharsets;

/**
 * Simple plugin messaging based mod API on channel "pwingcurios:api".
 *
 * Requests from client mods (sent to server on the channel):
 * - OPEN: opens the GUI for the sending player
 * - SLOTS: server replies with SLOTS;<count> then repeated [slotId,index,name]
 */
public class ModApiMessenger implements PluginMessageListener {
    public static final String CHANNEL = "pwingcurios:api";

    private final Pwingcurios plugin;

    public ModApiMessenger(Pwingcurios plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equalsIgnoreCase(channel)) return;
        if (player == null) return;

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String cmd = in.readUTF();
        if ("OPEN".equalsIgnoreCase(cmd)) {
            plugin.getAccessoriesGUI().open(player);
            return;
        }
        if ("SLOTS".equalsIgnoreCase(cmd)) {
            // Reply with slot definitions
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("SLOTS");
            out.writeInt(plugin.getAccessoryManager().getSlots().size());
            for (SlotDefinition def : plugin.getAccessoryManager().getSlots()) {
                out.writeUTF(def.getId());
                out.writeInt(def.getIndex());
                out.writeUTF(def.getName() == null ? def.getId() : def.getName());
            }
            player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
        }
    }
}
