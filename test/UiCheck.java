import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.*;

/**
 * Drives the REAL window with a Robot (mouse + keyboard) under a virtual display and saves screenshots.
 *   phase 1: java ... UiCheck 1 <dbFolder> <shotsFolder>   (play, create two saves, press EXIT)
 *   phase 2: java ... UiCheck 2 <dbFolder> <shotsFolder>   (fresh process: LOAD and compare with phase 1)
 */
public class UiCheck {
    static Robot robot; static GameFrame frame; static String shots; static int fails = 0;

    static void sleep(int ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { } }
    static void check(boolean cond, String name) { System.out.println((cond ? "PASS  " : "FAIL  ") + name); if (!cond) fails++; }

    static void shot(String name) throws Exception {
        sleep(250);
        SwingUtilities.invokeAndWait(() -> {
            Container c = frame.getContentPane();
            BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics(); c.paint(g); g.dispose();
            try { javax.imageio.ImageIO.write(img, "png", new File(shots, name + ".png")); } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    static <T extends Component> T find(Container c, Class<T> type, String prefix) {
        for (Component k : c.getComponents()) {
            if (type.isInstance(k) && k.isShowing() && (prefix == null || (k instanceof PixelButton b && b.getLabel().startsWith(prefix)))) return type.cast(k);
            if (k instanceof Container cc) { T r = find(cc, type, prefix); if (r != null) return r; }
        }
        return null;
    }

    static void clickAt(Point p) { robot.mouseMove(p.x, p.y); sleep(80); robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); sleep(50); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); sleep(200); }
    static void clickComp(Component c) throws Exception { Point p = c.getLocationOnScreen(); clickAt(new Point(p.x + c.getWidth() / 3, p.y + c.getHeight() / 3)); }

    static void click(String label) throws Exception {
        PixelButton[] b = new PixelButton[1];
        SwingUtilities.invokeAndWait(() -> b[0] = find(frame.getContentPane(), PixelButton.class, label));
        if (b[0] == null) throw new RuntimeException("button not found: " + label);
        clickComp(b[0]);
    }

    static void type(String s) {
        for (char ch : s.toCharArray()) {
            int code = KeyEvent.getExtendedKeyCodeForChar(ch);
            boolean shift = Character.isUpperCase(ch);
            if (shift) robot.keyPress(KeyEvent.VK_SHIFT);
            robot.keyPress(code); robot.keyRelease(code);
            if (shift) robot.keyRelease(KeyEvent.VK_SHIFT);
            sleep(40);
        }
    }

    static void typeName(String name) throws Exception {
        JTextField[] f = new JTextField[1];
        SwingUtilities.invokeAndWait(() -> f[0] = find(frame.getContentPane(), JTextField.class, null));
        clickComp(f[0]);
        robot.keyPress(KeyEvent.VK_CONTROL); robot.keyPress(KeyEvent.VK_A); robot.keyRelease(KeyEvent.VK_A); robot.keyRelease(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_DELETE); robot.keyRelease(KeyEvent.VK_DELETE);
        type(name);
    }

    static GardenPanel garden() { for (Component c : frame.getContentPane().getComponents()) if (c instanceof GardenPanel g) return g; return null; }
    static double geo(String f) throws Exception { Field fl = GardenPanel.class.getDeclaredField(f); fl.setAccessible(true); return fl.getDouble(garden()); }

    static void clickPlot(int r, int c) throws Exception {
        double gcx = geo("gcx"), gtop = geo("gtop"), gtw = geo("gtw"), gth = geo("gth");
        Point o = garden().getLocationOnScreen();
        clickAt(new Point((int) (o.x + gcx + (c - r) * gtw / 2), (int) (o.y + gtop + (c + r + 1) * gth / 2)));
    }

    /** Click the first item in a shop dialog's grid (geometry mirrors ItemDialog.doLayout). */
    static void clickFirstDialogItem() {
        Container p = garden(); int w = p.getWidth(), h = p.getHeight();
        int dw = Math.min(740, w - 40), dh = Math.min(500, h - 40), dx = (w - dw) / 2, dy = (h - dh) / 2;
        int gx = dx + (int) (dw * 0.36) + 18, gy = dy + 104;
        Point o = p.getLocationOnScreen();
        clickAt(new Point(o.x + gx + 43, o.y + gy + 47));
    }

    static void clickLoadRow(int i) {
        Container p = frame.getContentPane(); int w = p.getWidth(), h = p.getHeight();
        int rowH = Math.max(56, (int) (h * 0.13)), gap = Math.max(6, h / 70);
        Point o = p.getLocationOnScreen();
        clickAt(new Point(o.x + (int) (w * 0.12) + 100, o.y + (int) (h * 0.20) + i * (rowH + gap) + rowH / 2));
    }

    /** The plot at (2,2) holds a flower that withers by itself, so it is judged separately (by its deadline). */
    static String strip22(String s) { return s.replaceAll("\\[2,2:[^\\]]*\\]", ""); }

    static String summary(PlayerData d) {
        StringBuilder sb = new StringBuilder();
        sb.append("name=").append(d.getSaveName()).append(" money=").append(d.getMoney()).append(" garden=").append(d.getGardenSize())
          .append(" seeds=").append(d.getSeeds().total()).append(" ferts=").append(d.getFertilizers().total()).append(" unlocked=").append(d.unlockedCount());
        for (int r = 0; r < 6; r++) for (int c = 0; c < 6; c++) {
            GardenPlot p = d.getPlot(r, c);
            if (p.isWatered() || !p.isEmpty() || p.getFertilizer() != null)
                sb.append(" [").append(r).append(",").append(c).append(":").append(p.isWatered() ? "W" : "d").append(p.getFertilizer() != null ? "F" : "-").append(p.isEmpty() ? "empty" : p.getFlower().id).append("]");
        }
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        int phase = Integer.parseInt(args[0]);
        Path dir = Paths.get(args[1]); shots = args[2]; new File(shots).mkdirs();
        robot = new Robot();
        SwingUtilities.invokeAndWait(() -> { frame = new GameFrame(new SaveManager(dir)); frame.setVisible(true); });
        sleep(1200);
        Path expectedFile = dir.resolve("expected.txt");
        if (phase == 1) phase1(expectedFile); else phase2(expectedFile);
        System.out.println(fails == 0 ? "UI PHASE " + phase + ": ALL PASSED" : "UI PHASE " + phase + ": " + fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }

    static void phase1(Path expectedFile) throws Exception {
        shot("01_menu");
        // the four menu buttons in order
        java.util.List<String> labels = new java.util.ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            java.util.List<PixelButton> bs = new java.util.ArrayList<>();
            for (Component k : frame.getContentPane().getComponents()) if (k instanceof MainMenuPanel mp) for (Component b : mp.getComponents()) if (b instanceof PixelButton pb) bs.add(pb);
            bs.sort((x, y) -> Integer.compare(x.getX(), y.getX()));
            for (PixelButton pb : bs) labels.add(pb.getLabel());
        });
        check(labels.equals(java.util.List.of("NEW", "LOAD", "HOW TO PLAY", "EXIT")), "menu buttons left-to-right: " + labels);

        click("LOAD"); sleep(300); shot("02_load_empty");
        check(frame.saves().listSaves().isEmpty(), "LOAD with no saves shows the friendly empty screen (list is empty)");
        click("BACK");
        click("HOW TO PLAY"); sleep(300); shot("03_howto"); click("BACK");

        click("NEW"); sleep(300); typeName("Alpha"); shot("04_new_typed");
        click("CREATE"); sleep(500);
        PlayerData a = frame.getData();
        check(a != null && a.getSaveName().equals("Alpha") && a.getSaveId() > 0 && a.getMoney() == 100, "NEW -> typed 'Alpha' -> CREATE opens a fresh garden (id " + (a == null ? "-" : a.getSaveId()) + ")");
        shot("05_garden_new");

        // --- buy seeds ($2) through the real dialog
        click("SEEDS"); sleep(300);
        for (int i = 0; i < 12; i++) click("BUY SEED");
        shot("06_seed_shop");
        check(a.getMoney() == 100 - 24 && a.getSeeds().total() == 12, "12 seed purchases cost $24 and fill the seed bag");
        click("CLOSE"); sleep(200);

        // --- buy fertilizer
        click("FERTILIZER"); sleep(300);
        for (int i = 0; i < 4; i++) click("BUY FERTILIZER");
        shot("07_fertilizer_shop");
        int expectMoney = 76 - 4 * FertilizerRegistry.SHOP_PRICE;
        check(a.getMoney() == expectMoney && a.getFertilizers().total() == 4, "4 fertilizer purchases cost $" + 4 * FertilizerRegistry.SHOP_PRICE);

        // --- order of actions: dry soil refuses a seed
        click("CLOSE"); sleep(200);
        int seedsBefore = a.getSeeds().total();
        clickPlot(0, 0);
        check(a.getPlot(0, 0).isEmpty() && a.getSeeds().total() == seedsBefore, "clicking DRY soil with seeds does not plant (seed kept)");
        shot("08_dry_rejected");
        // --- water two plots with the WATER tool
        click("WATER"); clickPlot(0, 0); clickPlot(0, 1); clickPlot(1, 0);
        check(a.getPlot(0, 0).isWatered() && a.getPlot(0, 1).isWatered() && a.getPlot(1, 0).isWatered(), "WATER tool waters three empty plots");
        click("WATER");                                              // put the can away
        // --- fertilizer on a watered empty plot, using the real dialog + click
        click("FERTILIZER"); sleep(250); clickFirstDialogItem(); sleep(250);
        Fertilizer chosen = null; for (Fertilizer f : FertilizerRegistry.ALL) if (a.getFertilizers().count(f) > 0) { chosen = f; break; }
        int fertBefore = a.getFertilizers().total();
        clickPlot(0, 1);
        check(a.getPlot(0, 1).getFertilizer() != null && a.getFertilizers().total() == fertBefore - 1, "fertilizer picked in the shop is applied to a watered EMPTY plot and used up");
        clickPlot(0, 1);
        check(a.getFertilizers().total() == fertBefore - 1, "a second fertilizer on the same plot is rejected (not used up)");
        shot("09_watered_fertilized");
        garden().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        robot.keyPress(KeyEvent.VK_ESCAPE); robot.keyRelease(KeyEvent.VK_ESCAPE); sleep(200);      // ESC puts the tool away
        // --- plant: no tool, click watered plots -> random seed consumed
        seedsBefore = a.getSeeds().total();
        clickPlot(0, 0); clickPlot(0, 1); clickPlot(1, 0);
        check(!a.getPlot(0, 0).isEmpty() && !a.getPlot(0, 1).isEmpty() && !a.getPlot(1, 0).isEmpty() && a.getSeeds().total() == seedsBefore - 3, "clicking watered plots plants 3 random seeds (bag 12 -> 9)");
        shot("10_planted");

        // --- growth, ripening, harvest, death (timers shortened by -D options for this test)
        long start = System.currentTimeMillis();
        while (!(a.getPlot(0, 0).isMature() && a.getPlot(0, 1).isMature() && a.getPlot(1, 0).isMature()) && System.currentTimeMillis() - start < 15000) sleep(200);
        check(a.getPlot(0, 0).isMature() && a.getPlot(0, 1).isMature() && a.getPlot(1, 0).isMature(), "all three flowers grew by themselves and are ripe");
        shot("11_ripe");
        int money0 = a.getMoney(), unlocked0 = a.unlockedCount();
        Flower harvested = a.getPlot(0, 1).getFlower();
        clickPlot(0, 1);
        check(a.getPlot(0, 1).isEmpty() && !a.getPlot(0, 1).isWatered() && a.getPlot(0, 1).getFertilizer() == null && a.getMoney() == money0 + harvested.reward && a.unlockedCount() == unlocked0 + 1,
                "clicking a ripe flower harvests it: +$" + harvested.reward + ", unlock +1, soil back to dry & unfertilized");
        // wait for the warning window of an unharvested flower
        start = System.currentTimeMillis();
        while (a.getPlot(0, 0).msUntilDeath(a.now()) > GameConfig.WARNING_MS && System.currentTimeMillis() - start < 20000) sleep(100);
        sleep(300);
        shot("12_warning");
        check(!a.getPlot(0, 0).isEmpty() && a.getPlot(0, 0).msUntilDeath(a.now()) <= GameConfig.WARNING_MS, "unharvested flower enters the warning window (<" + GameConfig.WARNING_MS / 1000 + "s left)");
        int moneyBeforeDeath = a.getMoney(), unlockedBeforeDeath = a.unlockedCount();
        start = System.currentTimeMillis();
        while (!a.getPlot(0, 0).isEmpty() && System.currentTimeMillis() - start < 20000) sleep(100);
        sleep(400);
        shot("13_dead");
        check(a.getPlot(0, 0).isEmpty() && !a.getPlot(0, 0).isWatered(), "unharvested flower died on its own; plot reset to dry soil");
        check(a.getMoney() == moneyBeforeDeath && a.unlockedCount() == unlockedBeforeDeath, "dead flower gave no money and no unlock");
        // death/ripe counters leave state for the restart check:  plant one more and leave it growing
        click("WATER"); clickPlot(2, 2); click("WATER"); clickPlot(2, 2);
        click("SAVE"); sleep(300);
        shot("14_saved");
        String alphaSummary = summary(a);
        System.out.println("      Alpha before leaving: " + alphaSummary);

        // --- a second save must start fresh and never touch Alpha
        click("BACK"); sleep(300);
        click("NEW"); sleep(300); typeName("alpha"); click("CREATE"); sleep(300);
        shot("15_duplicate_name");
        check(frame.saves().listSaves().size() == 1, "creating 'alpha' (same name, other case) is refused with a message; still 1 save");
        typeName("Beta"); click("CREATE"); sleep(500);
        PlayerData b = frame.getData();
        check(b.getSaveName().equals("Beta") && b.getMoney() == 100 && b.getSeeds().total() == 0 && b.getFertilizers().total() == 0 && b.unlockedCount() == 0 && !b.getPlot(2, 2).isWatered(), "NEW 'Beta' is completely fresh (nothing from Alpha)");
        click("SEEDS"); sleep(250); click("BUY SEED"); click("BUY SEED"); click("CLOSE"); sleep(200);
        check(b.getMoney() == 96, "Beta spends its own money");
        click("BACK"); sleep(300);
        click("LOAD"); sleep(400); shot("16_load_two_saves");
        check(frame.saves().listSaves().size() == 2, "LOAD lists both saves");
        clickLoadRow(1); sleep(200);                                     // 2nd row = the older save = Alpha
        click("LOAD"); sleep(600);
        PlayerData a2 = frame.getData();
        check(a2.getSaveName().equals("Alpha") && strip22(summary(a2)).equals(strip22(alphaSummary)), "choosing Alpha in LOAD restores exactly Alpha's money/seeds/fertilizer/collection/plots");
        check(a2.getSeeds().total() == a.getSeeds().total() && a2.getMoney() == a.getMoney(), "Alpha's numbers are unchanged by Beta's purchases");
        shot("17_alpha_reloaded");
        click("WATER"); clickPlot(1, 1); clickPlot(1, 2); click("WATER");             // empty watered plots never expire...
        click("FERTILIZER"); sleep(250); clickFirstDialogItem(); sleep(250); clickPlot(1, 2);  // ...and one gets a fertilizer
        robot.keyPress(KeyEvent.VK_ESCAPE); robot.keyRelease(KeyEvent.VK_ESCAPE); sleep(200);
        check(a2.getPlot(1, 1).isWatered() && a2.getPlot(1, 2).getFertilizer() != null, "Alpha: two plots watered, one fertilized, before leaving");
        click("SAVE"); sleep(300);
        Files.writeString(expectedFile, summary(a2) + "\n");
        click("BACK"); sleep(300);
        System.out.println("      pressing EXIT ...");
        click("EXIT");
        sleep(3000);                                                      // process should end by itself
        check(false, "EXIT did not close the application");
    }

    static void phase2(Path expectedFile) throws Exception {
        String expected = Files.readString(expectedFile).trim();
        shot("20_menu_after_restart");
        // what the database says about the flower at plot (2,2) of Alpha, BEFORE the game loads it
        long deadline = 0; String storedFlower = null; boolean storedWatered = false;
        try (java.sql.Connection c = frame.saves().open(); java.sql.PreparedStatement ps = c.prepareStatement(
                "SELECT flower_id, watered, death_deadline_ms FROM garden_plots WHERE save_id = 1 AND plot_row = 2 AND plot_col = 2");
             java.sql.ResultSet rs = ps.executeQuery()) {
            rs.next(); storedFlower = rs.getString(1); storedWatered = rs.getInt(2) == 1; deadline = rs.getLong(3);
        }
        System.out.println("      DB row for Alpha plot (2,2): flower=" + storedFlower + " watered=" + storedWatered + " death_deadline=" + deadline + " (now=" + System.currentTimeMillis() + ")");
        click("LOAD"); sleep(400);
        shot("21_load_after_restart");
        check(frame.saves().listSaves().size() == 2, "after restarting the application both saves are still listed");
        clickLoadRow(0); sleep(200);
        click("LOAD"); sleep(600);
        PlayerData d = frame.getData();
        check(d.getSaveName().equals("Alpha"), "the most recently played save (Alpha) is first in the list");
        String now = summary(d);
        System.out.println("      expected: " + expected + "\n      actual:   " + now);
        check(strip22(now).equals(strip22(expected)), "Alpha restored after restart (name, money, garden, seeds, fertilizer, collection, watered + fertilized plots)");
        GardenPlot p22 = d.getPlot(2, 2);
        if (storedFlower != null && deadline > 0 && System.currentTimeMillis() >= deadline)
            check(p22.isEmpty() && !p22.isWatered(), "the flower saved at (2,2) passed its stored death deadline while the game was closed -> dead on load, soil reset");
        else if (storedFlower != null)
            check(!p22.isEmpty() && p22.getFlower().id.equals(storedFlower), "the flower saved at (2,2) was still alive and is restored");
        else
            check(p22.isEmpty(), "plot (2,2) was empty when saved and is empty now");
        shot("22_garden_after_restart");
        click("BACK"); click("LOAD"); sleep(300); clickLoadRow(1); click("LOAD"); sleep(500);
        check(frame.getData().getSaveName().equals("Beta") && frame.getData().getMoney() == 96 && frame.getData().unlockedCount() == 0, "Beta restored after restart with its own data ($96, nothing unlocked)");
        SwingUtilities.invokeLater(() -> frame.exitGame());
        sleep(2000);
    }
}
