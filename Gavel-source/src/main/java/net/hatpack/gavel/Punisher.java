package net.hatpack.gavel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;

/** the one place punishments actually get created and enforced */
public final class Punisher {

    private final GavelPlugin plugin;

    public Punisher(GavelPlugin plugin) {
        this.plugin = plugin;
    }

    public void punish(CommandSender staff, String staffName, PunishType type, Target target,
                       String reason, long duration, boolean silent) {
        Player online = Bukkit.getPlayer(target.uuid());
        if (online != null && online.hasPermission("gavel.bypass") && !(staff instanceof ConsoleCommandSender)) {
            Msg.send(staff, "bypass", "player", target.name());
            return;
        }

        long now = System.currentTimeMillis();
        long expires = type.timed() && duration > 0 ? now + duration : 0;
        String ip = online != null ? Target.of(online).ip() : target.ip();
        Punishment draft = new Punishment(0, type, target.uuid(), target.name(), ip, reason, staffName,
                now, expires, type != PunishType.KICK, null, null);

        plugin.async(() -> {
            // a new ban/mute replaces the old one instead of stacking
            if (type == PunishType.MUTE || type == PunishType.BAN || type == PunishType.BLACKLIST) {
                plugin.db().removeAll(target.uuid(), type, staffName, "Replaced");
            }
            return plugin.db().insert(draft);
        }, saved -> enforce(saved, silent), staff);
    }

    private void enforce(Punishment p, boolean silent) {
        Player player = Bukkit.getPlayer(p.uuid());
        String[] ph = placeholders(p);

        // unfreeze before kicking, otherwise the quit counts as logging out while frozen
        if (p.type() == PunishType.KICK || p.type() == PunishType.BAN || p.type() == PunishType.BLACKLIST) {
            plugin.freeze().release(p.uuid());
        }

        switch (p.type()) {
            case WARN -> plugin.async(() -> plugin.db().activeWarnings(p.uuid()), count -> {
                if (player != null) {
                    String[] withCount = append(ph, "count", String.valueOf(count));
                    Msg.send(player, "warned", withCount);
                    player.showTitle(Title.title(Msg.get("warned-title", withCount), Msg.get("warned-subtitle", withCount),
                            Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(600))));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f);
                }
                escalate(p, count);
            }, null);
            case MUTE -> {
                plugin.mutes().put(p.uuid(), p);
                if (player != null) {
                    Msg.send(player, "muted", ph);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                }
            }
            case KICK -> {
                if (player != null) player.kick(Msg.screen("kick", ph));
            }
            case BAN -> {
                if (player != null) player.kick(Msg.screen("ban", ph));
            }
            case BLACKLIST -> {
                // everyone on that connection goes
                for (Player other : Bukkit.getOnlinePlayers()) {
                    String otherIp = Target.of(other).ip();
                    if (other.getUniqueId().equals(p.uuid()) || (p.ip() != null && p.ip().equals(otherIp))) {
                        other.kick(Msg.screen("blacklist", ph));
                    }
                }
            }
        }
        Component note = Msg.get("notify-" + p.type().key(), ph);
        if (silent) note = Msg.get("notify-silent").append(note);
        notifyStaff(note);

        if (!silent && plugin.getConfig().getBoolean("broadcast." + p.type().key())) {
            Component pub = Msg.get("broadcast-" + p.type().key(), ph);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.hasPermission("gavel.notify")) online.sendMessage(pub);
            }
        }
    }

    private void escalate(Punishment warn, int count) {
        ConfigurationSection steps = plugin.getConfig().getConfigurationSection("warn-escalation.steps");
        if (!plugin.getConfig().getBoolean("warn-escalation.enabled") || steps == null) return;
        String action = steps.getString(String.valueOf(count));
        if (action == null) return;

        String[] parts = action.trim().split("\\s+");
        PunishType type;
        try {
            type = PunishType.valueOf(parts[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Bad warn-escalation step '" + action + "'");
            return;
        }
        Long duration = parts.length > 1 ? Durations.parse(parts[1]) : Long.valueOf(Durations.PERMANENT);
        if (duration == null) duration = Durations.PERMANENT;

        notifyStaff(Msg.get("notify-escalation", "player", warn.name(), "count", String.valueOf(count), "action", action));
        punish(Bukkit.getConsoleSender(), "AutoMod", type, new Target(warn.uuid(), warn.name(), warn.ip()),
                "Reached " + count + " warnings (last: " + warn.reason() + ")", duration, false);
    }

    /** lift a ban/mute/blacklist. callback gets how many were removed */
    public void unpunish(CommandSender staff, PunishType type, Target target, String reason) {
        plugin.async(() -> plugin.db().removeAll(target.uuid(), type, staff.getName(), reason), removed -> {
            if (removed == 0) {
                Msg.send(staff, "nothing-to-remove", "player", target.name(), "type", type.past());
                return;
            }
            afterRemove(type, target.uuid());
            Msg.send(staff, "removed", "player", target.name(), "type", type.display().toLowerCase());
            notifyStaff(Msg.get("notify-removed", "staff", staff.getName(), "player", target.name(), "type", type.display().toLowerCase()));
        }, staff);
    }

    /** cache/online cleanup after something was lifted, used by the history menu too */
    public void afterRemove(PunishType type, java.util.UUID uuid) {
        if (type == PunishType.MUTE) {
            plugin.mutes().remove(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) Msg.send(p, "unmuted-player");
        }
    }

    public void notifyStaff(Component message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("gavel.notify")) p.sendMessage(message);
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }

    public String[] placeholders(Punishment p) {
        String duration = p.type().timed() ? (p.permanent() ? "permanent" : Durations.format(p.expires() - p.created())) : "-";
        String expires = p.permanent() ? "Never" : Durations.format(p.remaining()) + " (" + date(p.expires()) + ")";
        return new String[]{
                "player", p.name(),
                "reason", p.reason(),
                "staff", p.staffName(),
                "duration", duration,
                "expires", expires,
                "remaining", p.permanent() ? "forever" : Durations.format(p.remaining()),
                "id", String.valueOf(p.id()),
                "type", p.type().display()
        };
    }

    public String date(long millis) {
        String pattern = plugin.getConfig().getString("date-format", "MMM d yyyy, HH:mm");
        return new SimpleDateFormat(pattern).format(new Date(millis));
    }

    private static String[] append(String[] base, String key, String value) {
        String[] out = java.util.Arrays.copyOf(base, base.length + 2);
        out[base.length] = key;
        out[base.length + 1] = value;
        return out;
    }
}
