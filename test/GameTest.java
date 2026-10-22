import java.nio.file.*;
import java.util.*;

/** Plain-Java checks of the game rules (no window needed). Run: java -cp out GameTest */
public class GameTest {
    static int fails = 0;
    static void check(boolean cond, String name) { System.out.println((cond ? "PASS  " : "FAIL  ") + name); if (!cond) fails++; }

    public static void main(String[] a) throws Exception {
        PlayerData d = new PlayerData();
        check(d.getMoney() == 100, "starts with $100");
        check(d.getGardenSize() == 3, "starts 3x3");
        check(FlowerRegistry.ALL.size() == 30, "30 flowers");
        int sum = 0; for (Rarity r : Rarity.values()) sum += r.dropPercent;
        check(sum == 100, "drop rates total 100");

        // seeds
        PlayerData.Result r = d.buySeed();
        check(r.ok && d.getMoney() == 98 && d.getSeeds().total() == 1, "buy seed costs $2");
        Flower f = r.flower;
        check(d.getSeeds().getSelected() == f, "first seed auto-selected");
        // plant
        check(d.plant(0, 0).ok && d.getSeeds().total() == 0, "plant consumes seed");
        check(!d.plant(0, 0).ok, "cannot plant on occupied plot");
        check(!d.plant(1, 1).ok, "cannot plant without selected seed");
        // harvest too early
        check(!d.harvest(0, 0).ok, "cannot harvest immature");
        check(!d.harvest(1, 1).ok, "cannot harvest empty");
        // fertilizer
        check(!d.fertilize(1, 1).ok && d.getMoney() == 98, "fertilizer on empty plot refused, no charge");
        long before = d.getPlot(0, 0).remainingMs();
        check(d.fertilize(0, 0).ok && d.getMoney() == 68, "fertilizer costs $30");
        check(d.getPlot(0, 0).remainingMs() < before, "fertilizer speeds growth");
        d.update(60_000);
        check(d.getPlot(0, 0).isMature(), "flower grows to maturity over time");
        check(!d.fertilize(0, 0).ok && d.getMoney() == 68, "no fertilizer on mature flower");
        int m0 = d.getMoney();
        PlayerData.Result h = d.harvest(0, 0);
        check(h.ok && h.newDiscovery && d.getMoney() == m0 + f.reward, "harvest pays " + f.reward + " and discovers");
        check(d.isUnlocked(f) && d.unlockedCount() == 1 && d.getPlot(0, 0).isEmpty(), "unlocked + plot reset");
        // second harvest of same flower: money but no new discovery
        d.buySpecificSeed(f); d.getSeeds().select(f); d.plant(0, 0); d.update(60_000);
        PlayerData.Result h2 = d.harvest(0, 0);
        check(h2.ok && !h2.newDiscovery && d.unlockedCount() == 1, "repeat harvest: money, no new discovery");

        // poor player
        PlayerData poor = new PlayerData(); poor.setMoney(1);
        check(!poor.buySeed().ok && poor.getMoney() == 1, "cannot buy seed without money");
        check(!poor.expandGarden().ok && poor.getGardenSize() == 3, "cannot expand without money");
        // expansion
        PlayerData rich = new PlayerData(); rich.setMoney(1000);
        check(rich.expandGarden().ok && rich.getGardenSize() == 4 && rich.getMoney() == 950, "3x3 -> 4x4 costs $50");
        check(rich.plant(3, 3).getClass() != null && !rich.plant(3, 3).ok, "(no seed) plot 3,3 exists but needs a seed");
        rich.expandGarden(); rich.expandGarden();
        check(rich.getGardenSize() == 6 && !rich.expandGarden().ok, "max size 6x6");

        // drop rate statistics
        Random rng = new Random(42); Map<Rarity, Integer> counts = new EnumMap<>(Rarity.class);
        int N = 200_000;
        for (int i = 0; i < N; i++) counts.merge(FlowerRegistry.randomSeed(rng).rarity, 1, Integer::sum);
        boolean ok = true;
        for (Rarity rr : Rarity.values()) {
            double pct = counts.getOrDefault(rr, 0) * 100.0 / N;
            System.out.printf("      %-10s expected %2d%%  got %.2f%%%n", rr.label, rr.dropPercent, pct);
            if (Math.abs(pct - rr.dropPercent) > 0.6) ok = false;
        }
        check(ok, "random seed rates match the drop table");

        // save / load
        Path tmp = Files.createTempFile("gudo", ".properties"); Files.delete(tmp);
        SaveManager sm = new SaveManager(tmp);
        PlayerData s = new PlayerData(); s.setMoney(777); s.setGardenSize(4);
        Flower rose = FlowerRegistry.byId("flower_18");
        s.getSeeds().add(rose); s.getSeeds().add(rose); s.getSeeds().select(rose);
        s.unlock("flower_01"); s.unlock("flower_30");
        s.getPlot(1, 2).plant(FlowerRegistry.byId("flower_05"), 4000);
        sm.save(s);
        PlayerData l = sm.load();
        check(l.getMoney() == 777 && l.getGardenSize() == 4, "save/load money + garden size");
        check(l.getSeeds().count(rose) == 2 && l.getSeeds().getSelected() == rose, "save/load seeds + selection");
        check(l.isUnlocked(FlowerRegistry.byId("flower_01")) && l.isUnlocked(FlowerRegistry.byId("flower_30")) && l.unlockedCount() == 2, "save/load unlocks");
        check(!l.getPlot(1, 2).isEmpty() && l.getPlot(1, 2).getGrowthMs() >= 4000, "save/load planted flower + progress");
        // corrupted + missing
        Files.writeString(tmp, "money=notanumber\ngardenSize=%%%\n");
        PlayerData c = sm.load();
        check(c.getMoney() == 100 && c.getGardenSize() == 3, "corrupted save -> new game");
        Files.deleteIfExists(tmp);
        check(new SaveManager(tmp).load().getMoney() == 100, "missing save -> new game");
        Files.deleteIfExists(tmp.resolveSibling(tmp.getFileName() + ".corrupt"));

        System.out.println(fails == 0 ? "\nALL TESTS PASSED" : "\n" + fails + " TEST(S) FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
