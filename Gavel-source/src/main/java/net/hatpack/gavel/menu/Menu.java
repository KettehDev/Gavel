package net.hatpack.gavel.menu;

import net.hatpack.gavel.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** chest gui where every slot can have a click handler */
public class Menu implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    public Menu(int rows, Component title) {
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    public void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inventory.setItem(slot, item);
        if (action == null) actions.remove(slot);
        else actions.put(slot, action);
    }

    public void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    public void clear() {
        inventory.clear();
        actions.clear();
    }

    /** puts the filler in every slot from {@code from} to {@code to} (inclusive) */
    public void fill(int from, int to) {
        ItemStack glass = glass();
        for (int i = from; i <= to; i++) set(i, glass);
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    void click(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> action = actions.get(event.getSlot());
        if (action != null) action.accept(event);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static ItemStack item(Material type, String name, List<String> lore, String... placeholders) {
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(Msg.item(name, placeholders));
        meta.lore(lore.stream().map(line -> Msg.item(line, placeholders)).toList());
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack glass() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setHideTooltip(true);
        item.setItemMeta(meta);
        return item;
    }
}
