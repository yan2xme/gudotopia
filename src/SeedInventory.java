import java.util.*;

/** The seeds the player owns (flower id -> count) and which seed is currently selected for planting. */
public class SeedInventory {
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private String selectedId;

    public void add(Flower f) { counts.merge(f.id, 1, Integer::sum); }
    public void add(String id, int n) { if (n > 0 && FlowerRegistry.byId(id) != null) counts.merge(id, n, Integer::sum); }

    public int count(Flower f) { return counts.getOrDefault(f.id, 0); }

    public int total() {
        int t = 0;
        for (int n : counts.values()) t += n;
        return t;
    }

    /** Removes one seed. Returns false if the player has none of that flower. */
    public boolean take(Flower f) {
        int n = count(f);
        if (n <= 0) return false;
        if (n == 1) counts.remove(f.id); else counts.put(f.id, n - 1);
        return true;
    }

    /** Owned flowers, in the same order as the flower list (stable for the UI). */
    public List<Flower> ownedFlowers() {
        List<Flower> list = new ArrayList<>();
        for (Flower f : FlowerRegistry.ALL) if (count(f) > 0) list.add(f);
        return list;
    }

    public void select(Flower f) { selectedId = (f != null && count(f) > 0) ? f.id : null; }

    /** The selected flower, or null when nothing is selected or the stack ran out. */
    public Flower getSelected() {
        if (selectedId == null) return null;
        Flower f = FlowerRegistry.byId(selectedId);
        if (f == null || count(f) <= 0) { selectedId = null; return null; }
        return f;
    }

    public Map<String, Integer> asMap() { return Collections.unmodifiableMap(counts); }
}
