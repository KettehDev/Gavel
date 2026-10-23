package net.hatpack.gavel;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.persistence.PersistentDataType;

/** command spy. the toggle is saved on the player so it survives relogs */
public final class Spy implements Listener {

    private final GavelPlugin plugin;
    private final NamespacedKey key;

    public Spy(GavelPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "commandspy");
    }

    public boolean isSpying(Player p) {
        return p.getPersistentDataContainer().has(key, PersistentDataType.BYTE) && p.hasPermission("gavel.commandspy");
    }

    public void toggle(Player p) {
        if (p.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            p.getPersistentDataContainer().remove(key);
            Msg.send(p, "spy-off");
        } else {
            p.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            Msg.send(p, "spy-on");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player sender = event.getPlayer();
        if (sender.hasPermission("gavel.commandspy.exempt")) return;
        String root = PlayerListener.root(event.getMessage());
        if (plugin.getConfig().getStringList("commandspy.ignored").stream().anyMatch(root::equalsIgnoreCase)) return;

        var line = Msg.get("spy-format", "player", sender.getName(), "command", event.getMessage());
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p != sender && isSpying(p)) p.sendMessage(line);
        }
    }
}
