package net.hatpack.gavel;

import net.hatpack.gavel.menu.MenuListener;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class GavelPlugin extends JavaPlugin {

    public static final String[] COMMANDS = {
            "warn", "mute", "ban", "kick", "blacklist", "unwarn", "unmute", "unban", "unblacklist",
            "freeze", "history", "check", "alts", "punish", "commandspy", "staffchat", "report", "gavel"
    };

    private final Database db = new Database();
    private final Map<UUID, Punishment> mutes = new ConcurrentHashMap<>();
    private Punisher punisher;
    private Freeze freeze;
    private Spy spy;
    private final StaffChat staffChat = new StaffChat();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Msg.init(this);
        try {
            getDataFolder().mkdirs();
            db.open(new File(getDataFolder(), "gavel.db"));
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Couldn't open the database, disabling", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        punisher = new Punisher(this);
        freeze = new Freeze(this);
        spy = new Spy(this);

        Commands commands = new Commands(this);
        for (String name : COMMANDS) {
            PluginCommand cmd = getCommand(name);
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }

        var pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(freeze, this);
        pm.registerEvents(spy, this);

        // after a /reload people are already online, load their mutes again
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            async(() -> db.active(id, PunishType.MUTE), m -> {
                if (m != null) mutes.put(id, m);
            }, null);
        }
    }

    @Override
    public void onDisable() {
        if (freeze != null) freeze.stop();
        db.close();
    }

    public interface Work<T> {
        T run() throws Exception;
    }

    /** runs {@code work} off the main thread, then {@code then} back on it. errors go to {@code errorsTo} */
    public <T> void async(Work<T> work, Consumer<T> then, CommandSender errorsTo) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            T result;
            try {
                result = work.run();
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Database error", e);
                if (errorsTo != null) Bukkit.getScheduler().runTask(this, () -> Msg.send(errorsTo, "db-error"));
                return;
            }
            Bukkit.getScheduler().runTask(this, () -> then.accept(result));
        });
    }

    /** finds a player by name: online first, then anyone who ever joined */
    public void resolve(CommandSender asker, String name, Consumer<Target> then) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            then.accept(Target.of(online));
            return;
        }
        async(() -> db.findPlayer(name), found -> {
            if (found != null) {
                then.accept(found);
                return;
            }
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
            if (cached != null) {
                then.accept(new Target(cached.getUniqueId(), cached.getName() == null ? name : cached.getName(), null));
            } else {
                Msg.send(asker, "not-found", "player", name);
            }
        }, asker);
    }

    public Database db() {
        return db;
    }

    public Punisher punisher() {
        return punisher;
    }

    public Freeze freeze() {
        return freeze;
    }

    public Spy spy() {
        return spy;
    }

    public StaffChat staffChat() {
        return staffChat;
    }

    public Map<UUID, Punishment> mutes() {
        return mutes;
    }
}
