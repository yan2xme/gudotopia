import java.awt.Color;
import java.util.*;

/**
 * Every fertilizer in the game plus the shop settings. This is the place to tune prices,
 * bonuses and drop chances.
 *
 * Buying fertilizer works like buying seeds: you pay a flat SHOP_PRICE and receive a RANDOM fertilizer.
 * First a rarity is rolled using DROP_PERCENT (must total 100), then one fertilizer of that rarity is picked.
 * Each fertilizer's own "price" is its value (shown in the inventory); it is
 * what a future fixed-price shop would charge.
 */
public final class FertilizerRegistry {
    /** What one random fertilizer costs in the shop. */
    public static final int SHOP_PRICE = 10;

    public static final List<Fertilizer> ALL = new ArrayList<>();
    private static final Map<String, Fertilizer> BY_ID = new HashMap<>();
    /** Chance (in %) of each rarity when buying fertilizer. Must total 100. */
    private static final Map<Rarity, Integer> DROP_PERCENT = new EnumMap<>(Rarity.class);

    static {
        // ---- configurable drop table: rarities that have no fertilizer yet stay at 0 ----
        for (Rarity r : Rarity.values()) DROP_PERCENT.put(r, 0);
        DROP_PERCENT.put(Rarity.COMMON, 70);
        DROP_PERCENT.put(Rarity.UNCOMMON, 30);

        // ---- the fertilizers (id, name, description, rarity, value $, growth bonus, colour) ----
        add(new Fertilizer("fert_worm", "Worm Fertilizer",
                "Rich castings from hard-working worms. Plants grow 50% faster.",
                Rarity.COMMON, 8, 0.50, new Color(0xE58AA0)));
        add(new Fertilizer("fert_manure", "Manure Fertilizer",
                "Strong, stinky and very effective. Plants grow twice as fast.",
                Rarity.UNCOMMON, 20, 1.00, new Color(0x7A4A25)));

        validate();
    }

    private FertilizerRegistry() {}

    private static void add(Fertilizer f) { ALL.add(f); BY_ID.put(f.id, f); }

    private static void validate() {
        int total = 0;
        for (Map.Entry<Rarity, Integer> e : DROP_PERCENT.entrySet()) {
            total += e.getValue();
            if (e.getValue() > 0 && ofRarity(e.getKey()).isEmpty())
                throw new IllegalStateException("Fertilizer drop table gives " + e.getKey() + " a chance but no fertilizer has that rarity");
        }
        if (total != 100) throw new IllegalStateException("Fertilizer drop rates add up to " + total + ", expected 100");
    }

    public static Fertilizer byId(String id) { return id == null ? null : BY_ID.get(id); }

    public static List<Fertilizer> ofRarity(Rarity r) {
        List<Fertilizer> list = new ArrayList<>();
        for (Fertilizer f : ALL) if (f.rarity == r) list.add(f);
        return list;
    }

    public static int dropPercent(Rarity r) { return DROP_PERCENT.get(r); }

    /** Chance (in %) of getting exactly this fertilizer from one purchase. */
    public static double chanceOf(Fertilizer f) { return dropPercent(f.rarity) / (double) ofRarity(f.rarity).size(); }

    /** Random purchase: roll a rarity from the drop table, then pick one fertilizer of that rarity. */
    public static Fertilizer roll(Random rng) {
        int n = rng.nextInt(100), running = 0;
        for (Rarity r : Rarity.values()) {
            running += DROP_PERCENT.get(r);
            if (n < running) {
                List<Fertilizer> pool = ofRarity(r);
                return pool.get(rng.nextInt(pool.size()));
            }
        }
        return ALL.get(0); // unreachable when the table totals 100
    }
}
