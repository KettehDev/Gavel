package net.hatpack.gavel.menu;

import net.hatpack.gavel.Durations;
import net.hatpack.gavel.GavelPlugin;
import net.hatpack.gavel.Msg;
import net.hatpack.gavel.PunishType;
import net.hatpack.gavel.Target;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** preset reasons for one punishment type, plus custom reason / silent toggle */
public class ReasonMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final GavelPlugin plugin;
    private final Player staff;
    private final PunishType type;
    private final Target target;
    private boolean silent;

    public ReasonMenu(GavelPlugin plugin, Player staff, PunishType type, Target target) {
        super(5, Msg.parse("<dark_gray>" + type.display() + " » <player>", "player", target.name()));
        this.plugin = plugin;
        this.staff = staff;
        this.type = type;
        this.target = target;
        draw();
    }

    private void draw() {
        clear();
        fill(0, 44);

        List<Map<?, ?>> presets = plugin.getConfig().getMapList("presets." + type.key());
        for (int i = 0; i < presets.size() && i < SLOTS.length; i++) {
            Map<?, ?> preset = presets.get(i);
            String name = String.valueOf(preset.get("name"));
            String reason = preset.containsKey("reason") ? String.valueOf(preset.get("reason")) : name;
            String rawDuration = preset.containsKey("duration") ? String.valueOf(preset.get("duration")) : "perm";
            Long parsed = Durations.parse(rawDuration);
            long duration = parsed == null ? Durations.PERMANENT : parsed;
            Material icon = preset.containsKey("icon") ? Material.matchMaterial(String.valueOf(preset.get("icon"))) : null;

            List<String> lore = new ArrayList<>();
            lore.add("<gray>Reason: <white><reason>");
            if (type.timed()) lore.add("<gray>Duration: <white><duration>");
            lore.add("");
            lore.add("<yellow>Click to " + type.display().toLowerCase());

            String shown = duration == Durations.PERMANENT ? "permanent" : Durations.format(duration);
            set(SLOTS[i], item(icon == null ? type.icon() : icon, "<gold><name>", lore,
                    "name", name, "reason", reason, "duration", shown), e -> choose(reason, duration));
        }

        set(36, item(Material.ARROW, "<gray>Back", List.of()), e -> new PunishMenu(plugin, staff, target).open(staff));
        set(39, item(silent ? Material.REDSTONE_TORCH : Material.LEVER,
                "<gray>Silent: " + (silent ? "<green>on" : "<red>off"),
                List.of("<dark_gray>Silent punishments are only", "<dark_gray>shown to staff", "", "<yellow>Click to toggle")), e -> {
            silent = !silent;
            staff.playSound(staff.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            draw();
        });
        set(40, item(Material.WRITABLE_BOOK, "<aqua>Custom reason",
                        List.of("<gray>Type your own reason" + (type.timed() ? " and duration" : ""), "", "<yellow>Click to open")),
                e -> Dialogs.custom(plugin, staff, type, target, silent));
        if (staff.hasPermission("gavel.history")) {
            set(41, item(Material.BOOK, "<gray>History", List.of("<yellow>Click to see <player>'s record"), "player", target.name()),
                    e -> HistoryMenu.open(plugin, staff, target));
        }
        set(44, item(Material.BARRIER, "<red>Close", List.of()), e -> staff.closeInventory());
    }

    private void choose(String reason, long duration) {
        if (plugin.getConfig().getStringList("confirm").stream().anyMatch(type.key()::equalsIgnoreCase)) {
            Dialogs.confirm(plugin, staff, type, target, reason, duration, silent);
            return;
        }
        staff.closeInventory();
        plugin.punisher().punish(staff, staff.getName(), type, target, reason, duration, silent);
    }
}
