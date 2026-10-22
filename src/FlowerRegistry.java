import java.awt.Color;
import java.util.*;

/**
 * The list of every flower in the game (30 total).
 * To rename a flower / change its description, edit the table in the static block below.
 * To give a flower a real classmate face, drop a PNG named after its id into assets/faces/ (see README).
 */
public final class FlowerRegistry {
    public static final List<Flower> ALL = new ArrayList<>();
    private static final Map<String, Flower> BY_ID = new HashMap<>();

    static {
        Rarity.validateDropTable();
        // name, rarity  (ids are generated as flower_01 ... flower_30)
        Object[][] table = {
            {"Daisy", Rarity.COMMON}, {"Buttercup", Rarity.COMMON}, {"Clover", Rarity.COMMON},
            {"Dandelion", Rarity.COMMON}, {"Pansy", Rarity.COMMON}, {"Marigold", Rarity.COMMON},
            {"Petunia", Rarity.COMMON}, {"Poppy", Rarity.COMMON}, {"Violet", Rarity.COMMON},
            {"Zinnia", Rarity.COMMON},
            {"Tulip", Rarity.UNCOMMON}, {"Carnation", Rarity.UNCOMMON}, {"Sunflower", Rarity.UNCOMMON},
            {"Lavender", Rarity.UNCOMMON}, {"Primrose", Rarity.UNCOMMON}, {"Cosmos", Rarity.UNCOMMON},
            {"Snapdragon", Rarity.UNCOMMON},
            {"Rose", Rarity.RARE}, {"Lily", Rarity.RARE}, {"Orchid", Rarity.RARE},
            {"Iris", Rarity.RARE}, {"Peony", Rarity.RARE}, {"Hibiscus", Rarity.RARE},
            {"Lotus", Rarity.EPIC}, {"Camellia", Rarity.EPIC}, {"Magnolia", Rarity.EPIC}, {"Dahlia", Rarity.EPIC},
            {"Moonflower", Rarity.LEGENDARY}, {"Starbloom", Rarity.LEGENDARY},
            {"Rainbow Bloom", Rarity.MYTHICAL},
        };
        Color[] skins = { new Color(0xF5CBA7), new Color(0xE8B48A), new Color(0xD59A6A), new Color(0xB67B4F), new Color(0xFAD7B5) };
        Color[] hairs = { new Color(0x2B1B12), new Color(0x4A2C17), new Color(0x1A1A1A), new Color(0x8A5A2B),
                          new Color(0xC9A227), new Color(0x7A2E1D) };
        for (int i = 0; i < table.length; i++) {
            Random rng = new Random(1000 + i * 77L);
            String id = String.format("flower_%02d", i + 1);
            Rarity rarity = (Rarity) table[i][1];
            Color petal = Color.getHSBColor((i * 0.137f) % 1f, 0.55f + 0.3f * rng.nextFloat(), 0.95f);
            String desc = "Classmate #" + (i + 1) + " in bloom. Put " + id + ".png in assets/faces/ to show their real face.";
            Flower f = new Flower(id, (String) table[i][0], desc, rarity, petal,
                    skins[rng.nextInt(skins.length)], hairs[rng.nextInt(hairs.length)], rng.nextInt(4));
            ALL.add(f);
            BY_ID.put(id, f);
        }
    }

    private FlowerRegistry() {}

    public static Flower byId(String id) { return BY_ID.get(id); }

    /**
     * Random seed system: roll a rarity from the drop table, then pick one flower of that rarity.
     * (A fixed-seed shop could later call byId() directly instead of this method.)
     */
    public static Flower randomSeed(Random rng) {
        Rarity rarity = Rarity.roll(rng);
        List<Flower> pool = new ArrayList<>();
        for (Flower f : ALL) if (f.rarity == rarity) pool.add(f);
        return pool.get(rng.nextInt(pool.size()));
    }
}
