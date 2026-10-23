package net.hatpack.gavel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/** reads messages out of config.yml. placeholders are always pairs: name, value */
public final class Msg {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static GavelPlugin plugin;

    private Msg() {
    }

    static void init(GavelPlugin p) {
        plugin = p;
    }

    private static FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public static TagResolver tags(String... placeholders) {
        TagResolver.Builder b = TagResolver.builder();
        b.resolver(Placeholder.parsed("prefix", cfg().getString("prefix", "")));
        b.resolver(Placeholder.unparsed("appeal", cfg().getString("appeal", "")));
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            // unparsed so a reason like "<red>lol" can't inject formatting
            b.resolver(Placeholder.unparsed(placeholders[i], placeholders[i + 1] == null ? "" : placeholders[i + 1]));
        }
        return b.build();
    }

    public static Component parse(String raw, String... placeholders) {
        return MM.deserialize(raw, tags(placeholders));
    }

    /** for item names/lore, which are italic by default */
    public static Component item(String raw, String... placeholders) {
        return parse(raw, placeholders).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** single message or a list of lines, whichever the config has */
    public static Component get(String key, String... placeholders) {
        String path = "messages." + key;
        if (cfg().isList(path)) return lines(cfg().getStringList(path), placeholders);
        return parse(cfg().getString(path, key), placeholders);
    }

    public static Component lines(List<String> lines, String... placeholders) {
        Component out = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) out = out.appendNewline();
            out = out.append(parse(lines.get(i), placeholders));
        }
        return out;
    }

    public static Component screen(String key, String... placeholders) {
        return lines(cfg().getStringList("screens." + key), placeholders);
    }

    public static void send(CommandSender to, String key, String... placeholders) {
        to.sendMessage(get(key, placeholders));
    }
}
