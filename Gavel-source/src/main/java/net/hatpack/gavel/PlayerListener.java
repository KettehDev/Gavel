package net.hatpack.gavel;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerListener implements Listener {

    private final GavelPlugin plugin;

    public PlayerListener(GavelPlugin plugin) {
        this.plugin = plugin;
    }

    /** "/minecraft:msg bob hi" -> "msg" */
    static String root(String message) {
        String first = message.startsWith("/") ? message.substring(1) : message;
        int space = first.indexOf(' ');
        if (space >= 0) first = first.substring(0, space);
        int colon = first.indexOf(':');
        if (colon >= 0) first = first.substring(colon + 1);
        return first.toLowerCase(Locale.ROOT);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        UUID uuid = event.getUniqueId();
        String ip = event.getAddress().getHostAddress();
        Database db = plugin.db();
        try {
            db.seen(uuid, event.getName(), ip);

            Punishment bl = db.active(uuid, PunishType.BLACKLIST);
            if (bl == null) bl = db.activeByIp(ip, PunishType.BLACKLIST);
            if (bl != null) {
                deny(event, "blacklist", bl);
                return;
            }
            Punishment ban = db.active(uuid, PunishType.BAN);
            if (ban != null) {
                deny(event, "ban", ban);
                return;
            }

            Punishment mute = db.active(uuid, PunishType.MUTE);
            if (mute != null) plugin.mutes().put(uuid, mute);
            else plugin.mutes().remove(uuid);
        } catch (Exception e) {
            // don't lock everyone out because of a db hiccup
            plugin.getLogger().log(Level.SEVERE, "Couldn't check punishments for " + event.getName(), e);
        }
    }

    private void deny(AsyncPlayerPreLoginEvent event, String screen, Punishment p) {
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, Msg.screen(screen, plugin.punisher().placeholders(p)));
        String name = event.getName();
        Bukkit.getScheduler().runTask(plugin, () -> plugin.punisher().notifyStaff(
                Msg.get("notify-join-attempt", "player", name, "type", p.type().past())));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.mutes().remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());

        Punishment mute = plugin.mutes().get(p.getUniqueId());
        if (mute != null) {
            if (mute.expired()) {
                plugin.mutes().remove(p.getUniqueId());
            } else {
                event.setCancelled(true);
                Msg.send(p, "still-muted", plugin.punisher().placeholders(mute));
                return;
            }
        }

        if (plugin.freeze().isFrozen(p.getUniqueId()) && plugin.getConfig().getBoolean("freeze.chat-to-staff")) {
            event.setCancelled(true);
            Component line = Msg.get("frozen-chat", "player", p.getName(), "message", text);
            p.sendMessage(line);
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff != p && staff.hasPermission("gavel.freeze")) staff.sendMessage(line);
            }
            return;
        }

        if (p.hasPermission("gavel.staffchat") && plugin.staffChat().isToggled(p)) {
            event.setCancelled(true);
            plugin.staffChat().send(p, text);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        Punishment mute = plugin.mutes().get(p.getUniqueId());
        if (mute == null || mute.expired()) return;
        String root = root(event.getMessage());
        if (plugin.getConfig().getStringList("muted-commands").stream().anyMatch(root::equalsIgnoreCase)) {
            event.setCancelled(true);
            Msg.send(p, "still-muted", plugin.punisher().placeholders(mute));
        }
    }
}
