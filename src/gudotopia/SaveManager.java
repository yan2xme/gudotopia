package gudotopia;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Properties;

/**
 * Saves and loads PlayerData as a simple text file (Java .properties format).
 * Location: <user home>/.gudotopia/save.properties
 * A missing or corrupted file never crashes the game - a fresh game is started.
 */
public final class SaveManager {
    private SaveManager() {}

    public static Path savePath() {
        return Paths.get(System.getProperty("user.home"), ".gudotopia", "save.properties");
    }

    public static boolean save(PlayerData d) {
        return save(d, savePath());
    }

    public static boolean save(PlayerData d, Path file) {
        try {
            Properties p = new Properties();
            p.setProperty("version", "1");
            p.setProperty("money", Integer.toString(d.money));
            p.setProperty("grid", Integer.toString(d.gridSize));

            StringBuilder seeds = new StringBuilder();
            for (Map.Entry<String, Integer> e : d.seeds.snapshot().entrySet()) {
                if (seeds.length() > 0) seeds.append(';');
                seeds.append(e.getKey()).append(':').append(e.getValue());
            }
            p.setProperty("seeds", seeds.toString());
            p.setProperty("selected", d.seeds.selectedId() == null ? "" : d.seeds.selectedId());
            p.setProperty("unlocked", String.join(",", d.unlockedIds()));

            for (int r = 0; r < PlayerData.MAX_GRID; r++) {
                for (int c = 0; c < PlayerData.MAX_GRID; c++) {
                    GardenPlot plot = d.plots[r][c];
                    if (!plot.isEmpty()) {
                        p.setProperty("plot." + r + "." + c, plot.flower().id + ":" + plot.elapsedMs());
                    }
                }
            }

            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                p.store(out, "Gudotopia save file");
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            System.err.println("Could not save game: " + e);
            return false;
        }
    }

    public static PlayerData load() {
        return load(savePath());
    }

    public static PlayerData load(Path file) {
        if (!Files.isRegularFile(file)) return new PlayerData();
        try (InputStream in = Files.newInputStream(file)) {
            Properties p = new Properties();
            p.load(in);
            return parse(p);
        } catch (IOException | RuntimeException e) {
            System.err.println("Save file unreadable, starting a new game: " + e);
            try {   // keep the broken file for inspection instead of overwriting it silently
                Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // nothing more we can do
            }
            return new PlayerData();
        }
    }

    private static PlayerData parse(Properties p) {
        PlayerData d = new PlayerData();
        d.money = Math.max(0, Integer.parseInt(p.getProperty("money", Integer.toString(PlayerData.START_MONEY)).trim()));
        int grid = Integer.parseInt(p.getProperty("grid", Integer.toString(PlayerData.MIN_GRID)).trim());
        d.gridSize = Math.max(PlayerData.MIN_GRID, Math.min(PlayerData.MAX_GRID, grid));

        for (String part : p.getProperty("seeds", "").split(";")) {
            if (part.isBlank()) continue;
            String[] kv = part.split(":");
            Flower f = Flower.byId(kv[0].trim());
            int n = Integer.parseInt(kv[1].trim());
            if (f != null && n > 0) d.seeds.add(f, n);
        }
        d.seeds.select(Flower.byId(p.getProperty("selected", "").trim()));
        d.seeds.ensureSelection();

        for (String id : p.getProperty("unlocked", "").split(",")) {
            if (!id.isBlank()) d.unlock(id.trim());
        }

        for (int r = 0; r < PlayerData.MAX_GRID; r++) {
            for (int c = 0; c < PlayerData.MAX_GRID; c++) {
                String v = p.getProperty("plot." + r + "." + c);
                if (v == null) continue;
                String[] kv = v.split(":");
                Flower f = Flower.byId(kv[0].trim());
                if (f != null) d.plots[r][c].restore(f, Long.parseLong(kv[1].trim()));
            }
        }
        return d;
    }
}
