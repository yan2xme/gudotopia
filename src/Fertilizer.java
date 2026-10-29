import java.awt.Color;

/**
 * Definition of one kind of fertilizer. To add a new fertilizer, add one line in FertilizerRegistry -
 * nothing else in the shop / garden code has to change.
 */
public final class Fertilizer {
    public final String id;            // unique, e.g. "fert_worm" (also what the save file stores)
    public final String name;
    public final String description;
    public final Rarity rarity;
    public final int price;            // value of one unit (see FertilizerRegistry.SHOP_PRICE)
    public final double growthBonus;   // 0.5 = plant grows 50% faster, 1.0 = twice as fast
    public final Color color;          // used for the icon and the speckles on the soil

    public Fertilizer(String id, String name, String description, Rarity rarity, int price, double growthBonus, Color color) {
        this.id = id; this.name = name; this.description = description; this.rarity = rarity;
        this.price = price; this.growthBonus = growthBonus; this.color = color;
    }

    /** Growth-speed multiplier while this fertilizer is active. */
    public double speedMultiplier() { return 1.0 + growthBonus; }

    public String effectText() { return "+" + Math.round(growthBonus * 100) + "% growth speed"; }
}
