package net.hatpack.gavel;

import org.bukkit.entity.Player;

import java.util.UUID;

/** someone we can punish, online or not. ip can be null if they never joined */
public record Target(UUID uuid, String name, String ip) {

    public static Target of(Player player) {
        String ip = player.getAddress() == null ? null : player.getAddress().getAddress().getHostAddress();
        return new Target(player.getUniqueId(), player.getName(), ip);
    }
}
