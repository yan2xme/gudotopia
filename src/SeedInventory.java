import java.util.*;

/** The seeds the player owns (flower id -> count). Planting picks one at random - there is no manual selection. */
public class SeedInventory {
    private final Map<String, Integer> counts = new LinkedHashMap<>();

    public void add(Flower f) { counts.merge(f.id, 1, Integer::sum); }
    public void add(String id, int n) { if (n > 0 && FlowerRegistry.byId(id) != null) counts.merge(id, n, Integer::sum); }

    public int count(Flower f) { return counts.getOrDefault(f.id, 0); }

    public int total() {
        int t = 0;
        for (int n : counts.values()) t += n;
        return t;
    }

    /** Removes one seed of a specific flower. Returns false if the player has none. */
    public boolean take(Flower f) {
        int n = count(f);
        if (n <= 0) return false;
        if (n == 1) counts.remove(f.id); else counts.put(f.id, n - 1);
        return true;
    }

    /**
     * Picks one seed at random and removes it from the inventory. Every individual seed is equally likely,
     * so a flower you own 3 of is picked 3x as often as one you own 1 of.
     * Returns null when the inventory is empty.
     */
    public Flower takeRandom(Random rng) {
        int total = total();
        if (total == 0) return null;
        int pick = rng.nextInt(total);                 // 0 .. total-1
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            pick -= e.getValue();
            if (pick < 0) {
                Flower f = FlowerRegistry.byId(e.getKey());
                take(f);
                return f;
            }
        }
        return null; // unreachable
    }

    /** Owned flowers, in the same order as the flower list (stable for the UI). */
    public List<Flower> ownedFlowers() {
        List<Flower> list = new ArrayList<>();
        for (Flower f : FlowerRegistry.ALL) if (count(f) > 0) list.add(f);
        return list;
    }

    public Map<String, Integer> asMap() { return Collections.unmodifiableMap(counts); }
}
