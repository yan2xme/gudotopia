package gudotopia;

import java.util.List;
import java.util.Random;

/**
 * Decides which seed the player receives.
 *
 * Default mode: random seed using the rarity drop chances from Rarity.
 * FUTURE: a fixed-seed shop can simply call fixedSeed("classmate05")
 * instead of rollRandomSeed().
 */
public final class SeedShop {
    private static final Random RNG = new Random();

    private SeedShop() {}

    /** Picks a random flower seed, weighted by rarity. */
    public static Flower rollRandomSeed() {
        return rollRandomSeed(RNG);
    }

    public static Flower rollRandomSeed(Random rng) {
        // Only rarities that actually contain flowers can drop.
        double total = 0;
        for (Rarity r : Rarity.values()) {
            if (!Flower.ofRarity(r).isEmpty()) total += r.dropChance;
        }
        double roll = rng.nextDouble() * total;
        double acc = 0;
        Rarity chosen = null;
        for (Rarity r : Rarity.values()) {
            if (Flower.ofRarity(r).isEmpty()) continue;
            acc += r.dropChance;
            chosen = r;
            if (roll < acc) break;
        }
        List<Flower> pool = Flower.ofRarity(chosen);
        return pool.get(rng.nextInt(pool.size()));
    }

    /** Direct selection of a specific flower (for a future fixed-seed shop). */
    public static Flower fixedSeed(String flowerId) {
        return Flower.byId(flowerId);
    }

    public static double totalDropChance() {
        double sum = 0;
        for (Rarity r : Rarity.values()) sum += r.dropChance;
        return sum;
    }

    /** Prints a warning if the configured drop chances do not add up to 100%. */
    public static void validateDropChances() {
        double sum = totalDropChance();
        if (Math.abs(sum - 100.0) > 0.001) {
            System.err.println("WARNING: rarity drop chances add up to " + sum + "% instead of 100%.");
        }
    }
}
