package gudotopia;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The seeds the player owns, plus which one is currently selected for planting. */
public class SeedInventory {
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private String selectedId;

    public void add(Flower f, int amount) {
        if (amount <= 0) return;
        counts.merge(f.id, amount, Integer::sum);
    }

    /** Removes one seed. Returns false if the player has none. */
    public boolean remove(Flower f) {
        int have = count(f);
        if (have <= 0) return false;
        if (have == 1) counts.remove(f.id);
        else counts.put(f.id, have - 1);
        return true;
    }

    public int count(Flower f) {
        return counts.getOrDefault(f.id, 0);
    }

    public int total() {
        int sum = 0;
        for (int n : counts.values()) sum += n;
        return sum;
    }

    /** Owned flowers in collection order. */
    public List<Flower> owned() {
        List<Flower> list = new ArrayList<>();
        for (Flower f : Flower.ALL) {
            if (count(f) > 0) list.add(f);
        }
        return list;
    }

    public Flower getSelected() {
        Flower f = Flower.byId(selectedId);
        return (f != null && count(f) > 0) ? f : null;
    }

    public void select(Flower f) {
        if (f != null && count(f) > 0) selectedId = f.id;
    }

    /** Clicking the already-selected seed deselects it. */
    public void toggle(Flower f) {
        if (getSelected() == f) selectedId = null;
        else select(f);
    }

    /** If the selected seed ran out, automatically choose another one (if any). */
    public void ensureSelection() {
        if (getSelected() == null) {
            List<Flower> list = owned();
            selectedId = list.isEmpty() ? null : list.get(0).id;
        }
    }

    public String selectedId() {
        return selectedId;
    }

    /** Copy of the counts, used for saving. */
    public Map<String, Integer> snapshot() {
        return new LinkedHashMap<>(counts);
    }
}
