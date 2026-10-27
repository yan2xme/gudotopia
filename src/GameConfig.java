/**
 * Easy-to-change timing settings. Edit the default numbers below, OR override them without
 * touching code by adding JVM options in IntelliJ (Run > Edit Configurations > VM options), e.g.
 *
 *     -Dgudotopia.deathSeconds=10 -Dgudotopia.growthScale=0.25
 */
public final class GameConfig {
    private GameConfig() {}

    /** A fully grown flower must be harvested within this many milliseconds or it dies. */
    public static final long DEATH_MS = 1000L * Long.getLong("gudotopia.deathSeconds", 30L);

    /** When fewer than this many milliseconds are left before death, the flower shows a warning. */
    public static final long WARNING_MS = 1000L * Long.getLong("gudotopia.warningSeconds", 10L);

    /** Multiplies every flower's growth time (0.5 = twice as fast, 2 = twice as slow). Handy for testing. */
    public static final double GROWTH_SCALE = Double.parseDouble(System.getProperty("gudotopia.growthScale", "1.0"));

    /** How often the game auto-saves while you play. */
    public static final long AUTOSAVE_MS = 1000L * Long.getLong("gudotopia.autosaveSeconds", 15L);

    /** Ripe flowers imported from an old save.properties get this long before they wither (kindness for migrated saves). */
    public static final long LEGACY_GRACE_MS = 5 * 60 * 1000L;
}
