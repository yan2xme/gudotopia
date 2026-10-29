import java.util.Random;

/** FACTORY METHOD pattern - concrete factory for seeds: rolls a rarity, then picks a flower of that rarity. */
public class SeedFactory implements ItemFactory<Flower> {
    @Override
    public Flower create(Random rng) {
        return FlowerRegistry.randomSeed(rng);   // same drop table as before
    }
}
