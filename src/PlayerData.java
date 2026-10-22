import java.util.*;

/**
 * All the progress of the player AND the core game rules (buying, planting, harvesting...).
 * It knows nothing about Swing, so it can be tested without any window.
 */
public class PlayerData {
    public static final int START_MONEY = 100;
    public static final int SEED_COST = 2;
    public static final int FERTILIZER_COST = 30;
    public static final double FERTILIZER_FRACTION = 0.5;   // skips 50% of a flower's growth time
    public static final int START_SIZE = 3;
    public static final int MAX_SIZE = 6;
    /** Price of growing the garden FROM size 3, 4, 5 (index = size - 3). */
    private static final int[] EXPAND_COSTS = {50, 100, 200};

    /** Outcome of a player action: success flag + text to show. */
    public static final class Result {
        public final boolean ok;
        public final String message;
        public final Flower flower;        // seed received / flower harvested (may be null)
        public final boolean newDiscovery; // true when a harvest unlocked a flower for the first time
        public final int amount;           // money gained on harvest
        Result(boolean ok, String message, Flower flower, boolean newDiscovery, int amount) {
            this.ok = ok; this.message = message; this.flower = flower; this.newDiscovery = newDiscovery; this.amount = amount;
        }
        static Result fail(String m) { return new Result(false, m, null, false, 0); }
        static Result ok(String m) { return new Result(true, m, null, false, 0); }
    }

    private int money = START_MONEY;
    private int gardenSize = START_SIZE;
    private final GardenPlot[][] plots = new GardenPlot[MAX_SIZE][MAX_SIZE];
    private final SeedInventory seeds = new SeedInventory();
    private final Set<String> unlocked = new LinkedHashSet<>();
    private final Random rng = new Random();

    public PlayerData() {
        for (int r = 0; r < MAX_SIZE; r++) for (int c = 0; c < MAX_SIZE; c++) plots[r][c] = new GardenPlot();
    }

    // ---------- getters ----------
    public int getMoney() { return money; }
    public int getGardenSize() { return gardenSize; }
    public GardenPlot getPlot(int row, int col) { return plots[row][col]; }
    public SeedInventory getSeeds() { return seeds; }
    public boolean isUnlocked(Flower f) { return unlocked.contains(f.id); }
    public int unlockedCount() { return unlocked.size(); }
    public Set<String> getUnlockedIds() { return Collections.unmodifiableSet(unlocked); }

    /** Cost of the next garden upgrade, or -1 when the garden is already at maximum size. */
    public int nextExpandCost() { return gardenSize >= MAX_SIZE ? -1 : EXPAND_COSTS[gardenSize - START_SIZE]; }

    // ---------- setters used by SaveManager / tests ----------
    public void setMoney(int m) { money = Math.max(0, m); }
    public void setGardenSize(int s) { gardenSize = Math.max(START_SIZE, Math.min(MAX_SIZE, s)); }
    public void unlock(String id) { if (FlowerRegistry.byId(id) != null) unlocked.add(id); }

    // ---------- game rules ----------
    /** Buy one random seed for $2. */
    public Result buySeed() {
        if (money < SEED_COST) return Result.fail("Not enough money! A seed costs $" + SEED_COST + ".");
        money -= SEED_COST;
        Flower f = FlowerRegistry.randomSeed(rng);
        seeds.add(f);
        if (seeds.getSelected() == null) seeds.select(f);
        return new Result(true, "You got a " + f.rarity.label + " " + f.name + " seed!", f, false, 0);
    }

    /** Test helper: buy a specific seed (what a future fixed-seed shop would use). */
    public Result buySpecificSeed(Flower f) {
        if (money < SEED_COST) return Result.fail("Not enough money! A seed costs $" + SEED_COST + ".");
        money -= SEED_COST;
        seeds.add(f);
        return new Result(true, "Bought a " + f.name + " seed.", f, false, 0);
    }

    private boolean inGarden(int r, int c) { return r >= 0 && c >= 0 && r < gardenSize && c < gardenSize; }

    /** Plant the currently selected seed in an empty plot. */
    public Result plant(int r, int c) {
        if (!inGarden(r, c)) return Result.fail("That plot doesn't exist.");
        GardenPlot p = plots[r][c];
        if (!p.isEmpty()) return Result.fail("This plot is already in use!");
        Flower f = seeds.getSelected();
        if (f == null) return Result.fail("Pick a seed first - open SEEDS and select one.");
        seeds.take(f);
        p.plant(f);
        return new Result(true, "Planted " + f.name + "!", f, false, 0);
    }

    /** Harvest a fully grown flower: money + permanent unlock. */
    public Result harvest(int r, int c) {
        if (!inGarden(r, c)) return Result.fail("That plot doesn't exist.");
        GardenPlot p = plots[r][c];
        if (p.isEmpty()) return Result.fail("Nothing is planted here.");
        if (!p.isMature()) return Result.fail("Not ready yet - " + (p.remainingMs() / 1000 + 1) + "s to go.");
        Flower f = p.getFlower();
        money += f.reward;
        boolean first = unlocked.add(f.id);
        p.clear();
        String msg = first ? "NEW FLOWER DISCOVERED: " + f.name + "!  +$" + f.reward
                           : "Harvested " + f.name + "  +$" + f.reward;
        return new Result(true, msg, f, first, f.reward);
    }

    /** Spend $30 to speed up one growing plant. Money is only taken when it really can be applied. */
    public Result fertilize(int r, int c) {
        if (!inGarden(r, c)) return Result.fail("That plot doesn't exist.");
        GardenPlot p = plots[r][c];
        if (p.isEmpty()) return Result.fail("Fertilizer needs a plant - this plot is empty.");
        if (p.isMature()) return Result.fail("This flower is already fully grown!");
        if (money < FERTILIZER_COST) return Result.fail("Not enough money! Fertilizer costs $" + FERTILIZER_COST + ".");
        money -= FERTILIZER_COST;
        p.fertilize(FERTILIZER_FRACTION);
        return Result.ok("Fertilized! The " + p.getFlower().name + " grows faster.");
    }

    /** Buy the next garden size (3x3 -> 4x4 -> 5x5 -> 6x6). */
    public Result expandGarden() {
        int cost = nextExpandCost();
        if (cost < 0) return Result.fail("Your garden is already at maximum size!");
        if (money < cost) return Result.fail("Not enough money! Larger plot costs $" + cost + ".");
        money -= cost;
        gardenSize++;
        return Result.ok("Garden expanded to " + gardenSize + "x" + gardenSize + "!");
    }

    /** Advance all plants. Called by the game timer. */
    public void update(long deltaMs) {
        for (int r = 0; r < gardenSize; r++) for (int c = 0; c < gardenSize; c++) plots[r][c].grow(deltaMs);
    }
}
