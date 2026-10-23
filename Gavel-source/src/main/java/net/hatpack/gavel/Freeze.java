package net.hatpack.gavel;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Freeze implements Listener {

    private final GavelPlugin plugin;
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
    private final BukkitTask reminder;

    public Freeze(GavelPlugin plugin) {
        this.plugin = plugin;
        // keep the title up the whole time so they can't miss it
        this.reminder = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (UUID id : frozen) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.showTitle(title());
            }
        }, 40, 40);
    }

    public boolean isFrozen(UUID uuid) {
        return frozen.contains(uuid);
    }

    public void toggle(CommandSender staff, Player target) {
        if (isFrozen(target.getUniqueId())) {
            release(target.getUniqueId());
            Msg.send(target, "you-unfrozen");
            target.playSound(target.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
            plugin.punisher().notifyStaff(Msg.get("unfrozen", "player", target.getName()));
            return;
        }
        if (target.hasPermission("gavel.bypass") && !(staff instanceof ConsoleCommandSender)) {
            Msg.send(staff, "bypass", "player", target.getName());
            return;
        }
        frozen.add(target.getUniqueId());
        Msg.send(target, "you-frozen");
        target.showTitle(title());
        target.playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.6f);
        if (plugin.getConfig().getBoolean("freeze.blindness")) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, PotionEffect.INFINITE_DURATION, 0, false, false));
        }
        plugin.punisher().notifyStaff(Msg.get("frozen", "player", target.getName()));
    }

    /** unfreeze without any messages, e.g. right before they get banned */
    public void release(UUID uuid) {
        if (!frozen.remove(uuid)) return;
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            p.clearTitle();
            if (plugin.getConfig().getBoolean("freeze.blindness")) p.removePotionEffect(PotionEffectType.BLINDNESS);
        }
    }

    public void stop() {
        reminder.cancel();
        List.copyOf(frozen).forEach(this::release);
    }

    private Title title() {
        return Title.title(Msg.get("you-frozen-title"), Msg.get("you-frozen-subtitle"),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(2600), Duration.ofMillis(300)));
    }

    // --- keep them still ---

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!isFrozen(event.getPlayer().getUniqueId())) return;
        Location from = event.getFrom(), to = event.getTo();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            // still let them look around
            Location back = from.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            event.setTo(back);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventory(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player p && isFrozen(p.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        // can't be hurt while frozen, it's not their fault they can't move
        if (event.getEntity() instanceof Player p && isFrozen(p.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p && isFrozen(p.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (!isFrozen(p.getUniqueId())) return;
        String root = PlayerListener.root(event.getMessage());
        if (plugin.getConfig().getStringList("freeze.allowed-commands").stream().noneMatch(root::equalsIgnoreCase)) {
            event.setCancelled(true);
            Msg.send(p, "frozen-blocked");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if (!frozen.remove(p.getUniqueId())) return;

        // restarts and kicks don't count, only them leaving on their own
        PlayerQuitEvent.QuitReason why = event.getReason();
        if (Bukkit.isStopping() || why == PlayerQuitEvent.QuitReason.KICKED) return;

        plugin.punisher().notifyStaff(Msg.get("frozen-left", "player", p.getName()));
        if (!plugin.getConfig().getBoolean("freeze.ban-on-logout")) return;

        Long duration = Durations.parse(plugin.getConfig().getString("freeze.logout-ban-duration", "perm"));
        plugin.punisher().punish(Bukkit.getConsoleSender(), "AutoMod", PunishType.BAN, Target.of(p),
                plugin.getConfig().getString("freeze.logout-ban-reason", "Logged out while frozen"),
                duration == null ? Durations.PERMANENT : duration, false);
    }

    private void block(Player p, org.bukkit.event.Cancellable event) {
        if (isFrozen(p.getUniqueId())) {
            event.setCancelled(true);
            Msg.send(p, "frozen-blocked");
        }
    }
}
