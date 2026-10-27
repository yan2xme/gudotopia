import java.util.*;

/** Rule checks without any window or database. Run: java -cp out GameTest */
public class GameTest {
    static int fails = 0;
    static void check(boolean cond, String name) { System.out.println((cond ? "PASS  " : "FAIL  ") + name); if (!cond) fails++; }

    static long[] clock = {1_700_000_000_000L};                  // fake "now" in ms - tests move it forward
    static PlayerData newGame() { PlayerData d = new PlayerData(); d.setClock(() -> clock[0]); d.setMoney(1000); return d; }
    static Flower common() { return FlowerRegistry.byId("flower_03"); }   // Clover, Common, 10 s
    static Flower rose() { return FlowerRegistry.byId("flower_18"); }
    static Fertilizer worm() { return FertilizerRegistry.byId("fert_worm"); }
    static Fertilizer manure() { return FertilizerRegistry.byId("fert_manure"); }

    /** water + plant one specific flower in plot (r,c). */
    static void prepare(PlayerData d, int r, int c, Flower f) {
        d.water(r, c); d.getSeeds().add(f); 
        while (d.getSeeds().total() > 1) d.getSeeds().takeRandom(new Random());   // keep exactly the one seed we added
        PlayerData.Result res = d.plant(r, c);
        if (!res.ok) throw new IllegalStateException(res.message);
    }

    public static void main(String[] a) {
        // ---------------- definitions ----------------
        check(FlowerRegistry.ALL.size() == 30, "30 flowers still defined");
        int sum = 0; for (Rarity r : Rarity.values()) sum += r.dropPercent;
        check(sum == 100, "seed drop rates total 100");
        check(FertilizerRegistry.ALL.size() >= 2 && worm().growthBonus > 0 && manure().growthBonus > worm().growthBonus, "Manure is stronger than Worm fertilizer");
        int fsum = 0; for (Rarity r : Rarity.values()) fsum += FertilizerRegistry.dropPercent(r);
        check(fsum == 100, "fertilizer drop rates total 100");
        check(worm().id != null && !worm().name.isEmpty() && !worm().description.isEmpty() && worm().rarity != null && worm().price > 0, "fertilizer has id/name/description/rarity/price/effect");

        // ---------------- buying ----------------
        PlayerData d = newGame();
        PlayerData.Result r = d.buySeed();
        check(r.ok && d.getMoney() == 998 && d.getSeeds().total() == 1 && r.flower != null, "seed purchase costs $2 and adds a random seed");
        r = d.buyFertilizer();
        check(r.ok && d.getMoney() == 998 - FertilizerRegistry.SHOP_PRICE && d.getFertilizers().total() == 1 && r.fertilizer != null, "fertilizer purchase costs $" + FertilizerRegistry.SHOP_PRICE + " and adds a random fertilizer");
        check(d.getSeeds().total() == 1, "buying fertilizer does not touch the seed inventory");
        PlayerData poor = newGame(); poor.setMoney(1);
        check(!poor.buySeed().ok && !poor.buyFertilizer().ok && poor.getMoney() == 1, "cannot buy seeds/fertilizer without money");

        // ---------------- watering / planting order ----------------
        d = newGame();
        check(!d.plant(0, 0).ok && d.getSeeds().total() == 0, "planting with no seeds is rejected");
        d.getSeeds().add(common());
        PlayerData.Result dry = d.plant(0, 0);
        check(!dry.ok && d.getSeeds().total() == 1 && d.getPlot(0, 0).isEmpty(), "unwatered soil rejects planting and keeps the seed (" + dry.message + ")");
        check(d.water(0, 0).ok && d.getPlot(0, 0).isWatered(), "watering an empty plot works");
        check(!d.water(0, 0).ok, "watering twice is rejected");
        check(d.plant(0, 0).ok && d.getSeeds().total() == 0 && !d.getPlot(0, 0).isEmpty(), "watered soil accepts a seed (seed consumed)");
        d.getSeeds().add(common());
        check(!d.plant(0, 0).ok && d.getSeeds().total() == 1, "cannot plant on an occupied plot (seed kept)");
        check(!d.water(0, 0).ok, "cannot water a plot that already has a flower");

        // ---------------- fertilizer ordering ----------------
        d = newGame();
        d.getFertilizers().add(worm()); d.getFertilizers().add(worm()); d.getFertilizers().add(manure());
        check(!d.applyFertilizer(1, 1, worm()).ok && d.getFertilizers().count(worm()) == 2, "fertilizer on dry empty plot rejected, none used up");
        d.water(1, 1);
        check(d.applyFertilizer(1, 1, worm()).ok && d.getFertilizers().count(worm()) == 1 && d.getPlot(1, 1).getFertilizer() == worm(), "fertilizer on watered empty plot is stored on the plot and used up");
        check(!d.applyFertilizer(1, 1, manure()).ok && d.getFertilizers().count(manure()) == 1 && d.getPlot(1, 1).getFertilizer() == worm(), "second fertilizer rejected (no stacking), none used up");
        d.getSeeds().add(common());
        check(d.plant(1, 1).ok && d.getPlot(1, 1).getFertilizer() == worm(), "fertilizer effect carries over to the planted flower");
        PlayerData none = newGame(); none.water(0, 0);
        check(!none.applyFertilizer(0, 0, worm()).ok, "cannot use fertilizer you do not own");
        check(!none.applyFertilizer(0, 0, null).ok, "no fertilizer chosen is rejected");

        // ---------------- growth speed with fertilizer ----------------
        clock[0] = 1_700_000_000_000L;
        d = newGame();
        d.getFertilizers().add(worm()); d.getFertilizers().add(manure());
        for (int c = 0; c < 3; c++) d.water(0, c);
        d.applyFertilizer(0, 1, worm()); d.applyFertilizer(0, 2, manure());
        for (int c = 0; c < 3; c++) { d.getSeeds().add(common()); d.plant(0, c); }
        long total = common().growthMillis();                        // 10 000 ms
        clock[0] += (long) (total * 0.55); d.update();               // 55% of the normal time
        check(!d.getPlot(0, 0).isMature() && !d.getPlot(0, 1).isMature() && d.getPlot(0, 2).isMature(), "after 55% time: plain & worm still growing, manure (x2) is ripe");
        clock[0] += (long) (total * 0.15); d.update();               // 70% total
        check(!d.getPlot(0, 0).isMature() && d.getPlot(0, 1).isMature(), "after 70% time: worm (x1.5) ripe, plain still growing");
        check(d.getPlot(0, 0).progress() > 0.65 && d.getPlot(0, 0).progress() < 0.75, "plain plot progress ~70%");
        clock[0] += (long) (total * 0.31); d.update();
        check(d.getPlot(0, 0).isMature(), "plain plot ripe after 100% time");

        // ---------------- death timer ----------------
        clock[0] = 1_700_000_000_000L;
        d = newGame(); prepare(d, 0, 0, common());
        long t0 = clock[0];
        clock[0] = t0 + total - 1; d.update();
        check(!d.getPlot(0, 0).isMature(), "not ripe just before growth time");
        clock[0] = t0 + total + 100; d.update();
        check(d.getPlot(0, 0).isMature() && d.getPlot(0, 0).msUntilDeath(clock[0]) > GameConfig.DEATH_MS - 1000, "ripe: death countdown starts at ripeness (~30s left)");
        check(d.drainEvents().stream().anyMatch(e -> e.type == GardenPlot.Event.MATURED), "ripening is reported as an event");
        clock[0] = t0 + total + GameConfig.DEATH_MS - 5000; d.update();
        check(!d.getPlot(0, 0).isEmpty() && d.getPlot(0, 0).msUntilDeath(clock[0]) <= GameConfig.WARNING_MS, "warning window: < 10 s left but still alive");
        clock[0] = t0 + total + GameConfig.DEATH_MS - 1; d.update();
        check(!d.getPlot(0, 0).isEmpty(), "alive 1 ms before the deadline");
        int moneyBefore = d.getMoney();
        clock[0] = t0 + total + GameConfig.DEATH_MS + 1; d.update();
        GardenPlot dead = d.getPlot(0, 0);
        check(dead.isEmpty() && !dead.isWatered() && dead.getFertilizer() == null, "dead flower: plot reset (dry, no fertilizer)");
        check(d.getMoney() == moneyBefore && d.unlockedCount() == 0, "dead flower: no money, no collection unlock");
        check(d.drainEvents().stream().anyMatch(e -> e.type == GardenPlot.Event.DIED), "death is reported as an event");
        d.getSeeds().add(common());
        check(!d.plant(0, 0).ok, "after death the soil must be watered again before planting");
        check(d.water(0, 0).ok && d.plant(0, 0).ok, "water + plant again works after death");

        // death counted from ripeness, not from planting: with a 10s flower, planting+30s is NOT death
        clock[0] = 1_700_000_000_000L;
        d = newGame(); prepare(d, 0, 0, common()); t0 = clock[0];
        clock[0] = t0 + 30_000; d.update();
        check(!d.getPlot(0, 0).isEmpty() && d.getPlot(0, 0).isMature(), "30 s after PLANTING a 10 s flower it is still alive (countdown began at ripeness)");

        // long absence (game closed): ripe and dead
        clock[0] = t0 + 10 * 60_000; d.update();
        check(d.getPlot(0, 0).isEmpty(), "after 10 minutes away the unharvested flower is dead");

        // ---------------- harvest ----------------
        clock[0] = 1_700_000_000_000L;
        d = newGame(); d.getFertilizers().add(worm()); d.water(0, 0); d.applyFertilizer(0, 0, worm());
        d.getSeeds().add(rose()); d.plant(0, 0); t0 = clock[0];
        check(!d.harvest(0, 0).ok, "cannot harvest an immature flower");
        clock[0] = t0 + rose().growthMillis(); d.update();
        int m0 = d.getMoney();
        PlayerData.Result h = d.harvest(0, 0);
        check(h.ok && h.newDiscovery && d.getMoney() == m0 + rose().reward && d.isUnlocked(rose()), "harvest pays reward and unlocks the flower");
        check(d.getPlot(0, 0).isEmpty() && !d.getPlot(0, 0).isWatered() && d.getPlot(0, 0).getFertilizer() == null, "harvest resets watering + fertilizer");
        check(!d.harvest(0, 0).ok, "cannot harvest an empty plot");
        prepare(d, 0, 0, rose()); clock[0] += rose().growthMillis(); d.update();
        PlayerData.Result h2 = d.harvest(0, 0);
        check(h2.ok && !h2.newDiscovery && d.unlockedCount() == 1, "repeat harvest: money but no new discovery");

        // ---------------- expansion ----------------
        PlayerData rich = newGame(); rich.setMoney(1000);
        check(rich.expandGarden().ok && rich.getGardenSize() == 4 && rich.getMoney() == 950, "3x3 -> 4x4 costs $50");
        rich.expandGarden(); rich.expandGarden();
        check(rich.getGardenSize() == 6 && !rich.expandGarden().ok, "max size 6x6");
        check(!newGame().water(5, 5).ok, "plots outside the garden cannot be used");

        // ---------------- weighted random planting ----------------
        Flower clover = FlowerRegistry.byId("flower_03"), roseF = FlowerRegistry.byId("flower_18"), sun = FlowerRegistry.byId("flower_13");
        Map<String, Integer> picks = new HashMap<>();
        int N = 60_000; boolean invOk = true;
        Random rng = new Random(7);
        for (int i = 0; i < N; i++) {
            SeedInventory inv = new SeedInventory();
            inv.add(clover.id, 3); inv.add(roseF.id, 2); inv.add(sun.id, 1);
            Flower f = inv.takeRandom(rng);
            if (inv.total() != 5) invOk = false;
            picks.merge(f.id, 1, Integer::sum);
        }
        double pc = picks.get(clover.id) * 100.0 / N, pr = picks.get(roseF.id) * 100.0 / N, ps = picks.get(sun.id) * 100.0 / N;
        System.out.printf("      3 Clover / 2 Rose / 1 Sunflower -> %.1f%% / %.1f%% / %.1f%%  (expected 50 / 33.3 / 16.7)%n", pc, pr, ps);
        check(invOk, "planting removes exactly one seed from the inventory");
        check(Math.abs(pc - 50) < 1.2 && Math.abs(pr - 33.3) < 1.2 && Math.abs(ps - 16.7) < 1.2, "random planting is weighted by quantity");
        check(new SeedInventory().takeRandom(rng) == null, "empty inventory gives no seed");

        // ---------------- purchase drop rates ----------------
        rng = new Random(42); Map<Rarity, Integer> counts = new EnumMap<>(Rarity.class);
        for (int i = 0; i < 100_000; i++) counts.merge(FlowerRegistry.randomSeed(rng).rarity, 1, Integer::sum);
        boolean ok = true;
        for (Rarity rr : Rarity.values()) if (Math.abs(counts.getOrDefault(rr, 0) / 1000.0 - rr.dropPercent) > 0.8) ok = false;
        check(ok, "random seed purchase matches the rarity table");
        int worms = 0, N2 = 100_000; rng = new Random(5);
        for (int i = 0; i < N2; i++) if (FertilizerRegistry.roll(rng) == worm()) worms++;
        System.out.printf("      fertilizer purchase: Worm %.1f%% / Manure %.1f%% (expected 70 / 30)%n", worms * 100.0 / N2, 100 - worms * 100.0 / N2);
        check(Math.abs(worms * 100.0 / N2 - 70) < 1.0, "random fertilizer purchase matches its drop table");

        System.out.println(fails == 0 ? "\nALL GAME-RULE TESTS PASSED" : "\n" + fails + " TEST(S) FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
