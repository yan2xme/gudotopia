import java.util.*;
import java.util.function.LongSupplier;

/**
 * All the progress of ONE save slot AND the core game rules (buying, watering, fertilizing, planting,
 * harvesting...). It knows nothing about Swing or SQL, so it can be tested without a window or a database.
 *
 * Required order for growing a flower:  WATER the soil  ->  (optional) FERTILIZE  ->  PLANT.
 */
public class PlayerData {
    public static final int START_MONEY = 100;
    public static final int SEED_COST = 2;
    public static final int START_SIZE = 3;
    public static final int MAX_SIZE = 6;
    /** Price of growing the garden FROM size 3, 4, 5 (index = size - 3). */
    private static final int[] EXPAND_COSTS = {50, 100, 200};

    /** Outcome of a player action: success flag + text to show. */
    public static final class Result {
        public final boolean ok;
        public final String message;
        public final Flower flower;            // seed received / planted / harvested (may be null)
        public final Fertilizer fertilizer;    // fertilizer received (may be null)
        public final boolean newDiscovery;     // true when a harvest unlocked a flower for the first time
        public final int amount;               // money gained on harvest
        Result(boolean ok, String message, Flower flower, Fertilizer fertilizer, boolean newDiscovery, int amount) {
            this.ok = ok; this.message = message; this.flower = flower; this.fertilizer = fertilizer;
            this.newDiscovery = newDiscovery; this.amount = amount;
        }
        static Result fail(String m) { return new Result(false, m, null, null, false, 0); }
        static Result ok(String m) { return new Result(true, m, null, null, false, 0); }
    }

    /** Something that happened to a plot by itself (a flower became ripe, or died). */
    public static final class PlotEvent {
        public final GardenPlot.Event type;
        public final int row, col;
        public final String flowerName;
        PlotEvent(GardenPlot.Event type, int row, int col, String flowerName) {
            this.type = type; this.row = row; this.col = col; this.flowerName = flowerName;
        }
    }

    private long saveId;                 // database id of this save slot (0 = not stored yet)
    private String saveName = "";
    private int money = START_MONEY;
    private int gardenSize = START_SIZE;
    private final GardenPlot[][] plots = new GardenPlot[MAX_SIZE][MAX_SIZE];
    private final SeedInventory seeds = new SeedInventory();
    private final FertilizerInventory fertilizers = new FertilizerInventory();
    private final Set<String> unlocked = new LinkedHashSet<>();
    private final List<PlotEvent> pendingEvents = new ArrayList<>();
    private final Random rng = new Random();
    private LongSupplier clock = System::currentTimeMillis;   // replaceable in tests

    public PlayerData() {
        for (int r = 0; r < MAX_SIZE; r++) for (int c = 0; c < MAX_SIZE; c++) plots[r][c] = new GardenPlot();
    }

    // ---------- getters ----------
    public long getSaveId() { return saveId; }
    public String getSaveName() { return saveName; }
    public int getMoney() { return money; }
    public int getGardenSize() { return gardenSize; }
    public GardenPlot getPlot(int row, int col) { return plots[row][col]; }
    public SeedInventory getSeeds() { return seeds; }
    public FertilizerInventory getFertilizers() { return fertilizers; }
    public boolean isUnlocked(Flower f) { return unlocked.contains(f.id); }
    public int unlockedCount() { return unlocked.size(); }
    public Set<String> getUnlockedIds() { return Collections.unmodifiableSet(unlocked); }
    public long now() { return clock.getAsLong(); }

    /** Cost of the next garden upgrade, or -1 when the garden is already at maximum size. */
    public int nextExpandCost() { return gardenSize >= MAX_SIZE ? -1 : EXPAND_COSTS[gardenSize - START_SIZE]; }

    // ---------- setters used by SaveManager / tests ----------
    public void setSaveInfo(long id, String name) { this.saveId = id; this.saveName = name; }
    public void setMoney(int m) { money = Math.max(0, m); }
    public void setGardenSize(int s) { gardenSize = Math.max(START_SIZE, Math.min(MAX_SIZE, s)); }
    public void unlock(String id) { if (FlowerRegistry.byId(id) != null) unlocked.add(id); }
    /** Tests can plug in a fake clock to "fast-forward" time. */
    public void setClock(LongSupplier clock) { this.clock = clock; }

    // ---------- time ----------
    /**
     * Brings every plot up to date with the clock (growth, ripening, death). Called every game tick,
     * before every action, before saving and right after loading - so offline time is handled too.
     */
    public void update() {
        long now = clock.getAsLong();
        for (int r = 0; r < gardenSize; r++)
            for (int c = 0; c < gardenSize; c++) {
                GardenPlot p = plots[r][c];
                Flower f = p.getFlower();
                GardenPlot.Event ev = p.advance(now);
                if (ev == GardenPlot.Event.DIED) {
                    p.reset();                                   // the soil must be prepared again
                    pendingEvents.add(new PlotEvent(ev, r, c, f.name));
                } else if (ev == GardenPlot.Event.MATURED) {
                    pendingEvents.add(new PlotEvent(ev, r, c, f.name));
                }
            }
    }

    /** Returns (and clears) the list of things that happened since the last call, for the UI to announce. */
    public List<PlotEvent> drainEvents() {
        List<PlotEvent> copy = new ArrayList<>(pendingEvents);
        pendingEvents.clear();
        return copy;
    }

    // ---------- shop ----------
    /** Buy one random seed for $2. */
    public Result buySeed() {
        if (money < SEED_COST) return Result.fail("Not enough money! A seed costs $" + SEED_COST + ".");
        money -= SEED_COST;
        Flower f = FlowerRegistry.randomSeed(rng);
        seeds.add(f);
        return new Result(true, "You got a " + f.rarity.label + " " + f.name + " seed!", f, null, false, 0);
    }

    /** Buy one random fertilizer. */
    public Result buyFertilizer() {
        int price = FertilizerRegistry.SHOP_PRICE;
        if (money < price) return Result.fail("Not enough money! Fertilizer costs $" + price + ".");
        money -= price;
        Fertilizer f = FertilizerRegistry.roll(rng);
        fertilizers.add(f);
        return new Result(true, "You got " + f.name + "! (" + f.rarity.label + ", " + f.effectText() + ")", null, f, false, 0);
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

    // ---------- garden actions ----------
    private boolean inGarden(int r, int c) { return r >= 0 && c >= 0 && r < gardenSize && c < gardenSize; }

    /** STEP 1: water an empty plot. Dry soil can never be planted. */
    public Result water(int r, int c) {
        update();
        if (!inGarden(r, c)) return Result.fail("That plot doesn't exist.");
        GardenPlot p = plots[r][c];
        if (!p.isEmpty()) return Result.fail("Watering is only for empty plots - this one already has a flower.");
        if (p.isWatered()) return Result.fail("This soil is already watered.");
        p.water();
        return Result.ok("Soil watered! Add fertilizer if you like, then plant a seed.");
    }

    /**
     * STEP 2 (optional): apply a fertilizer to a WATERED empty plot, or to a growing flower.
     * One fertilizer per plot - a second one is rejected, so effects never stack by accident.
     * The fertilizer is only used up when it is really applied.
     */
    public Result applyFertilizer(int r, int c, Fertilizer f) {
        update();
        if (!inGarden(r, c)) return Result.fail("That plot doesn't exist.");
        if (f == null) return Result.fail("Pick a fertilizer first (open FERTILIZER).");
        GardenPlot p = plots[r][c];
        if (p.isEmpty() && !p.isWatered()) return Result.fail("Water the soil first, then add fertilizer.");
        if (!p.isEmpty() && p.isMature()) return Result.fail("This flower is already fully grown - harvest it!");
        if (p.getFertilizer() != null) return Result.fail("This plot already has " + p.getFertilizer().name + ". Only one fertilizer per plot.");
        if (!fertilizers.take(f)) return Result.fail("You have no " + f.name + " left.");
        p.setFertilizer(f);
        return Result.ok(p.isEmpty() ? f.name + " added to the soil. Now plant a seed!"
                                      : f.name + " applied - the " + p.getFlower().name + " grows faster!");
    }

    /**
     * STEP 3: plant. A random seed is taken from the inventory (more copies = more likely).
     * Nothing is consumed unless the planting really happens.
     */
    public Result plant(int r, int c) {
        update();
        if (!inGarden(r, c)) return Result.fail("That plot doesn't exist.");
        GardenPlot p = plots[r][c];
        if (!p.isEmpty()) return Result.fail("This plot is already in use!");
        if (seeds.total() == 0) return Result.fail("You have no seeds yet - buy some with SEEDS first!");
        if (!p.isWatered()) return Result.fail("The soil is dry! Use WATER on it first, then plant.");
        Flower f = seeds.takeRandom(rng);
        p.plant(f, now());
        return new Result(true, "Planted a " + f.name + " (" + f.rarity.label + ")!", f, null, false, 0);
    }

    /** Harvest a fully grown flower: money + permanent unlock. The soil goes back to dry. */
    public Result harvest(int r, int c) {
        update();
        if (!inGarden(r, c)) return Result.fail("That plot doesn't exist.");
        GardenPlot p = plots[r][c];
        if (p.isEmpty()) return Result.fail("Nothing is planted here.");
        if (!p.isMature()) return Result.fail("Not ready yet - " + (p.remainingMs() / 1000 + 1) + "s to go.");
        Flower f = p.getFlower();
        money += f.reward;
        boolean first = unlocked.add(f.id);
        p.reset();
        String msg = first ? "NEW FLOWER DISCOVERED: " + f.name + "!  +$" + f.reward
                           : "Harvested " + f.name + "  +$" + f.reward;
        return new Result(true, msg, f, null, first, f.reward);
    }
}
