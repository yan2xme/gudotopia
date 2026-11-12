import java.awt.Color;
import java.util.*;

/**
 * The list of every flower in the game (12 total: 5 Common, 4 Rare, 3 Legendary).
 * To add or change a flower, edit the table below. A classmate's face goes in assets/flowers/<id>.png;
 * the game draws the petals around it in the flower's colour. Flowers without a picture get a drawn face.
 */
public final class FlowerRegistry {
    public static final List<Flower> ALL = List.of(
        // id, name, description, rarity, petal colour, then the drawn face: skin, hair, hair style
        flower("daisy",     "Daisy",        "A cheerful little white flower.",                                             Rarity.COMMON,    0xF4F4F4, 0xF5CBA7, 0x2B1B12, 1),
        flower("tulip",     "Tulip",        "Cup-shaped and bright red.",                                                  Rarity.COMMON,    0xE84A5F, 0xE8B48A, 0x7A2E1D, 1),
        flower("sunflower", "Sunflower",    "Always faces the sun.",                                                       Rarity.COMMON,    0xFFC93C, 0xFAD7B5, 0x8A5A2B, 2),
        flower("rose",      "Rose",         "Classic, lovely, a little thorny.",                                           Rarity.COMMON,    0xC2185B, 0xD59A6A, 0x4A2C17, 3),
        flower("lily",      "Lily",         "Soft pink petals and a sweet smell.",                                         Rarity.COMMON,    0xF8A5C2, 0xE8B48A, 0x2B1B12, 2),
        flower("brent",     "Brentunia",    "Loud, proud and petunia-shaped. You will hear him before you see him.",       Rarity.RARE,      0xFF5FA2, 0xD59A6A, 0x1A1A1A, 0),
        flower("rovid",     "Rovidendron",  "A rhododendron in a red bandana. Blooms only after coffee.",                  Rarity.RARE,      0x3EC9C0, 0xF5CBA7, 0x7A2E1D, 1),
        flower("paolo",     "Paolotus",     "A lotus that is always mid-sentence. Floats past every deadline.",            Rarity.RARE,      0x9B6BFF, 0xE8B48A, 0x1A1A1A, 1),
        flower("klent",     "Klentigold",   "A marigold with a villain smirk. Plotting something (a group project).",      Rarity.RARE,      0x7E3FF2, 0xD59A6A, 0x2B1B12, 3),
        flower("aron",      "Aronchid",     "A legendary orchid: rare, refined and slightly unimpressed.",                 Rarity.LEGENDARY, 0x2ECC71, 0xF5CBA7, 0x2B1B12, 0),
        flower("zeus",      "Zeusnia",      "A zinnia with thunderbolt glasses. Legend says he never misses a quiz.",      Rarity.LEGENDARY, 0x7FDBFF, 0xE8B48A, 0x1A1A1A, 2),
        flower("gudo",      "Sir Gudahlia", "The legendary dahlia Gudotopia is named after. Grades your garden silently.", Rarity.LEGENDARY, 0xFFD700, 0xE8B48A, 0x2B1B12, 0)
    );
    private static final Map<String, Flower> BY_ID = new HashMap<>();

    static {
        Rarity.validateDropTable();
        for (Flower f : ALL) BY_ID.put(f.id, f);
        for (Rarity r : Rarity.values())
            if (ALL.stream().noneMatch(f -> f.rarity == r)) throw new IllegalStateException("No flower has rarity " + r);
    }

    private FlowerRegistry() {}

    /** Keeps the table above short: colours are written as 0xRRGGBB numbers. */
    private static Flower flower(String id, String name, String description, Rarity rarity, int petal, int skin, int hair, int hairStyle) {
        return new Flower(id, name, description, rarity, new Color(petal), new Color(skin), new Color(hair), hairStyle);
    }

    public static Flower byId(String id) { return id == null ? null : BY_ID.get(id); }

    /** Random seed: roll a rarity from the drop table, then pick one flower of that rarity. */
    public static Flower randomSeed(Random rng) {
        Rarity rarity = Rarity.roll(rng);
        List<Flower> pool = new ArrayList<>();
        for (Flower f : ALL) if (f.rarity == rarity) pool.add(f);
        return pool.get(rng.nextInt(pool.size()));
    }
}
