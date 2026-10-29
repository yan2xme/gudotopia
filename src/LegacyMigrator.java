import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Reads the OLD save format (~/.gudotopia/save.properties, java.util.Properties) into a PlayerData object.
 * It only PARSES the file; SaveManager is the one that writes the result into a new save file.
 */
public final class LegacyMigrator {
    private LegacyMigrator() {}

    /** Throws an exception if the file is unreadable/corrupted - the caller then keeps the original file untouched. */
    public static PlayerData read(Path file, long nowMs) throws Exception {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) { p.load(in); }

        PlayerData d = new PlayerData();
        d.setMoney(Integer.parseInt(p.getProperty("money", String.valueOf(PlayerData.START_MONEY)).trim()));
        d.setGardenSize(Integer.parseInt(p.getProperty("gardenSize", "3").trim()));
        for (String id : p.getProperty("unlocked", "").split(",")) if (!id.isBlank()) d.unlock(id.trim());
        for (String key : p.stringPropertyNames())
            if (key.startsWith("seed.")) d.getSeeds().add(key.substring(5), Integer.parseInt(p.getProperty(key).trim()));

        for (int r = 0; r < d.getGardenSize(); r++)
            for (int c = 0; c < d.getGardenSize(); c++) {
                String v = p.getProperty("plot." + r + "." + c);          // format: flower_03:4000
                if (v == null) continue;
                String[] parts = v.split(":");
                Flower f = FlowerRegistry.byId(parts[0]);
                if (f == null) continue;
                long growth = Long.parseLong(parts[1].trim());
                // Old plots had no watering step, so a planted plot counts as watered.
                GardenPlot plot = d.getPlot(r, c);
                plot.restore(true, f, null, growth, nowMs, 0);
                // A flower that was already ripe gets a generous grace period instead of dying at once.
                if (plot.isMature()) plot.restore(true, f, null, growth, nowMs, nowMs + GameConfig.LEGACY_GRACE_MS);
            }
        return d;
    }
}
