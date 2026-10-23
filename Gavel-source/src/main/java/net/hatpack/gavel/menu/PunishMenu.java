package net.hatpack.gavel.menu;

import net.hatpack.gavel.GavelPlugin;
import net.hatpack.gavel.Msg;
import net.hatpack.gavel.PunishType;
import net.hatpack.gavel.Target;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

/** /punish <player>: one menu with everything you can do to them */
public class PunishMenu extends Menu {

    private final GavelPlugin plugin;
    private final Player staff;
    private final Target target;

    public PunishMenu(GavelPlugin plugin, Player staff, Target target) {
        super(3, Msg.parse("<dark_gray>Punish » <player>", "player", target.name()));
        this.plugin = plugin;
        this.staff = staff;
        this.target = target;
        draw();
    }

    private void draw() {
        clear();
        fill(0, 26);

        Player online = Bukkit.getPlayer(target.uuid());
        ItemStack head = item(Material.PLAYER_HEAD, "<white><player>", List.of(
                online != null ? "<green>Online" : "<gray>Offline"), "player", target.name());
        SkullMeta skull = (SkullMeta) head.getItemMeta();
        skull.setOwningPlayer(Bukkit.getOfflinePlayer(target.uuid()));
        head.setItemMeta(skull);
        set(4, head);

        button(10, PunishType.WARN, Material.YELLOW_DYE, "<yellow>Warn");
        button(11, PunishType.MUTE, Material.ORANGE_DYE, "<gold>Mute");
        button(12, PunishType.KICK, Material.FEATHER, "<aqua>Kick");

        if (staff.hasPermission("gavel.freeze") && online != null) {
            boolean frozen = plugin.freeze().isFrozen(target.uuid());
            set(13, item(Material.PACKED_ICE, frozen ? "<aqua>Unfreeze" : "<aqua>Freeze",
                    List.of("<gray>" + (frozen ? "Let them move again" : "Lock them in place for a check"))), e -> {
                Player now = Bukkit.getPlayer(target.uuid());
                if (now == null) {
                    Msg.send(staff, "not-online", "player", target.name());
                    return;
                }
                plugin.freeze().toggle(staff, now);
                draw();
            });
        }

        button(14, PunishType.BAN, Material.IRON_AXE, "<red>Ban");
        button(15, PunishType.BLACKLIST, Material.WITHER_SKELETON_SKULL, "<dark_red>Blacklist");

        if (staff.hasPermission("gavel.history")) {
            set(16, item(Material.BOOK, "<white>History", List.of("<gray>Every punishment they've had")),
                    e -> HistoryMenu.open(plugin, staff, target));
        }
    }

    private void button(int slot, PunishType type, Material icon, String name) {
        if (!staff.hasPermission(type.permission())) return;
        set(slot, item(icon, name, List.of("<gray>Pick a reason")), e -> new ReasonMenu(plugin, staff, type, target).open(staff));
    }
}
