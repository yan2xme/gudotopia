import java.util.Random;

/**
 * FACTORY METHOD pattern - the "creator" interface.
 *
 * The shop always does the same steps (check money, pay, receive one random item, store it), but
 * WHAT item is created differs: a seed (Flower) or a fertilizer. The factory method create()
 * hides that difference, so PlayerData never needs to know how a particular item is rolled.
 * To sell a new kind of item, write one more class that implements this interface.
 *
 * @param <T> the kind of item this factory creates
 */
public interface ItemFactory<T> {
    /** Creates one random item (using the given Random so tests can make it predictable). */
    T create(Random rng);
}
