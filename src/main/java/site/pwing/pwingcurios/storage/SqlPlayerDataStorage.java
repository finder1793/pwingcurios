package site.pwing.pwingcurios.storage;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import site.pwing.pwingcurios.Pwingcurios;

import java.io.*;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SqlPlayerDataStorage implements PlayerDataStorage {
    private final Pwingcurios plugin;
    private final File dbFile;
    private Connection conn;

    public SqlPlayerDataStorage(Pwingcurios plugin, File dbFile) {
        this.plugin = plugin;
        this.dbFile = dbFile;
    }

    @Override
    public void init() throws Exception {
        if (dbFile.getParentFile() != null && !dbFile.getParentFile().exists()) {
            dbFile.getParentFile().mkdirs();
        }
        Class.forName("org.sqlite.JDBC");
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS curios_equipment (uuid TEXT NOT NULL, slot TEXT NOT NULL, item BLOB, PRIMARY KEY(uuid, slot))");
        }
    }

    @Override
    public void close() {
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    private byte[] serialize(ItemStack item) throws IOException {
        if (item == null) return null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos)) {
            oos.writeObject(item);
        }
        return baos.toByteArray();
    }

    private ItemStack deserialize(byte[] data) throws IOException, ClassNotFoundException {
        if (data == null) return null;
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        try (BukkitObjectInputStream ois = new BukkitObjectInputStream(bais)) {
            Object obj = ois.readObject();
            return (ItemStack) obj;
        }
    }

    @Override
    public void savePlayer(UUID uuid, Map<String, ItemStack> map) throws Exception {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement("REPLACE INTO curios_equipment(uuid, slot, item) VALUES(?,?,?)")) {
            if (map == null || map.isEmpty()) {
                // nothing to insert (we keep any existing rows as-is due to primary key; optional: delete rows)
            } else {
                for (Map.Entry<String, ItemStack> e : map.entrySet()) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, e.getKey());
                    ItemStack it = e.getValue();
                    byte[] blob = it == null ? null : serialize(it);
                    if (blob == null) {
                        ps.setNull(3, Types.BLOB);
                    } else {
                        ps.setBytes(3, blob);
                    }
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    @Override
    public Map<String, ItemStack> loadPlayer(UUID uuid) throws Exception {
        Map<String, ItemStack> map = new HashMap<>();
        if (conn == null) return map;
        try (PreparedStatement ps = conn.prepareStatement("SELECT slot, item FROM curios_equipment WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String slotName = rs.getString(1);
                    byte[] blob = rs.getBytes(2);
                    ItemStack it = deserialize(blob);
                    if (it != null) {
                        map.put(slotName, it);
                    }
                }
            }
        }
        return map;
    }

    @Override
    public void saveAll(Map<UUID, Map<String, ItemStack>> all) throws Exception {
        if (all == null) return;
        for (Map.Entry<UUID, Map<String, ItemStack>> e : all.entrySet()) {
            savePlayer(e.getKey(), e.getValue());
        }
    }
}
