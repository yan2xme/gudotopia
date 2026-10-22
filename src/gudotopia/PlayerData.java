package gudotopia;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * All player progress AND the game rules (buying, planting, fertilizing,
 * harvesting, upgrading). The UI only calls these methods and shows the result,
 * so the core gameplay stays separate from drawing code.
 */
public class PlayerData {
    public static final int START_MONEY = 100;
    public static final int SEED_COST = 2;
    public static final int FERTILIZER_COST = 30;
    public static final int MIN_GRID = 3;
    public static final int MAX_GRID = 6;
    /** Cost to grow the garden from 3->4, 4->5, 5->6. */
    public static final int[] UPGRADE_COSTS = {50, 100, 200, 400};

    public int money = START_MONEY;
    public int gridSize = MIN_GRID;
    public final SeedInventory seeds = new SeedInventory();
    /** Always MAX_GRID x MAX_GRID; only the top-left gridSize x gridSize part is in use. */
    public final GardenPlot[][] plots = new GardenPlot[MAX_GRID][MAX_GRID];
    private final Set<String> unlocked = new LinkedHashSet<>();

    public PlayerData() {
        for (int r = 0; r < MAX_GRID; r++) {
            for (int c = 0; c < MAX_GRID; c++) {
                plots[r][c] = new GardenPlot();
            }
        }
    }

    /** Result of a player action, with a message to show on screen. */
    public static class ActionResult {
        public final boolean success;
        public final String message;
        public final Flower flower;        // harvested / planted flower, if any
        public final int amount;           // money gained, if any
        public final boolean newDiscovery; // first time this flower was harvested

        ActionResult(boolean success, String message, Flower flower, int amount, boolean newDiscovery) {
            this.success = success;
            this.message = message;
            this.flower = flower;
            this.amount = amount;
            this.newDiscovery = newDiscovery;
        }

        static ActionResult ok(String msg) {
            return new ActionResult(true, msg, null, 0, false);
        }

        static ActionResult fail(String msg) {
            return new ActionResult(false, msg, null, 0, false);
        }
    }

    // ---------------- collection ----------------

    public boolean isUnlocked(Flower f) {
        return unlocked.contains(f.id);
    }

    public int unlockedCount() {
        int n = 0;
        for (Flower f : Flower.ALL) if (unlocked.contains(f.id)) n++;
        return n;
    }

    public Set<String> unlockedIds() {
        return new LinkedHashSet<>(unlocked);
    }

    /** Used when loading a save. */
    public void unlock(String id) {
        if (Flower.byId(id) != null) unlocked.add(id);
    }

    // ---------------- growth ----------------

    public void tick(long deltaMs) {
        for (int r = 0; r < MAX_GRID; r++) {
            for (int c = 0; c < MAX_GRID; c++) {
                plots[r][c].tick(deltaMs);
            }
        }
    }

    public boolean inGrid(int r, int c) {
        return r >= 0 && c >= 0 && r < gridSize && c < gridSize;
    }

    public boolean hasGrowingPlant() {
        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                GardenPlot p = plots[r][c];
                if (!p.isEmpty() && !p.isReady()) return true;
            }
        }
        return false;
    }

    // ---------------- shop ----------------

    /** Buys 'count' random seeds. The flowers received are added to 'received'. */
    public ActionResult buySeeds(int count, List<Flower> received) {
        int cost = count * SEED_COST;
        if (money < cost) {
            return ActionResult.fail("Not enough money! " + count + (count == 1 ? " seed costs $" : " seeds cost $") + cost + ".");
        }
        money -= cost;
        for (int i = 0; i < count; i++) {
            Flower f = SeedShop.rollRandomSeed();
            seeds.add(f, 1);
            received.add(f);
        }
        seeds.ensureSelection();
        return ActionResult.ok("Bought " + count + (count == 1 ? " seed!" : " seeds!"));
    }

    /** Cost of the next garden upgrade, or -1 if the garden is already at maximum size. */
    public int upgradeCost() {
        if (gridSize >= MAX_GRID) return -1;
        return UPGRADE_COSTS[gridSize - MIN_GRID];
    }

    public ActionResult upgradeGarden() {
        int cost = upgradeCost();
        if (cost < 0) return ActionResult.fail("Your garden is already at maximum size!");
        if (money < cost) return ActionResult.fail("Not enough money! A larger plot costs $" + cost + ".");
        money -= cost;
        gridSize++;
        return ActionResult.ok("Garden expanded to " + gridSize + "x" + gridSize + "!");
    }

    // ---------------- plot actions ----------------

    public ActionResult plantSelected(int r, int c) {
        if (!inGrid(r, c)) return ActionResult.fail("That plot does not exist.");
        GardenPlot plot = plots[r][c];
        if (!plot.isEmpty()) return ActionResult.fail("That plot is already in use!");
        Flower f = seeds.getSelected();
        if (f == null) return ActionResult.fail("Select a seed from your Seed Bag first!");
        seeds.remove(f);
        plot.plant(f);
        seeds.ensureSelection();
        return new ActionResult(true, "Planted " + f.name + "!", f, 0, false);
    }

    public ActionResult fertilize(int r, int c) {
        if (!inGrid(r, c)) return ActionResult.fail("That plot does not exist.");
        GardenPlot plot = plots[r][c];
        if (plot.isEmpty()) return ActionResult.fail("Nothing is planted there - fertilizer needs a flower!");
        if (plot.isReady()) return ActionResult.fail("That flower is already fully grown!");
        if (money < FERTILIZER_COST) return ActionResult.fail("Not enough money! Fertilizer costs $" + FERTILIZER_COST + ".");
        money -= FERTILIZER_COST;
        plot.fertilize();
        return ActionResult.ok("Fertilizer applied - growth boosted!");
    }

    public ActionResult harvest(int r, int c) {
        if (!inGrid(r, c)) return ActionResult.fail("That plot does not exist.");
        GardenPlot plot = plots[r][c];
        if (plot.isEmpty()) return ActionResult.fail("Nothing is planted here!");
        if (!plot.isReady()) {
            long secs = (plot.remainingMs() + 999) / 1000;
            return ActionResult.fail(plot.flower().name + " is still growing (" + secs + "s left).");
        }
        Flower f = plot.harvest();
        money += f.reward;
        boolean isNew = unlocked.add(f.id);
        String msg = "Harvested " + f.name + "! +$" + f.reward + (isNew ? "  NEW FLOWER DISCOVERED!" : "");
        return new ActionResult(true, msg, f, f.reward, isNew);
    }
}
