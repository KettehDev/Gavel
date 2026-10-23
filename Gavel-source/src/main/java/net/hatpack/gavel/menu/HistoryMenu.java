package net.hatpack.gavel.menu;

import net.hatpack.gavel.Durations;
import net.hatpack.gavel.GavelPlugin;
import net.hatpack.gavel.Msg;
import net.hatpack.gavel.PunishType;
import net.hatpack.gavel.Punishment;
import net.hatpack.gavel.Target;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** every punishment someone ever got, newest first. shift-click lifts active ones */
public class HistoryMenu extends Menu {

    private static final int PER_PAGE = 45;

    private final GavelPlugin plugin;
    private final Player viewer;
    private final Target target;
    private final List<Punishment> entries;
    private int page;

    private HistoryMenu(GavelPlugin plugin, Player viewer, Target target, List<Punishment> entries) {
        super(6, Msg.parse("<dark_gray>History » <player>", "player", target.name()));
        this.plugin = plugin;
        this.viewer = viewer;
        this.target = target;
        this.entries = entries;
        draw();
    }

    public static void open(GavelPlugin plugin, Player viewer, Target target) {
        plugin.async(() -> plugin.db().history(target.uuid()),
                list -> new HistoryMenu(plugin, viewer, target, list).open(viewer), viewer);
    }

    private void draw() {
        clear();
        fill(45, 53);

        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < entries.size(); i++) {
            Punishment p = entries.get(start + i);
            set(i, icon(p), e -> {
                if (e.isShiftClick() && p.inEffect() && canRemove(p.type())) remove(p);
            });
        }
        if (entries.isEmpty()) {
            set(22, item(Material.LIME_DYE, "<green>Clean record", List.of("<gray><player> has never been punished"), "player", target.name()));
        }

        long warns = entries.stream().filter(p -> p.type() == PunishType.WARN && p.active()).count();
        long total = entries.size();
        set(49, item(Material.PAPER, "<white><player>", List.of(
                "<gray>Punishments: <white>" + total,
                "<gray>Active warnings: <white>" + warns), "player", target.name()));

        if (page > 0) set(45, item(Material.ARROW, "<yellow>Previous page", List.of()), e -> {
            page--;
            draw();
        });
        if (start + PER_PAGE < entries.size()) set(53, item(Material.ARROW, "<yellow>Next page", List.of()), e -> {
            page++;
            draw();
        });
        set(48, item(Material.OAK_DOOR, "<gray>Punish menu", List.of()), e -> new PunishMenu(plugin, viewer, target).open(viewer));
    }

    private org.bukkit.inventory.ItemStack icon(Punishment p) {
        String status;
        if (p.inEffect()) status = "<green>Active";
        else if (p.type() == PunishType.KICK) status = "<gray>-";
        else if (p.removedBy() != null && !"Expired".equals(p.removedBy())) status = "<red>Removed by " + safe(p.removedBy());
        else status = "<gray>Expired";

        List<String> lore = new ArrayList<>();
        lore.add("<gray>Reason: <white><reason>");
        lore.add("<gray>By: <white><staff>");
        lore.add("<gray>Date: <white><date>");
        if (p.type().timed()) lore.add("<gray>Length: <white><length>");
        if (p.inEffect() && p.type().timed()) lore.add("<gray>Ends in: <white><remaining>");
        lore.add("<gray>Status: " + status);
        if (p.removedReason() != null && p.removedBy() != null && !"Expired".equals(p.removedBy())) {
            lore.add("<gray>Removal reason: <white><removed>");
        }
        if (p.inEffect() && canRemove(p.type())) {
            lore.add("");
            lore.add("<yellow>Shift-click to remove");
        }

        String length = p.permanent() ? "permanent" : Durations.format(p.expires() - p.created());
        Material type = p.inEffect() ? p.type().icon() : Material.GRAY_CONCRETE;
        var item = item(type, "<white>#<id> <gold><type>", lore,
                "id", String.valueOf(p.id()),
                "type", p.type().display(),
                "reason", p.reason(),
                "staff", p.staffName(),
                "date", plugin.punisher().date(p.created()),
                "length", length,
                "remaining", p.permanent() ? "never" : Durations.format(p.remaining()),
                "removed", p.removedReason());
        if (p.inEffect()) {
            var meta = item.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean canRemove(PunishType type) {
        return switch (type) {
            case WARN -> viewer.hasPermission("gavel.unwarn");
            case MUTE -> viewer.hasPermission("gavel.unmute");
            case BAN -> viewer.hasPermission("gavel.unban");
            case BLACKLIST -> viewer.hasPermission("gavel.unblacklist");
            case KICK -> false;
        };
    }

    private void remove(Punishment p) {
        plugin.async(() -> plugin.db().remove(p.id(), viewer.getName(), "Removed from history menu"), ok -> {
            if (ok) {
                plugin.punisher().afterRemove(p.type(), p.uuid());
                plugin.punisher().notifyStaff(Msg.get("notify-removed", "staff", viewer.getName(),
                        "player", p.name(), "type", p.type().display().toLowerCase()));
            }
            open(plugin, viewer, target);
        }, viewer);
    }

    /** names in lore go through minimessage as raw text, keep tags out of them */
    private static String safe(String s) {
        return s.replace("<", "").replace(">", "");
    }
}
