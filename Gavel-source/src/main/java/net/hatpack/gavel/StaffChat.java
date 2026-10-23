package net.hatpack.gavel;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StaffChat {

    private final Set<UUID> toggled = ConcurrentHashMap.newKeySet();

    public boolean isToggled(Player p) {
        return toggled.contains(p.getUniqueId());
    }

    public void toggle(Player p) {
        if (toggled.remove(p.getUniqueId())) {
            Msg.send(p, "staffchat-off");
        } else {
            toggled.add(p.getUniqueId());
            Msg.send(p, "staffchat-on");
        }
    }

    public void send(CommandSender from, String message) {
        var line = Msg.get("staffchat-format", "player", from.getName(), "message", message);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("gavel.staffchat")) p.sendMessage(line);
        }
        Bukkit.getConsoleSender().sendMessage(line);
    }
}
