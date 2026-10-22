import java.awt.Color;
import java.util.Random;

/**
 * Flower rarity tiers.
 * DROP WEIGHTS ARE CONFIGURABLE HERE - they must add up to exactly 100 (percent).
 * The reward and default growth time of a tier are also set here.
 */
public enum Rarity {
    //          label        drop%  reward($) growth(s)  colour
    COMMON(    "Common",     50,    5,        10,        new Color(0xB8B8B8)),
    UNCOMMON(  "Uncommon",   25,    10,       14,        new Color(0x5CCB5F)),
    RARE(      "Rare",       14,    20,       18,        new Color(0x4A9BF0)),
    EPIC(      "Epic",       7,     40,       22,        new Color(0xB45CF0)),
    LEGENDARY( "Legendary",  3,     75,       26,        new Color(0xFFB020)),
    MYTHICAL(  "Mythical",   1,     150,      30,        new Color(0xFF4F8B));

    public final String label;
    public final int dropPercent;
    public final int reward;
    public final int growthSeconds;
    public final Color color;

    Rarity(String label, int dropPercent, int reward, int growthSeconds, Color color) {
        this.label = label;
        this.dropPercent = dropPercent;
        this.reward = reward;
        this.growthSeconds = growthSeconds;
        this.color = color;
    }

    /** Sanity check used at start-up: the drop table must total 100%. */
    public static void validateDropTable() {
        int total = 0;
        for (Rarity r : values()) total += r.dropPercent;
        if (total != 100) throw new IllegalStateException("Rarity drop rates add up to " + total + ", expected 100");
    }

    /** Rolls a rarity tier using the drop table. */
    public static Rarity roll(Random rng) {
        int n = rng.nextInt(100); // 0..99
        int running = 0;
        for (Rarity r : values()) {
            running += r.dropPercent;
            if (n < running) return r;
        }
        return COMMON; // unreachable when the table is valid
    }
}
