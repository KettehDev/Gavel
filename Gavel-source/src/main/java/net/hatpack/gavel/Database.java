package net.hatpack.gavel;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SQLite storage. One connection, every method synchronized, call it off the
 * main thread (see {@link GavelPlugin#async}).
 */
public final class Database {

    private Connection conn;

    public void open(File file) throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
            // paper ships the driver, DriverManager will complain if it really isn't there
        }
        conn = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS punishments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        type TEXT NOT NULL,
                        uuid TEXT NOT NULL,
                        name TEXT NOT NULL,
                        ip TEXT,
                        reason TEXT NOT NULL,
                        staff TEXT NOT NULL,
                        created INTEGER NOT NULL,
                        expires INTEGER NOT NULL,
                        active INTEGER NOT NULL,
                        removed_by TEXT,
                        removed_reason TEXT
                    )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_p_uuid ON punishments(uuid)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_p_ip ON punishments(ip)");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS players (
                        uuid TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        ip TEXT,
                        last_seen INTEGER NOT NULL
                    )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_pl_name ON players(name COLLATE NOCASE)");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS ips (
                        uuid TEXT NOT NULL,
                        ip TEXT NOT NULL,
                        PRIMARY KEY (uuid, ip)
                    )""");
        }
    }

    public synchronized void close() {
        try {
            if (conn != null) conn.close();
        } catch (SQLException ignored) {
        }
    }

    // --- punishments ---

    public synchronized Punishment insert(Punishment p) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO punishments (type, uuid, name, ip, reason, staff, created, expires, active) VALUES (?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, p.type().name());
            ps.setString(2, p.uuid().toString());
            ps.setString(3, p.name());
            ps.setString(4, p.ip());
            ps.setString(5, p.reason());
            ps.setString(6, p.staffName());
            ps.setLong(7, p.created());
            ps.setLong(8, p.expires());
            ps.setInt(9, p.active() ? 1 : 0);
            ps.executeUpdate();
        }
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            return p.withId(rs.getInt(1));
        }
    }

    /** the punishment of this type that's currently in effect, expired ones get cleaned up on the way */
    public synchronized Punishment active(UUID uuid, PunishType type) throws SQLException {
        return firstActive("SELECT * FROM punishments WHERE uuid = ? AND type = ? AND active = 1 ORDER BY id DESC",
                uuid.toString(), type.name());
    }

    public synchronized Punishment activeByIp(String ip, PunishType type) throws SQLException {
        if (ip == null) return null;
        return firstActive("SELECT * FROM punishments WHERE ip = ? AND type = ? AND active = 1 ORDER BY id DESC",
                ip, type.name());
    }

    private Punishment firstActive(String sql, String a, String b) throws SQLException {
        for (Punishment p : query(sql, a, b)) {
            if (!p.expired()) return p;
            remove(p.id(), "Expired", null);
        }
        return null;
    }

    public synchronized List<Punishment> history(UUID uuid) throws SQLException {
        return query("SELECT * FROM punishments WHERE uuid = ? ORDER BY id DESC", uuid.toString());
    }

    public synchronized Punishment byId(int id) throws SQLException {
        List<Punishment> list = query("SELECT * FROM punishments WHERE id = ?", String.valueOf(id));
        return list.isEmpty() ? null : list.get(0);
    }

    public synchronized boolean remove(int id, String by, String reason) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE punishments SET active = 0, removed_by = ?, removed_reason = ? WHERE id = ? AND active = 1")) {
            ps.setString(1, by);
            ps.setString(2, reason);
            ps.setInt(3, id);
            return ps.executeUpdate() > 0;
        }
    }

    /** lifts every active punishment of a type, returns how many there were */
    public synchronized int removeAll(UUID uuid, PunishType type, String by, String reason) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE punishments SET active = 0, removed_by = ?, removed_reason = ? WHERE uuid = ? AND type = ? AND active = 1")) {
            ps.setString(1, by);
            ps.setString(2, reason);
            ps.setString(3, uuid.toString());
            ps.setString(4, type.name());
            return ps.executeUpdate();
        }
    }

    public synchronized int activeWarnings(UUID uuid) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM punishments WHERE uuid = ? AND type = 'WARN' AND active = 1")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private List<Punishment> query(String sql, String... args) throws SQLException {
        List<Punishment> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setString(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Punishment(
                            rs.getInt("id"),
                            PunishType.valueOf(rs.getString("type")),
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("name"),
                            rs.getString("ip"),
                            rs.getString("reason"),
                            rs.getString("staff"),
                            rs.getLong("created"),
                            rs.getLong("expires"),
                            rs.getInt("active") == 1,
                            rs.getString("removed_by"),
                            rs.getString("removed_reason")));
                }
            }
        }
        return out;
    }

    // --- players ---

    public synchronized void seen(UUID uuid, String name, String ip) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO players (uuid, name, ip, last_seen) VALUES (?,?,?,?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, ip = excluded.ip, last_seen = excluded.last_seen")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, ip);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        }
        if (ip != null) {
            try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO ips (uuid, ip) VALUES (?,?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, ip);
                ps.executeUpdate();
            }
        }
    }

    public synchronized Target findPlayer(String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT uuid, name, ip FROM players WHERE name = ? COLLATE NOCASE ORDER BY last_seen DESC LIMIT 1")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new Target(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3)) : null;
            }
        }
    }

    /** other accounts that ever joined from one of this player's ips */
    public synchronized List<Target> alts(UUID uuid) throws SQLException {
        List<Target> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT DISTINCT p.uuid, p.name, p.ip FROM ips a
                JOIN ips b ON a.ip = b.ip AND b.uuid != a.uuid
                JOIN players p ON p.uuid = b.uuid
                WHERE a.uuid = ?
                ORDER BY p.last_seen DESC""")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new Target(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3)));
            }
        }
        return out;
    }

    public synchronized List<String> names(String prefix, int limit) throws SQLException {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name FROM players WHERE name LIKE ? ORDER BY last_seen DESC LIMIT ?")) {
            ps.setString(1, prefix.replace("%", "") + "%");
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        }
        return out;
    }
}
