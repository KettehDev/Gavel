package net.hatpack.gavel;

import net.hatpack.gavel.menu.HistoryMenu;
import net.hatpack.gavel.menu.PunishMenu;
import net.hatpack.gavel.menu.ReasonMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Commands implements TabExecutor {

    private static final List<String> DURATIONS = List.of("30m", "1h", "6h", "1d", "3d", "7d", "14d", "30d", "perm");

    private final GavelPlugin plugin;
    private final Map<UUID, Long> lastReport = new HashMap<>();

    public Commands(GavelPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        String perm = switch (name) {
            case "gavel" -> "gavel.reload";
            default -> "gavel." + name;
        };
        if (!sender.hasPermission(perm)) {
            Msg.send(sender, "no-permission");
            return true;
        }

        switch (name) {
            case "warn", "mute", "kick", "ban", "blacklist" -> punish(sender, PunishType.valueOf(name.toUpperCase()), label, args);
            case "unmute", "unban", "unblacklist" -> unpunish(sender, PunishType.valueOf(name.substring(2).toUpperCase()), label, args);
            case "unwarn" -> unwarn(sender, label, args);
            case "freeze" -> freeze(sender, label, args);
            case "punish" -> withTarget(sender, label, args, "<player>", true, t -> new PunishMenu(plugin, (Player) sender, t).open((Player) sender));
            case "history" -> history(sender, label, args);
            case "check" -> withTarget(sender, label, args, "<player>", false, t -> check(sender, t));
            case "alts" -> withTarget(sender, label, args, "<player>", false, t -> alts(sender, t));
            case "commandspy" -> {
                if (sender instanceof Player p) plugin.spy().toggle(p);
                else Msg.send(sender, "player-only");
            }
            case "staffchat" -> staffChat(sender, args);
            case "report" -> report(sender, label, args);
            case "gavel" -> {
                plugin.reloadConfig();
                Msg.send(sender, "reloaded");
            }
        }
        return true;
    }

    // --- punishing ---

    private void punish(CommandSender sender, PunishType type, String label, String[] args) {
        if (args.length == 0) {
            usage(sender, label, type.timed() ? "<player> [duration] [reason] [-s]" : "<player> [reason] [-s]");
            return;
        }
        List<String> rest = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
        boolean silent = rest.removeIf(a -> a.equalsIgnoreCase("-s"));

        long duration = Durations.PERMANENT;
        if (type.timed() && !rest.isEmpty()) {
            Long parsed = Durations.parse(rest.get(0));
            if (parsed != null) {
                duration = parsed;
                rest.remove(0);
            }
        }
        String typed = String.join(" ", rest).trim();
        long finalDuration = duration;

        plugin.resolve(sender, args[0], target -> {
            if (type == PunishType.KICK && Bukkit.getPlayer(target.uuid()) == null) {
                Msg.send(sender, "not-online", "player", target.name());
                return;
            }
            if (typed.isEmpty() && sender instanceof Player staff) {
                new ReasonMenu(plugin, staff, type, target).open(staff);
                return;
            }
            String reason = typed.isEmpty() ? plugin.getConfig().getString("default-reason", "Breaking the rules") : typed;
            plugin.punisher().punish(sender, sender.getName(), type, target, reason, finalDuration, silent);
        });
    }

    private void unpunish(CommandSender sender, PunishType type, String label, String[] args) {
        if (args.length == 0) {
            usage(sender, label, "<player> [reason]");
            return;
        }
        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "No reason given";
        plugin.resolve(sender, args[0], target -> plugin.punisher().unpunish(sender, type, target, reason));
    }

    private void unwarn(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            usage(sender, label, "<id | player>");
            return;
        }
        String raw = args[0].startsWith("#") ? args[0].substring(1) : args[0];
        if (raw.chars().allMatch(Character::isDigit)) {
            int id = Integer.parseInt(raw);
            plugin.async(() -> {
                Punishment p = plugin.db().byId(id);
                if (p == null || p.type() != PunishType.WARN || !p.active()) return false;
                return plugin.db().remove(id, sender.getName(), "Removed with /unwarn");
            }, ok -> Msg.send(sender, ok ? "warn-removed" : "no-such-id", "id", String.valueOf(id)), sender);
        } else {
            // a name clears all of their warnings
            plugin.resolve(sender, args[0], t -> plugin.punisher().unpunish(sender, PunishType.WARN, t, "Cleared with /unwarn"));
        }
    }

    // --- staff tools ---

    private void freeze(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            usage(sender, label, "<player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Msg.send(sender, "not-online", "player", args[0]);
            return;
        }
        plugin.freeze().toggle(sender, target);
    }

    private void history(CommandSender sender, String label, String[] args) {
        withTarget(sender, label, args, "<player>", false, target -> {
            if (sender instanceof Player p) {
                HistoryMenu.open(plugin, p, target);
                return;
            }
            plugin.async(() -> plugin.db().history(target.uuid()), list -> {
                sender.sendMessage(Msg.parse("<gold>History of <player> <gray>(" + list.size() + ")", "player", target.name()));
                for (Punishment p : list.subList(0, Math.min(15, list.size()))) {
                    sender.sendMessage(Msg.parse("<gray>#<id> <white><type></white> <dark_gray>-</dark_gray> <reason> <dark_gray>(<staff>, <date>)" + (p.inEffect() ? " <green>active" : ""),
                            "id", String.valueOf(p.id()), "type", p.type().display(), "reason", p.reason(),
                            "staff", p.staffName(), "date", plugin.punisher().date(p.created())));
                }
            }, sender);
        });
    }

    private void check(CommandSender sender, Target target) {
        plugin.async(() -> {
            Database db = plugin.db();
            Punishment ban = db.active(target.uuid(), PunishType.BAN);
            Punishment mute = db.active(target.uuid(), PunishType.MUTE);
            Punishment bl = db.active(target.uuid(), PunishType.BLACKLIST);
            if (bl == null) bl = db.activeByIp(target.ip(), PunishType.BLACKLIST);
            int warns = db.activeWarnings(target.uuid());
            int alts = db.alts(target.uuid()).size();
            return new Object[]{ban, mute, bl, warns, alts};
        }, r -> {
            Punishment ban = (Punishment) r[0], mute = (Punishment) r[1], bl = (Punishment) r[2];
            Player online = Bukkit.getPlayer(target.uuid());
            sender.sendMessage(Msg.parse("<dark_gray>------ <gold><player></gold> ------", "player", target.name()));
            sender.sendMessage(Msg.parse("<gray>Status: " + (online != null ? "<green>online" : "<red>offline")
                    + (plugin.freeze().isFrozen(target.uuid()) ? " <aqua>(frozen)" : "")));
            sender.sendMessage(status("Banned", ban));
            sender.sendMessage(status("Muted", mute));
            sender.sendMessage(status("Blacklisted", bl));
            sender.sendMessage(Msg.parse("<gray>Active warnings: <white>" + r[3]));
            sender.sendMessage(Msg.parse("<gray>Accounts on same IP: <white>" + r[4]));
            if (sender.hasPermission("gavel.alts.ip") && target.ip() != null) {
                sender.sendMessage(Msg.parse("<gray>Last IP: <white><ip>", "ip", target.ip()));
            }
        }, sender);
    }

    private Component status(String label, Punishment p) {
        if (p == null) return Msg.parse("<gray>" + label + ": <green>no");
        return Msg.parse("<gray>" + label + ": <red>yes</red> <dark_gray>(#<id>, <remaining> left)</dark_gray> <white><reason>",
                plugin.punisher().placeholders(p));
    }

    private void alts(CommandSender sender, Target target) {
        plugin.async(() -> {
            List<Component> names = new ArrayList<>();
            for (Target alt : plugin.db().alts(target.uuid())) {
                boolean banned = plugin.db().active(alt.uuid(), PunishType.BAN) != null
                        || plugin.db().active(alt.uuid(), PunishType.BLACKLIST) != null;
                boolean online = Bukkit.getPlayer(alt.uuid()) != null;
                names.add(Msg.parse((banned ? "<red>" : online ? "<green>" : "<gray>") + "<name>", "name", alt.name()));
            }
            return names;
        }, names -> {
            if (names.isEmpty()) {
                sender.sendMessage(Msg.parse("<prefix><gray>No other accounts share an IP with <player>.", "player", target.name()));
                return;
            }
            Component line = Msg.parse("<prefix><gray>Alts of <white><player></white> <dark_gray>(<green>online</green>, <red>banned</red>)</dark_gray>: ", "player", target.name());
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) line = line.append(Msg.parse("<dark_gray>, "));
                line = line.append(names.get(i));
            }
            sender.sendMessage(line);
        }, sender);
    }

    private void staffChat(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) plugin.staffChat().toggle(p);
            else Msg.send(sender, "player-only");
            return;
        }
        plugin.staffChat().send(sender, String.join(" ", args));
    }

    private void report(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "player-only");
            return;
        }
        if (args.length < 2) {
            usage(sender, label, "<player> <reason>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Msg.send(sender, "not-online", "player", args[0]);
            return;
        }
        if (target == player) {
            Msg.send(sender, "report-self");
            return;
        }
        long cooldown = plugin.getConfig().getLong("report-cooldown", 60) * 1000;
        Long last = lastReport.get(player.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < cooldown) {
            Msg.send(sender, "report-cooldown", "remaining", Durations.format(cooldown - (System.currentTimeMillis() - last)));
            return;
        }
        lastReport.put(player.getUniqueId(), System.currentTimeMillis());

        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        Component note = Msg.get("report-staff", "reporter", player.getName(), "player", target.getName(), "reason", reason)
                .append(Msg.get("report-button")
                        .clickEvent(ClickEvent.runCommand("/punish " + target.getName()))
                        .hoverEvent(HoverEvent.showText(Msg.parse("<gray>Open the punish menu"))));
        plugin.punisher().notifyStaff(note);
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("gavel.notify")) {
                staff.playSound(staff.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.4f);
            }
        }
        Msg.send(sender, "report-sent");
    }

    // --- helpers ---

    private void withTarget(CommandSender sender, String label, String[] args, String usage, boolean playerOnly,
                            java.util.function.Consumer<Target> then) {
        if (playerOnly && !(sender instanceof Player)) {
            Msg.send(sender, "player-only");
            return;
        }
        if (args.length == 0) {
            usage(sender, label, usage);
            return;
        }
        plugin.resolve(sender, args[0], then);
    }

    private void usage(CommandSender sender, String label, String usage) {
        Msg.send(sender, "usage", "usage", "/" + label + " " + usage);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase();
        List<String> out = new ArrayList<>();
        if (name.equals("gavel")) {
            out.add("reload");
        } else if (name.equals("commandspy") || name.equals("staffchat")) {
            return out;
        } else if (args.length == 1) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args.length == 2 && (name.equals("mute") || name.equals("ban"))) {
            out.addAll(DURATIONS);
        } else if (args.length >= 2 && List.of("warn", "mute", "kick", "ban", "blacklist").contains(name)) {
            out.add("-s");
        }
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        out.removeIf(s -> !s.toLowerCase().startsWith(typed));
        return out;
    }
}
