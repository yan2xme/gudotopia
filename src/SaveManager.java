import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * The database service: ALL SQL in the game lives in this class.
 *
 * Storage: a local SQLite database file, gudotopia.db, accessed with JDBC (no server needed).
 * Default location:  <your home folder>/.gudotopia/gudotopia.db
 *
 * Every public method opens its own short-lived connection, does its work inside ONE transaction
 * (all-or-nothing: commit on success, rollback on error) and closes the connection again.
 * Each save slot is a row in "saves"; every other table points to it with a save_id foreign key,
 * which is what keeps different saves completely separate.
 */
public class SaveManager {
    public static final String DB_FILE_NAME = "gudotopia.db";
    public static final int MAX_NAME_LENGTH = 20;
    private static final String DEFAULT_LEGACY_NAME = "Migrated Garden";
    private static final Pattern NAME_PATTERN = Pattern.compile("[\\p{L}\\p{N} _'.-]+");

    private final Path dir;
    private final Path dbFile;
    private final Path legacyFile;
    private String startupMessage;       // result of the one-time migration, shown to the player once

    /** Folder that holds the database: ~/.gudotopia (or -Dgudotopia.dir=... for experiments). */
    public static Path defaultDir() {
        String override = System.getProperty("gudotopia.dir");
        return override != null ? Paths.get(override) : Paths.get(System.getProperty("user.home"), ".gudotopia");
    }

    public SaveManager() { this(defaultDir()); }

    /** Opens (and if needed creates) the database inside the given folder. */
    public SaveManager(Path dir) {
        this.dir = dir;
        this.dbFile = dir.resolve(DB_FILE_NAME);
        this.legacyFile = dir.resolve("save.properties");
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new SaveException("Could not create the save folder " + dir + ": " + e.getMessage(), e);
        }
        inTransaction(c -> { createSchema(c); syncDefinitions(c); return null; });
        migrateLegacyIfNeeded();
    }

    public Path getDatabaseFile() { return dbFile; }
    /** A message about the old-save import (success or failure), or null. Cleared after it is read. */
    public String takeStartupMessage() { String m = startupMessage; startupMessage = null; return m; }

    // =====================================================================================================
    //  Connection + transaction helpers
    // =====================================================================================================

    /** Opens a connection with foreign-key checking switched on (SQLite has it off by default). */
    public Connection open() throws SQLException {
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON");
            s.execute("PRAGMA busy_timeout = 3000");
        } catch (SQLException e) { c.close(); throw e; }
        return c;
    }

    private interface SqlWork<T> { T run(Connection c) throws SQLException; }

    /** Runs the work as one transaction: commit if it succeeds, roll everything back if anything fails. */
    private <T> T inTransaction(SqlWork<T> work) {
        try (Connection c = open()) {                    // try-with-resources always closes the connection
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                try { c.rollback(); } catch (SQLException ignored) { }
                throw e;
            }
        } catch (SQLException e) {
            throw new SaveException("Database error: " + e.getMessage(), e);
        }
    }

    // =====================================================================================================
    //  Schema (CREATE TABLE IF NOT EXISTS = safe to run on every start)
    // =====================================================================================================

    private static void createSchema(Connection c) throws SQLException {
        String[] ddl = {
            // --- definitions (what flowers / fertilizers exist) ---
            "CREATE TABLE IF NOT EXISTS flowers (" +
            "  flower_id TEXT PRIMARY KEY, name TEXT NOT NULL, description TEXT, rarity TEXT NOT NULL," +
            "  reward INTEGER NOT NULL, growth_seconds INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS fertilizers (" +
            "  fertilizer_id TEXT PRIMARY KEY, name TEXT NOT NULL, description TEXT, rarity TEXT NOT NULL," +
            "  price INTEGER NOT NULL, growth_bonus REAL NOT NULL)",

            // --- one row per save slot ---
            "CREATE TABLE IF NOT EXISTS saves (" +
            "  save_id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  name TEXT NOT NULL UNIQUE COLLATE NOCASE," +                  // no two saves may share a name (any letter case)
            "  created_at INTEGER NOT NULL, last_played INTEGER NOT NULL)",

            // --- everything below belongs to exactly one save (save_id) ---
            "CREATE TABLE IF NOT EXISTS player_data (" +
            "  save_id INTEGER PRIMARY KEY REFERENCES saves(save_id) ON DELETE CASCADE," +
            "  money INTEGER NOT NULL CHECK (money >= 0)," +
            "  garden_size INTEGER NOT NULL CHECK (garden_size BETWEEN 3 AND 6))",
            "CREATE TABLE IF NOT EXISTS garden_plots (" +
            "  save_id INTEGER NOT NULL REFERENCES saves(save_id) ON DELETE CASCADE," +
            "  plot_row INTEGER NOT NULL, plot_col INTEGER NOT NULL," +
            "  watered INTEGER NOT NULL DEFAULT 0," +
            "  flower_id TEXT REFERENCES flowers(flower_id)," +
            "  growth_ms INTEGER NOT NULL DEFAULT 0," +                      // accumulated growth progress
            "  growth_updated_ms INTEGER NOT NULL DEFAULT 0," +              // timestamp the progress was calculated at
            "  death_deadline_ms INTEGER," +                                 // when a fully grown flower dies (NULL = not ripe)
            "  fertilizer_id TEXT REFERENCES fertilizers(fertilizer_id)," +  // active fertilizer effect
            "  PRIMARY KEY (save_id, plot_row, plot_col))",
            "CREATE TABLE IF NOT EXISTS seed_inventory (" +
            "  save_id INTEGER NOT NULL REFERENCES saves(save_id) ON DELETE CASCADE," +
            "  flower_id TEXT NOT NULL REFERENCES flowers(flower_id)," +
            "  quantity INTEGER NOT NULL CHECK (quantity >= 0)," +
            "  PRIMARY KEY (save_id, flower_id))",
            "CREATE TABLE IF NOT EXISTS fertilizer_inventory (" +
            "  save_id INTEGER NOT NULL REFERENCES saves(save_id) ON DELETE CASCADE," +
            "  fertilizer_id TEXT NOT NULL REFERENCES fertilizers(fertilizer_id)," +
            "  quantity INTEGER NOT NULL CHECK (quantity >= 0)," +
            "  PRIMARY KEY (save_id, fertilizer_id))",
            "CREATE TABLE IF NOT EXISTS flower_collection (" +
            "  save_id INTEGER NOT NULL REFERENCES saves(save_id) ON DELETE CASCADE," +
            "  flower_id TEXT NOT NULL REFERENCES flowers(flower_id)," +
            "  unlocked_at INTEGER NOT NULL," +
            "  PRIMARY KEY (save_id, flower_id))",

            // --- remembers which old save.properties files were already imported ---
            "CREATE TABLE IF NOT EXISTS migration_log (" +
            "  source TEXT PRIMARY KEY, migrated_at INTEGER NOT NULL," +
            "  save_id INTEGER REFERENCES saves(save_id))"
        };
        try (Statement s = c.createStatement()) {
            for (String sql : ddl) s.execute(sql);
        }
    }

    /**
     * Copies the flower and fertilizer definitions into their tables. "ON CONFLICT ... DO UPDATE" (an UPSERT)
     * means a definition that already exists is updated instead of duplicated, so running this on every
     * start never creates duplicate rows.
     */
    private static void syncDefinitions(Connection c) throws SQLException {
        String flowerSql = "INSERT INTO flowers(flower_id, name, description, rarity, reward, growth_seconds) VALUES (?,?,?,?,?,?) " +
                "ON CONFLICT(flower_id) DO UPDATE SET name=excluded.name, description=excluded.description, " +
                "rarity=excluded.rarity, reward=excluded.reward, growth_seconds=excluded.growth_seconds";
        try (PreparedStatement ps = c.prepareStatement(flowerSql)) {
            for (Flower f : FlowerRegistry.ALL) {
                ps.setString(1, f.id); ps.setString(2, f.name); ps.setString(3, f.description);
                ps.setString(4, f.rarity.label); ps.setInt(5, f.reward); ps.setInt(6, f.growthSeconds);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        String fertSql = "INSERT INTO fertilizers(fertilizer_id, name, description, rarity, price, growth_bonus) VALUES (?,?,?,?,?,?) " +
                "ON CONFLICT(fertilizer_id) DO UPDATE SET name=excluded.name, description=excluded.description, " +
                "rarity=excluded.rarity, price=excluded.price, growth_bonus=excluded.growth_bonus";
        try (PreparedStatement ps = c.prepareStatement(fertSql)) {
            for (Fertilizer f : FertilizerRegistry.ALL) {
                ps.setString(1, f.id); ps.setString(2, f.name); ps.setString(3, f.description);
                ps.setString(4, f.rarity.label); ps.setInt(5, f.price); ps.setDouble(6, f.growthBonus);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // =====================================================================================================
    //  Save names
    // =====================================================================================================

    /** Returns a friendly error message if the name is not allowed, or null if it is fine. */
    public static String validateName(String raw) {
        String name = cleanName(raw);
        if (name.isEmpty()) return "Please type a name for your garden.";
        if (name.length() > MAX_NAME_LENGTH) return "That name is too long (max " + MAX_NAME_LENGTH + " characters).";
        if (!NAME_PATTERN.matcher(name).matches()) return "Use only letters, numbers, spaces and - _ . ' in the name.";
        return null;
    }

    /** Trims the name and squeezes repeated spaces. */
    public static String cleanName(String raw) { return raw == null ? "" : raw.trim().replaceAll("\\s+", " "); }

    public boolean nameExists(String name) {
        return inTransaction(c -> nameExists(c, cleanName(name)));
    }

    private static boolean nameExists(Connection c, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM saves WHERE name = ?")) {   // column is COLLATE NOCASE
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    // =====================================================================================================
    //  Listing, creating, loading, saving
    // =====================================================================================================

    /** All save slots, most recently played first (feeds the LOAD screen). */
    public List<SaveInfo> listSaves() {
        String sql = "SELECT s.save_id, s.name, s.created_at, s.last_played, p.money, p.garden_size, " +
                     "       (SELECT COUNT(*) FROM flower_collection fc WHERE fc.save_id = s.save_id) AS discovered " +
                     "FROM saves s JOIN player_data p ON p.save_id = s.save_id " +
                     "ORDER BY s.last_played DESC, s.save_id DESC";
        return inTransaction(c -> {
            List<SaveInfo> list = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                while (rs.next())
                    list.add(new SaveInfo(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getLong(4),
                                          rs.getInt(5), rs.getInt(6), rs.getInt(7)));
            }
            return list;
        });
    }

    /** NEW GAME: inserts a brand-new save with starting money, an empty garden and empty inventories. */
    public PlayerData createSave(String rawName) {
        String error = validateName(rawName);
        if (error != null) throw new SaveException(error);
        String name = cleanName(rawName);
        return inTransaction(c -> {
            if (nameExists(c, name))
                throw new SaveException("A save called \"" + name + "\" already exists. Please choose another name.");
            long now = System.currentTimeMillis();
            PlayerData d = new PlayerData();
            long id = insertSaveRows(c, name, now);
            d.setSaveInfo(id, name);
            writeState(c, d, now);
            return d;
        });
    }

    /** INSERTs the rows every save needs: the save itself, its player_data row and one row per plot (6x6). */
    private static long insertSaveRows(Connection c, String name, long now) throws SQLException {
        long id;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO saves(name, created_at, last_played) VALUES (?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name); ps.setLong(2, now); ps.setLong(3, now);
            try { ps.executeUpdate(); }
            catch (SQLException e) {
                if (e.getMessage() != null && e.getMessage().contains("UNIQUE"))
                    throw new SaveException("A save called \"" + name + "\" already exists. Please choose another name.");
                throw e;
            }
            try (ResultSet keys = ps.getGeneratedKeys()) { keys.next(); id = keys.getLong(1); }
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO player_data(save_id, money, garden_size) VALUES (?,?,?)")) {
            ps.setLong(1, id); ps.setInt(2, PlayerData.START_MONEY); ps.setInt(3, PlayerData.START_SIZE);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO garden_plots(save_id, plot_row, plot_col) VALUES (?,?,?)")) {
            for (int r = 0; r < PlayerData.MAX_SIZE; r++)
                for (int col = 0; col < PlayerData.MAX_SIZE; col++) {
                    ps.setLong(1, id); ps.setInt(2, r); ps.setInt(3, col);
                    ps.addBatch();
                }
            ps.executeBatch();
        }
        return id;
    }

    /** LOAD: reads every row that belongs to the given save id and rebuilds the game state. */
    public PlayerData loadSave(long saveId) {
        PlayerData d = inTransaction(c -> {
            PlayerData data = new PlayerData();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT s.name, p.money, p.garden_size FROM saves s JOIN player_data p ON p.save_id = s.save_id WHERE s.save_id = ?")) {
                ps.setLong(1, saveId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SaveException("That save no longer exists.");
                    data.setSaveInfo(saveId, rs.getString(1));
                    data.setMoney(rs.getInt(2));
                    data.setGardenSize(rs.getInt(3));
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT plot_row, plot_col, watered, flower_id, growth_ms, growth_updated_ms, death_deadline_ms, fertilizer_id " +
                    "FROM garden_plots WHERE save_id = ?")) {
                ps.setLong(1, saveId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int r = rs.getInt(1), col = rs.getInt(2);
                        if (r >= PlayerData.MAX_SIZE || col >= PlayerData.MAX_SIZE) continue;
                        data.getPlot(r, col).restore(rs.getInt(3) != 0, FlowerRegistry.byId(rs.getString(4)),
                                FertilizerRegistry.byId(rs.getString(8)), rs.getLong(5), rs.getLong(6), rs.getLong(7));
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT flower_id, quantity FROM seed_inventory WHERE save_id = ? AND quantity > 0")) {
                ps.setLong(1, saveId);
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()) data.getSeeds().add(rs.getString(1), rs.getInt(2)); }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT fertilizer_id, quantity FROM fertilizer_inventory WHERE save_id = ? AND quantity > 0")) {
                ps.setLong(1, saveId);
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()) data.getFertilizers().add(rs.getString(1), rs.getInt(2)); }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT flower_id FROM flower_collection WHERE save_id = ?")) {
                ps.setLong(1, saveId);
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()) data.unlock(rs.getString(1)); }
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE saves SET last_played = ? WHERE save_id = ?")) {
                ps.setLong(1, System.currentTimeMillis()); ps.setLong(2, saveId);
                ps.executeUpdate();
            }
            return data;
        });
        d.update();   // apply the time that passed while the game was closed (growth, ripening, death)
        return d;
    }

    /** SAVE: writes the whole current game state of one save slot, as a single transaction. */
    public void save(PlayerData d) {
        if (d.getSaveId() <= 0) throw new SaveException("This game has no save slot to write to.");
        d.update();                                   // make sure timestamps/growth are current first
        inTransaction(c -> { writeState(c, d, System.currentTimeMillis()); return null; });
    }

    /** UPDATE / UPSERT statements that copy the in-memory game into the database rows of this save. */
    private static void writeState(Connection c, PlayerData d, long now) throws SQLException {
        long id = d.getSaveId();

        try (PreparedStatement ps = c.prepareStatement("UPDATE saves SET last_played = ? WHERE save_id = ?")) {
            ps.setLong(1, now); ps.setLong(2, id);
            if (ps.executeUpdate() != 1) throw new SaveException("Save slot " + id + " was not found in the database.");
        }
        try (PreparedStatement ps = c.prepareStatement("UPDATE player_data SET money = ?, garden_size = ? WHERE save_id = ?")) {
            ps.setInt(1, d.getMoney()); ps.setInt(2, d.getGardenSize()); ps.setLong(3, id);
            if (ps.executeUpdate() != 1) throw new SaveException("Player data row for save " + id + " is missing.");
        }
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE garden_plots SET watered = ?, flower_id = ?, growth_ms = ?, growth_updated_ms = ?, " +
                "death_deadline_ms = ?, fertilizer_id = ? WHERE save_id = ? AND plot_row = ? AND plot_col = ?")) {
            for (int r = 0; r < PlayerData.MAX_SIZE; r++)
                for (int col = 0; col < PlayerData.MAX_SIZE; col++) {
                    GardenPlot p = d.getPlot(r, col);
                    ps.setInt(1, p.isWatered() ? 1 : 0);
                    ps.setString(2, p.isEmpty() ? null : p.getFlower().id);
                    ps.setLong(3, p.getGrowthMs());
                    ps.setLong(4, p.getUpdatedMs());
                    if (p.getDeathDeadlineMs() > 0) ps.setLong(5, p.getDeathDeadlineMs()); else ps.setNull(5, Types.INTEGER);
                    ps.setString(6, p.getFertilizer() == null ? null : p.getFertilizer().id);
                    ps.setLong(7, id); ps.setInt(8, r); ps.setInt(9, col);
                    ps.addBatch();
                }
            for (int n : ps.executeBatch()) if (n != 1) throw new SaveException("A garden plot row is missing for save " + id + ".");
        }
        // Inventories: set every old row to 0, then UPSERT what the player really owns.
        writeInventory(c, "seed_inventory", "flower_id", id, d.getSeeds().asMap());
        writeInventory(c, "fertilizer_inventory", "fertilizer_id", id, d.getFertilizers().asMap());
        // Collection: INSERT OR IGNORE keeps the first discovery time and never duplicates a row.
        try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO flower_collection(save_id, flower_id, unlocked_at) VALUES (?,?,?)")) {
            for (String flowerId : d.getUnlockedIds()) {
                ps.setLong(1, id); ps.setString(2, flowerId); ps.setLong(3, now);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void writeInventory(Connection c, String table, String idColumn, long saveId, Map<String, Integer> items) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE " + table + " SET quantity = 0 WHERE save_id = ?")) {   // table/column names are fixed constants
            ps.setLong(1, saveId);
            ps.executeUpdate();
        }
        String upsert = "INSERT INTO " + table + "(save_id, " + idColumn + ", quantity) VALUES (?,?,?) " +
                        "ON CONFLICT(save_id, " + idColumn + ") DO UPDATE SET quantity = excluded.quantity";
        try (PreparedStatement ps = c.prepareStatement(upsert)) {
            for (Map.Entry<String, Integer> e : items.entrySet()) {
                ps.setLong(1, saveId); ps.setString(2, e.getKey()); ps.setInt(3, e.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // =====================================================================================================
    //  One-time migration from the old save.properties
    // =====================================================================================================

    /**
     * Imports ~/.gudotopia/save.properties into SQLite - but only when
     *   (1) the old file exists, (2) the database has no saves yet and (3) this file was never imported before.
     * It never overwrites an existing SQLite save. After a successful import the old file is renamed to
     * save.properties.migrated.bak (kept as a backup, and never imported twice). On failure the original
     * file is left exactly as it was and a message is stored for the player.
     */
    private void migrateLegacyIfNeeded() {
        if (!Files.isRegularFile(legacyFile)) return;
        String source = legacyFile.toAbsolutePath().toString();
        try {
            String importedAs = inTransaction(c -> {
                if (count(c, "SELECT COUNT(*) FROM saves", null) > 0) return null;                          // database already has saves
                if (count(c, "SELECT COUNT(*) FROM migration_log WHERE source = ?", source) > 0) return null; // imported before
                long now = System.currentTimeMillis();
                PlayerData d;
                try { d = LegacyMigrator.read(legacyFile, now); }
                catch (Exception e) { throw new SaveException("the old save file could not be read (" + e + ")", e); }
                String name = DEFAULT_LEGACY_NAME;
                for (int i = 2; nameExists(c, name); i++) name = DEFAULT_LEGACY_NAME + " " + i;
                long id = insertSaveRows(c, name, now);
                d.setSaveInfo(id, name);
                writeState(c, d, now);
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO migration_log(source, migrated_at, save_id) VALUES (?,?,?)")) {
                    ps.setString(1, source); ps.setLong(2, now); ps.setLong(3, id);
                    ps.executeUpdate();
                }
                return name;
            });
            if (importedAs != null) {
                Path backup = legacyFile.resolveSibling("save.properties.migrated.bak");
                try {
                    Files.move(legacyFile, backup, StandardCopyOption.REPLACE_EXISTING);
                    startupMessage = "Your old save was imported into the new database as \"" + importedAs + "\".\nThe original file was kept as " + backup.getFileName() + ".";
                } catch (Exception e) {
                    startupMessage = "Your old save was imported as \"" + importedAs + "\", but the old file could not be renamed: " + e.getMessage();
                }
            }
        } catch (Exception e) {
            startupMessage = "Could not import your old save: " + e.getMessage() + "\nThe original file was NOT changed: " + legacyFile;
            System.err.println(startupMessage);
        }
    }

    private static int count(Connection c, String sql, String param) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (param != null) ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }
}
