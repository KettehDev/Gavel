package net.hatpack.gavel;

import org.bukkit.Material;

public enum PunishType {
    WARN("Warn", "warned", Material.YELLOW_CONCRETE, false),
    MUTE("Mute", "muted", Material.ORANGE_CONCRETE, true),
    KICK("Kick", "kicked", Material.LIGHT_BLUE_CONCRETE, false),
    BAN("Ban", "banned", Material.RED_CONCRETE, true),
    BLACKLIST("Blacklist", "blacklisted", Material.BLACK_CONCRETE, false);

    private final String display;
    private final String past;
    private final Material icon;
    private final boolean timed;

    PunishType(String display, String past, Material icon, boolean timed) {
        this.display = display;
        this.past = past;
        this.icon = icon;
        this.timed = timed;
    }

    public String display() {
        return display;
    }

    public String past() {
        return past;
    }

    public Material icon() {
        return icon;
    }

    /** mutes and bans can have a duration, everything else is instant or forever */
    public boolean timed() {
        return timed;
    }

    public String key() {
        return name().toLowerCase();
    }

    public String permission() {
        return "gavel." + key();
    }
}
