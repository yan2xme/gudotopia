import java.util.Random;

/** FACTORY METHOD pattern - concrete factory for fertilizers, using the fertilizer drop table. */
public class FertilizerFactory implements ItemFactory<Fertilizer> {
    @Override
    public Fertilizer create(Random rng) {
        return FertilizerRegistry.roll(rng);     // rolls the fertilizer drop table
    }
}
