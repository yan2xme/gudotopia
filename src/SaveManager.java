import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Saves/loads PlayerData as a small text file (java.util.Properties format, no extra libraries).
 * Default location: <your home folder>/.gudotopia/save.properties
 */
public class SaveManager {
    private static final long MAX_OFFLINE_MS = 10 * 60 * 1000L;  // plants keep growing up to 10 min while the game is closed
    private final Path file;

    public SaveManager() { this(Paths.get(System.getProperty("user.home"), ".gudotopia", "save.properties")); }
    public SaveManager(Path file) { this.file = file; }

    public Path getFile() { return file; }

    public void save(PlayerData d) {
        try {
            Properties p = new Properties();
            p.setProperty("version", "1");
            p.setProperty("money", String.valueOf(d.getMoney()));
            p.setProperty("gardenSize", String.valueOf(d.getGardenSize()));
            p.setProperty("savedAt", String.valueOf(System.currentTimeMillis()));
            for (Map.Entry<String, Integer> e : d.getSeeds().asMap().entrySet()) p.setProperty("seed." + e.getKey(), String.valueOf(e.getValue()));
            Flower sel = d.getSeeds().getSelected();
            if (sel != null) p.setProperty("selectedSeed", sel.id);
            p.setProperty("unlocked", String.join(",", d.getUnlockedIds()));
            for (int r = 0; r < PlayerData.MAX_SIZE; r++)
                for (int c = 0; c < PlayerData.MAX_SIZE; c++) {
                    GardenPlot plot = d.getPlot(r, c);
                    if (!plot.isEmpty()) p.setProperty("plot." + r + "." + c, plot.getFlower().id + ":" + plot.getGrowthMs());
                }
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) { p.store(out, "Gudotopia save"); }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            System.err.println("Could not save game: " + e);   // never crash the game because of a failed save
        }
    }

    /** Loads the save; returns a brand-new game if there is none or it is corrupted. */
    public PlayerData load() {
        if (!Files.exists(file)) return new PlayerData();
        try {
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(file)) { p.load(in); }
            PlayerData d = new PlayerData();
            d.setMoney(Integer.parseInt(p.getProperty("money", String.valueOf(PlayerData.START_MONEY)).trim()));
            d.setGardenSize(Integer.parseInt(p.getProperty("gardenSize", "3").trim()));
            for (String id : p.getProperty("unlocked", "").split(",")) if (!id.isBlank()) d.unlock(id.trim());
            for (String key : p.stringPropertyNames()) {
                if (key.startsWith("seed.")) d.getSeeds().add(key.substring(5), Integer.parseInt(p.getProperty(key).trim()));
            }
            Flower sel = FlowerRegistry.byId(p.getProperty("selectedSeed", ""));
            if (sel != null) d.getSeeds().select(sel);
            long offline = Math.max(0, Math.min(MAX_OFFLINE_MS, System.currentTimeMillis() - Long.parseLong(p.getProperty("savedAt", "0").trim())));
            for (int r = 0; r < d.getGardenSize(); r++)
                for (int c = 0; c < d.getGardenSize(); c++) {
                    String v = p.getProperty("plot." + r + "." + c);
                    if (v == null) continue;
                    String[] parts = v.split(":");
                    Flower f = FlowerRegistry.byId(parts[0]);
                    if (f != null) d.getPlot(r, c).plant(f, Long.parseLong(parts[1]) + offline);
                }
            return d;
        } catch (Exception e) {
            System.err.println("Save file unreadable, starting a new game: " + e);
            try { Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING); } catch (IOException ignored) { }
            return new PlayerData();
        }
    }
}
