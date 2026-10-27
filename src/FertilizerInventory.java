import java.util.*;

/** The fertilizers the player owns (fertilizer id -> count). Kept separate from seeds on purpose. */
public class FertilizerInventory {
    private final Map<String, Integer> counts = new LinkedHashMap<>();

    public void add(Fertilizer f) { counts.merge(f.id, 1, Integer::sum); }
    public void add(String id, int n) { if (n > 0 && FertilizerRegistry.byId(id) != null) counts.merge(id, n, Integer::sum); }
    public int count(Fertilizer f) { return counts.getOrDefault(f.id, 0); }

    public int total() {
        int t = 0;
        for (int n : counts.values()) t += n;
        return t;
    }

    /** Uses up one unit. Returns false (and changes nothing) when none are left. */
    public boolean take(Fertilizer f) {
        int n = count(f);
        if (n <= 0) return false;
        if (n == 1) counts.remove(f.id); else counts.put(f.id, n - 1);
        return true;
    }

    public List<Fertilizer> owned() {
        List<Fertilizer> list = new ArrayList<>();
        for (Fertilizer f : FertilizerRegistry.ALL) if (count(f) > 0) list.add(f);
        return list;
    }

    public Map<String, Integer> asMap() { return Collections.unmodifiableMap(counts); }
}
