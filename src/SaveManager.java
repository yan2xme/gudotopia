import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * The save service (a SINGLETON - see getInstance()): ALL reading and writing of save files happens here.
 *
 * Storage: plain text files, one per save slot, in   <your home folder>/.gudotopia/saves/
 *          for example   save_1.properties,  save_2.properties ...
 * Each file is a list of  key=value  lines (Java "Properties" format) that you can open in Notepad.
 * Because every save slot is its own file, saves can never mix with each other.
 *
 * Safety: a file is first written to a temporary file and only then moved over the real one, so a crash
 * in the middle of saving cannot leave a half-written save. Creating a new save never overwrites a file.
 */
public class SaveManager {
    public static final int MAX_NAME_LENGTH = 20;
    private static final String DEFAULT_LEGACY_NAME = "Migrated Garden";
    private static final Pattern NAME_PATTERN = Pattern.compile("[\\p{L}\\p{N} _'.-]+");
    private static final Pattern FILE_PATTERN = Pattern.compile("save_(\\d+)\\.properties");

    private final Path dir;          // ~/.gudotopia
    private final Path savesDir;     // ~/.gudotopia/saves
    private final Path legacyFile;   // ~/.gudotopia/save.properties (the very first, single-save format)
    private String startupMessage;   // result of the one-time import of the old file, shown to the player once

    /** Folder that holds everything: ~/.gudotopia (or -Dgudotopia.dir=... for experiments). */
    public static Path defaultDir() {
        String override = System.getProperty("gudotopia.dir");
        return override != null ? Paths.get(override) : Paths.get(System.getProperty("user.home"), ".gudotopia");
    }

    // ---- SINGLETON pattern: the whole game shares exactly ONE SaveManager ----
    private static SaveManager instance;

    /** The one shared SaveManager. It is created the first time this is called. */
    public static synchronized SaveManager getInstance() {
        if (instance == null) instance = new SaveManager(defaultDir());
        return instance;
    }

    /** Not public (Singleton). Package-private only so the tests can point it at a temporary folder. */
    SaveManager(Path dir) {
        this.dir = dir;
        this.savesDir = dir.resolve("saves");
        this.legacyFile = dir.resolve("save.properties");
        try {
            Files.createDirectories(savesDir);
        } catch (Exception e) {
            throw new SaveException("Could not create the save folder " + savesDir + ": " + e.getMessage(), e);
        }
        migrateLegacyIfNeeded();
    }

    public Path getSavesFolder() { return savesDir; }
    public Path getSaveFile(long id) { return savesDir.resolve("save_" + id + ".properties"); }
    /** A message about the old-save import (success or failure), or null. Cleared after it is read. */
    public String takeStartupMessage() { String m = startupMessage; startupMessage = null; return m; }

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

    public synchronized boolean nameExists(String name) {
        String wanted = cleanName(name);
        for (SaveInfo s : listSaves()) if (s.name().equalsIgnoreCase(wanted)) return true;
        return false;
    }

    // =====================================================================================================
    //  Listing, creating, loading, saving
    // =====================================================================================================

    /** All save slots, most recently played first (feeds the LOAD screen). Unreadable files are skipped. */
    public synchronized List<SaveInfo> listSaves() {
        List<SaveInfo> list = new ArrayList<>();
        for (long id : existingIds()) {
            try {
                Properties p = readProps(getSaveFile(id));
                String collection = p.getProperty("collection", "");
                int discovered = collection.isBlank() ? 0 : collection.split(",").length;
                list.add(new SaveInfo(id, p.getProperty("name", "?"), num(p, "createdAt", 0), num(p, "lastPlayed", 0),
                        (int) num(p, "money", 0), (int) num(p, "gardenSize", PlayerData.START_SIZE), discovered));
            } catch (Exception e) {
                System.err.println("Skipping unreadable save file " + getSaveFile(id) + ": " + e);
            }
        }
        list.sort(Comparator.comparingLong(SaveInfo::lastPlayed).reversed().thenComparing(Comparator.comparingLong(SaveInfo::id).reversed()));
        return list;
    }

    /** NEW GAME: creates a brand-new save file with starting money, an empty garden and empty inventories. */
    public synchronized PlayerData createSave(String rawName) {
        String error = validateName(rawName);
        if (error != null) throw new SaveException(error);
        String name = cleanName(rawName);
        if (nameExists(name))
            throw new SaveException("A save called \"" + name + "\" already exists. Please choose another name.");
        return createSaveFile(name, new PlayerData(), System.currentTimeMillis());
    }

    /** Picks a free id, writes the new file (CREATE_NEW: it can never overwrite an existing save). */
    private PlayerData createSaveFile(String name, PlayerData d, long now) {
        long id = 1;
        for (long existing : existingIds()) id = Math.max(id, existing + 1);
        d.setSaveInfo(id, name);
        write(getSaveFile(id), toProps(d, now, now), true);
        return d;
    }

    /** LOAD: reads one save file into a PlayerData and applies the time that passed while the game was closed. */
    public synchronized PlayerData loadSave(long saveId) {
        Path file = getSaveFile(saveId);
        if (!Files.isRegularFile(file)) throw new SaveException("That save no longer exists.");
        PlayerData d;
        try {
            Properties p = readProps(file);
            d = new PlayerData();
            d.setSaveInfo(saveId, p.getProperty("name", "Garden " + saveId));
            d.setMoney((int) num(p, "money", PlayerData.START_MONEY));
            d.setGardenSize((int) num(p, "gardenSize", PlayerData.START_SIZE));
            for (int r = 0; r < PlayerData.MAX_SIZE; r++)
                for (int c = 0; c < PlayerData.MAX_SIZE; c++) {
                    String k = "plot." + r + "." + c + ".";
                    d.getPlot(r, c).restore("1".equals(p.getProperty(k + "watered")),
                            FlowerRegistry.byId(blankToNull(p.getProperty(k + "flower"))),
                            FertilizerRegistry.byId(blankToNull(p.getProperty(k + "fertilizer"))),
                            num(p, k + "growthMs", 0), num(p, k + "updatedMs", 0), num(p, k + "deadlineMs", 0));
                }
            for (String key : p.stringPropertyNames()) {
                if (key.startsWith("seed.")) d.getSeeds().add(key.substring(5), (int) num(p, key, 0));
                else if (key.startsWith("fertilizer.")) d.getFertilizers().add(key.substring(11), (int) num(p, key, 0));
            }
            String collection = p.getProperty("collection", "");
            if (!collection.isBlank()) for (String id : collection.split(",")) d.unlock(id.trim());
        } catch (SaveException e) {
            throw e;
        } catch (Exception e) {
            throw new SaveException("The save file " + file.getFileName() + " could not be read: " + e.getMessage(), e);
        }
        d.update();   // apply the time that passed while the game was closed (growth, ripening, death)
        return d;
    }

    /** SAVE: writes the whole current game state of one save slot into its file. */
    public synchronized void save(PlayerData d) {
        if (d.getSaveId() <= 0) throw new SaveException("This game has no save slot to write to.");
        Path file = getSaveFile(d.getSaveId());
        if (!Files.isRegularFile(file)) throw new SaveException("The save file " + file.getFileName() + " is missing.");
        d.update();                                   // make sure timestamps/growth are current first
        long created;
        try { created = num(readProps(file), "createdAt", System.currentTimeMillis()); }
        catch (Exception e) { created = System.currentTimeMillis(); }
        write(file, toProps(d, created, System.currentTimeMillis()), false);
    }

    // =====================================================================================================
    //  Turning a game into text (and back)
    // =====================================================================================================

    private static Properties toProps(PlayerData d, long created, long now) {
        Properties p = new Properties();
        p.setProperty("name", d.getSaveName());
        p.setProperty("createdAt", String.valueOf(created));
        p.setProperty("lastPlayed", String.valueOf(now));
        p.setProperty("money", String.valueOf(d.getMoney()));
        p.setProperty("gardenSize", String.valueOf(d.getGardenSize()));
        for (int r = 0; r < PlayerData.MAX_SIZE; r++)
            for (int c = 0; c < PlayerData.MAX_SIZE; c++) {
                GardenPlot g = d.getPlot(r, c);
                if (!g.isWatered() && g.isEmpty() && g.getFertilizer() == null) continue;   // untouched plots are not written
                String k = "plot." + r + "." + c + ".";
                p.setProperty(k + "watered", g.isWatered() ? "1" : "0");
                if (!g.isEmpty()) p.setProperty(k + "flower", g.getFlower().id);
                if (g.getFertilizer() != null) p.setProperty(k + "fertilizer", g.getFertilizer().id);
                p.setProperty(k + "growthMs", String.valueOf(g.getGrowthMs()));
                p.setProperty(k + "updatedMs", String.valueOf(g.getUpdatedMs()));
                p.setProperty(k + "deadlineMs", String.valueOf(Math.max(0, g.getDeathDeadlineMs())));
            }
        for (Map.Entry<String, Integer> e : d.getSeeds().asMap().entrySet())
            if (e.getValue() > 0) p.setProperty("seed." + e.getKey(), String.valueOf(e.getValue()));
        for (Map.Entry<String, Integer> e : d.getFertilizers().asMap().entrySet())
            if (e.getValue() > 0) p.setProperty("fertilizer." + e.getKey(), String.valueOf(e.getValue()));
        p.setProperty("collection", String.join(",", new TreeSet<>(d.getUnlockedIds())));
        return p;
    }

    /** Writes via a temporary file, then moves it into place. createNew = refuse to replace an existing file. */
    private void write(Path file, Properties p, boolean createNew) {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                p.store(w, "Gudotopia save - one garden. Safe to read, please do not edit while the game runs.");
            }
            if (createNew) {
                if (Files.exists(file)) throw new SaveException("Save file " + file.getFileName() + " already exists.");
                Files.move(tmp, file);
            } else {
                try { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException e) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
            }
        } catch (IOException e) {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) { }
            throw new SaveException("Could not write " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    private static Properties readProps(Path file) throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { p.load(r); }
        return p;
    }

    private static long num(Properties p, String key, long fallback) {
        try { return Long.parseLong(p.getProperty(key, "").trim()); } catch (NumberFormatException e) { return fallback; }
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    /** The numbers of all save_N.properties files in the saves folder. */
    private List<Long> existingIds() {
        List<Long> ids = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(savesDir)) {
            for (Path f : ds) {
                java.util.regex.Matcher m = FILE_PATTERN.matcher(f.getFileName().toString());
                if (m.matches()) ids.add(Long.parseLong(m.group(1)));
            }
        } catch (IOException e) {
            throw new SaveException("Could not read the save folder " + savesDir + ": " + e.getMessage(), e);
        }
        Collections.sort(ids);
        return ids;
    }

    // =====================================================================================================
    //  One-time import of the very first single-save file (~/.gudotopia/save.properties)
    // =====================================================================================================

    /**
     * Imports the old save.properties as a new save slot - but only when the file exists and there are no
     * saves yet. It never overwrites a save. After a successful import the old file is renamed to
     * save.properties.migrated.bak (a backup, and it can never be imported twice). On failure the original
     * file is left exactly as it was and a message is stored for the player.
     */
    private void migrateLegacyIfNeeded() {
        if (!Files.isRegularFile(legacyFile) || !existingIds().isEmpty()) return;
        try {
            long now = System.currentTimeMillis();
            PlayerData d;
            try { d = LegacyMigrator.read(legacyFile, now); }
            catch (Exception e) { throw new SaveException("the old save file could not be read (" + e + ")", e); }
            createSaveFile(DEFAULT_LEGACY_NAME, d, now);
            Path backup = legacyFile.resolveSibling("save.properties.migrated.bak");
            try {
                Files.move(legacyFile, backup, StandardCopyOption.REPLACE_EXISTING);
                startupMessage = "Your old save was imported as \"" + DEFAULT_LEGACY_NAME + "\".\nThe original file was kept as " + backup.getFileName() + ".";
            } catch (Exception e) {
                startupMessage = "Your old save was imported as \"" + DEFAULT_LEGACY_NAME + "\", but the old file could not be renamed: " + e.getMessage();
            }
        } catch (Exception e) {
            startupMessage = "Could not import your old save: " + e.getMessage() + "\nThe original file was NOT changed: " + legacyFile;
            System.err.println(startupMessage);
        }
    }
}
