import java.nio.file.*;
import java.util.*;

/** Save-file persistence tests: real text files in a temp folder, fake clocks. Run: java -cp out SaveFileTest */
public class SaveFileTest {
    static int fails = 0;
    static void check(boolean cond, String name) { System.out.println((cond ? "PASS  " : "FAIL  ") + name); if (!cond) fails++; }

    static java.util.Properties props(SaveManager sm, long id) throws Exception { var p = new java.util.Properties(); try (var r = Files.newBufferedReader(sm.getSaveFile(id))) { p.load(r); } return p; }
    static long files(Path dir) throws Exception { try (var st = Files.list(dir.resolve("saves"))) { return st.filter(x -> x.getFileName().toString().endsWith(".properties")).count(); } }
    static Path tempDir() throws Exception { return Files.createTempDirectory("gudo-db-"); }
    static void rmrf(Path p) throws Exception { try (var s = Files.walk(p)) { s.sorted(Comparator.reverseOrder()).forEach(x -> x.toFile().delete()); } }
    static Flower f(String id) { return FlowerRegistry.byId(id); }

    public static void main(String[] args) throws Exception {
        Path dir = tempDir();

        // ============ folder creation ============
        SaveManager sm = new SaveManager(dir);
        check(Files.isDirectory(dir.resolve("saves")), "the saves folder is created automatically");
        check(sm.listSaves().isEmpty() && files(dir) == 0, "fresh folder has no saves");
        check(SaveManager.getInstance() == SaveManager.getInstance(), "Singleton: getInstance() always returns the same object");

        // ============ names ============
        check(SaveManager.validateName("") != null && SaveManager.validateName("   ") != null, "empty name rejected");
        check(SaveManager.validateName("x".repeat(21)) != null, "too-long name rejected");
        check(SaveManager.validateName("bad/name*") != null, "illegal characters rejected");
        check(SaveManager.validateName("Lola's Farm 2") == null, "normal name accepted");

        // ============ two independent saves ============
        PlayerData a = sm.createSave("Alpha");
        PlayerData b = sm.createSave("Beta");
        check(a.getSaveId() != b.getSaveId() && a.getSaveId() > 0, "each save gets its own unique id");
        check(a.getMoney() == 100 && a.getGardenSize() == 3 && a.getSeeds().total() == 0 && a.unlockedCount() == 0, "new save starts with default money/garden/inventory/collection");
        boolean dup = false, dupMsg = false;
        try { sm.createSave("alpha"); } catch (SaveException e) { dup = true; dupMsg = e.getMessage().contains("already exists"); }
        check(dup && dupMsg, "duplicate name (even different letter case) is refused with a message");
        check(sm.listSaves().size() == 2 && files(dir) == 2, "failed duplicate created no extra file");

        // play on Alpha with a controllable clock
        long[] clk = {System.currentTimeMillis() - 5_000};
        a.setClock(() -> clk[0]);
        a.setMoney(500); a.setGardenSize(4);
        a.getSeeds().add("flower_03", 3); a.getSeeds().add("flower_18", 2);
        a.getFertilizers().add(FertilizerRegistry.byId("fert_worm")); a.getFertilizers().add(FertilizerRegistry.byId("fert_manure"));
        a.unlock("flower_01"); a.unlock("flower_05");
        a.water(0, 0);                                                    // watered, empty
        a.water(0, 1); a.applyFertilizer(0, 1, FertilizerRegistry.byId("fert_worm"));   // watered + fertilizer, empty
        a.water(1, 1); a.plant(1, 1);                                     // planted, growing
        GardenPlot planted = a.getPlot(1, 1); String plantedId = planted.getFlower().id;
        clk[0] += 2_000;
        sm.save(a);

        // Beta must be completely untouched
        PlayerData b2 = sm.loadSave(b.getSaveId());
        check(b2.getMoney() == 100 && b2.getGardenSize() == 3 && b2.getSeeds().total() == 0 && b2.getFertilizers().total() == 0 && b2.unlockedCount() == 0, "saving Alpha did not change Beta (money, garden, seeds, fertilizers, collection)");
        boolean betaPlotsClean = true;
        for (int r = 0; r < 6; r++) for (int c = 0; c < 6; c++) if (!b2.getPlot(r, c).isEmpty() || b2.getPlot(r, c).isWatered() || b2.getPlot(r, c).getFertilizer() != null) betaPlotsClean = false;
        check(betaPlotsClean, "Beta's garden plots are all still dry and empty");

        // change Beta, then verify Alpha is unaffected
        b2.setMoney(42); b2.getSeeds().add("flower_30", 1); b2.unlock("flower_30"); b2.water(2, 2);
        sm.save(b2);

        // ============ "restart the application": brand-new SaveManager, then load ============
        SaveManager sm2 = new SaveManager(dir);
        List<SaveInfo> list = sm2.listSaves();
        check(list.size() == 2, "after restart both saves are listed");
        SaveInfo ia = list.stream().filter(s -> s.name().equals("Alpha")).findFirst().get();
        check(ia.money() == 500 && ia.gardenSize() == 4 && ia.discovered() == 2 && ia.lastPlayed() > 0, "list shows name/money/garden size/discovered/last played");
        PlayerData al = sm2.loadSave(a.getSaveId());
        check(al.getSaveName().equals("Alpha") && al.getMoney() == 500 && al.getGardenSize() == 4, "Alpha restored: name, money, garden size");
        int sc = al.getSeeds().count(f("flower_03")) + al.getSeeds().count(f("flower_18"));
        check(sc == 4, "Alpha restored: seed inventory (5 owned - 1 planted = 4)");
        check(al.getFertilizers().count(FertilizerRegistry.byId("fert_manure")) == 1 && al.getFertilizers().count(FertilizerRegistry.byId("fert_worm")) == 0, "Alpha restored: fertilizer inventory (worm used on plot, manure left)");
        check(al.isUnlocked(f("flower_01")) && al.isUnlocked(f("flower_05")) && al.unlockedCount() == 2 && !al.isUnlocked(f("flower_30")), "Alpha restored: its own collection only");
        check(al.getPlot(0, 0).isWatered() && al.getPlot(0, 0).isEmpty(), "watering status of an empty plot persists");
        check(al.getPlot(0, 1).isWatered() && al.getPlot(0, 1).getFertilizer() != null && al.getPlot(0, 1).getFertilizer().id.equals("fert_worm"), "fertilizer effect on an empty plot persists");
        check(!al.getPlot(1, 1).isEmpty() && al.getPlot(1, 1).getFlower().id.equals(plantedId) && al.getPlot(1, 1).isWatered(), "planted flower persists with watered soil");
        check(al.getPlot(1, 1).getGrowthMs() > 0, "growth progress persists");
        PlayerData bl = sm2.loadSave(b.getSaveId());
        check(bl.getMoney() == 42 && bl.getSeeds().count(f("flower_30")) == 1 && bl.isUnlocked(f("flower_30")) && bl.getPlot(2, 2).isWatered(), "Beta restored its own changes");
        check(bl.getFertilizers().total() == 0 && !bl.isUnlocked(f("flower_01")) && bl.getGardenSize() == 3, "Beta has none of Alpha's data");

        // ============ the file really is readable text ============
        var pa = props(sm2, a.getSaveId());
        check("500".equals(pa.getProperty("money")) && "Alpha".equals(pa.getProperty("name")), "file save_N.properties contains money=500 and name=Alpha as plain text");
        check("1".equals(pa.getProperty("plot.0.0.watered")) && "fert_worm".equals(pa.getProperty("plot.0.1.fertilizer")), "file stores watered / fertilizer state of plots");
        check(pa.getProperty("collection").split(",").length == 2, "file stores the 2 collection entries");
        long filesBefore = files(dir);
        for (int i = 0; i < 5; i++) sm2.save(al);
        check(files(dir) == filesBefore && !Files.exists(sm2.getSaveFile(a.getSaveId()).resolveSibling("save_" + a.getSaveId() + ".properties.tmp")), "saving repeatedly rewrites the same file (no new files, no leftovers)");
        al.getSeeds().takeRandom(new Random()); sm2.save(al);          // inventory shrinks -> stored quantity must shrink too
        check(sm2.loadSave(a.getSaveId()).getSeeds().total() == 3, "using a seed lowers the stored quantity");
        PlayerData emptied = sm2.loadSave(b.getSaveId()); emptied.getSeeds().take(f("flower_30")); sm2.save(emptied);
        check(sm2.loadSave(b.getSaveId()).getSeeds().total() == 0, "using the last seed of a type removes it (quantity 0)");

        // ============ growth + death timing across a restart ============
        long now = System.currentTimeMillis();
        PlayerData t = sm2.createSave("Timing");
        Flower fc = f("flower_03"); long grow = fc.growthMillis();      // Clover: 10 s to grow
        // Build the game as it was 45 s ago, when it was saved and the app was closed:
        PlayerData tOld = new PlayerData(); tOld.setSaveInfo(t.getSaveId(), "Timing"); tOld.setMoney(t.getMoney());
        tOld.setClock(() -> now - 45_000);
        //  (0,0) ripe, deadline 20 s ago  -> must be DEAD when loaded now
        tOld.getPlot(0, 0).restore(true, fc, null, grow, now - 45_000, now - 20_000);
        //  (0,1) ripe, deadline in 28 s   -> must still be ALIVE, ~28 s left
        tOld.getPlot(0, 1).restore(true, fc, null, grow, now - 45_000, now + 28_000);
        //  (0,2) planted 4 s ago, still growing -> keeps growing
        tOld.getPlot(0, 2).restore(true, fc, null, 0, now - 4_000, 0);
        sm2.save(tOld);                                                  // closing the game: everything is written to the file
        check(props(sm2, t.getSaveId()).stringPropertyNames().stream().filter(k -> k.endsWith(".flower")).count() == 3, "3 planted plots stored in the file before the 'restart'");
        PlayerData tl = sm2.loadSave(t.getSaveId());
        List<PlayerData.PlotEvent> ev = tl.drainEvents();
        check(tl.getPlot(0, 0).isEmpty() && !tl.getPlot(0, 0).isWatered(), "flower that waited > 30 s while the game was closed is dead on load (plot reset)");
        check(ev.stream().anyMatch(e -> e.type == GardenPlot.Event.DIED && e.row == 0 && e.col == 0), "...and the death is reported to the UI");
        check(tl.getMoney() == tOld.getMoney() && tl.unlockedCount() == 0, "...with no reward and no collection unlock");
        long left = tl.getPlot(0, 1).msUntilDeath(tl.now());
        check(!tl.getPlot(0, 1).isEmpty() && tl.getPlot(0, 1).isMature() && left > 15_000 && left <= GameConfig.DEATH_MS, "flower that ripened 2 s before reload is alive with ~" + left / 1000 + " s left");
        double pr = tl.getPlot(0, 2).progress();
        check(!tl.getPlot(0, 2).isEmpty() && !tl.getPlot(0, 2).isMature() && pr > 0.2 && pr < 0.9, "growing flower kept growing across the restart (progress " + Math.round(pr * 100) + "%)");

        // ============ failure safety ============
        PlayerData ghost = new PlayerData(); ghost.setSaveInfo(98765, "Ghost");
        boolean threw = false; try { sm2.save(ghost); } catch (SaveException e) { threw = true; }
        check(threw, "saving to a save id that does not exist raises an error (not silent)");
        check(sm2.listSaves().size() == 3 && !Files.exists(sm2.getSaveFile(98765)), "...and creates no file");
        boolean loadMissing = false; try { sm2.loadSave(555); } catch (SaveException e) { loadMissing = true; }
        check(loadMissing, "loading a missing save raises a friendly error");

        // a damaged save file is skipped in the list, its text left untouched, and loading it gives a clear error
        Path badDir = tempDir(); SaveManager bm = new SaveManager(badDir);
        PlayerData good = bm.createSave("Good");
        Path badFile = bm.getSaveFile(99); byte[] garbage = "money=5\ngardenSize=\\u12\n".getBytes();
        Files.write(badFile, garbage);
        check(bm.listSaves().size() == 1 && bm.listSaves().get(0).name().equals("Good"), "a damaged file is skipped in the LOAD list (the good save is still listed)");
        boolean badThrew = false; try { bm.loadSave(99); } catch (SaveException e) { badThrew = true; }
        check(badThrew, "loading the damaged file gives a clear error instead of crashing");
        bm.save(bm.loadSave(good.getSaveId()));
        check(Arrays.equals(Files.readAllBytes(badFile), garbage), "the damaged file is left untouched (never silently erased)");
        rmrf(badDir);

        // ============ migration from save.properties ============
        Path md = tempDir();
        String legacy = "#Gudotopia save\nversion=1\nmoney=777\ngardenSize=4\nsavedAt=" + (now - 3_600_000) + "\n" +
                "seed.flower_18=2\nseed.flower_03=1\nselectedSeed=flower_18\nunlocked=flower_01,flower_30\n" +
                "plot.1.2=flower_05\\:4000\nplot.0.0=flower_03\\:999999\n";
        Files.writeString(md.resolve("save.properties"), legacy);
        SaveManager ms = new SaveManager(md);
        List<SaveInfo> msaves = ms.listSaves();
        check(msaves.size() == 1 && msaves.get(0).name().equals("Migrated Garden"), "old save.properties imported as one save with a default name");
        PlayerData mg = ms.loadSave(msaves.get(0).id());
        check(mg.getMoney() == 777 && mg.getGardenSize() == 4 && mg.getSeeds().count(f("flower_18")) == 2 && mg.getSeeds().count(f("flower_03")) == 1, "migrated: money, garden size, seeds");
        check(mg.isUnlocked(f("flower_01")) && mg.isUnlocked(f("flower_30")) && mg.unlockedCount() == 2, "migrated: unlocked flowers");
        check(!mg.getPlot(1, 2).isEmpty() && mg.getPlot(1, 2).getFlower().id.equals("flower_05") && mg.getPlot(1, 2).isWatered() && mg.getPlot(1, 2).getGrowthMs() >= 4000, "migrated: planted flower with growth progress (soil counts as watered)");
        check(!mg.getPlot(0, 0).isEmpty() && mg.getPlot(0, 0).isMature(), "migrated: a ripe flower survives (grace period) instead of dying at once");
        check(!Files.exists(md.resolve("save.properties")) && Files.readString(md.resolve("save.properties.migrated.bak")).equals(legacy), "original file preserved unchanged as save.properties.migrated.bak");
        check(ms.takeStartupMessage() != null, "player gets a message about the import");
        SaveManager ms2 = new SaveManager(md);
        check(ms2.listSaves().size() == 1, "reopening does not import it a second time");
        Files.writeString(md.resolve("save.properties"), legacy);       // someone puts the old file back
        SaveManager ms3 = new SaveManager(md);
        check(ms3.listSaves().size() == 1 && Files.exists(md.resolve("save.properties")), "existing saves are never overwritten/extended by an old file; the file is left alone");
        rmrf(md);

        Path cd = tempDir();
        Files.writeString(cd.resolve("save.properties"), "money=notanumber\ngardenSize=%%%\n");
        SaveManager cs = new SaveManager(cd);
        check(cs.listSaves().isEmpty() && Files.exists(cd.resolve("save.properties")) && cs.takeStartupMessage() != null, "corrupt old file: error reported, original file preserved, no save created");
        rmrf(cd);

        rmrf(dir);
        System.out.println(fails == 0 ? "\nALL SAVE-FILE TESTS PASSED" : "\n" + fails + " TEST(S) FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
