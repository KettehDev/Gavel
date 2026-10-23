package net.hatpack.gavel;

import java.util.UUID;

public record Punishment(
        int id,
        PunishType type,
        UUID uuid,
        String name,
        String ip,
        String reason,
        String staffName,
        long created,
        long expires,
        boolean active,
        String removedBy,
        String removedReason
) {

    public boolean permanent() {
        return expires == 0;
    }

    public boolean expired() {
        return expires != 0 && System.currentTimeMillis() >= expires;
    }

    public boolean inEffect() {
        return active && !expired();
    }

    public long remaining() {
        return permanent() ? 0 : Math.max(0, expires - System.currentTimeMillis());
    }

    public Punishment withId(int id) {
        return new Punishment(id, type, uuid, name, ip, reason, staffName, created, expires, active, removedBy, removedReason);
    }
}
