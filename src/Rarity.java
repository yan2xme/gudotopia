import java.awt.Color;
import java.util.Random;

/**
 * Flower rarity tiers: drop chance, sell price and growth time.
 * The drop percentages must add up to exactly 100 (checked at start-up).
 */
public enum Rarity {
    //          label        drop%  reward($) growth(s)  colour
    COMMON(    "Common",     70,    5,        10,        new Color(0xB8B8B8)),
    RARE(      "Rare",       25,    25,       18,        new Color(0x4A9BF0)),
    LEGENDARY( "Legendary",  5,     100,      26,        new Color(0xFFB020));

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

    /** Start-up sanity check: the drop table must total 100%. */
    public static void validateDropTable() {
        int total = 0;
        for (Rarity r : values()) total += r.dropPercent;
        if (total != 100) throw new IllegalStateException("Rarity drop rates add up to " + total + ", expected 100");
    }

    /** Rolls a rarity tier using the drop table. */
    public static Rarity roll(Random rng) {
        int n = rng.nextInt(100), running = 0;
        for (Rarity r : values()) {
            running += r.dropPercent;
            if (n < running) return r;
        }
        return COMMON; // unreachable when the table is valid
    }
}
