import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.*;

/** Drives the real window under Xvfb: clicks through the game with a Robot and saves screenshots. */
public class RenderCheck {
    static Robot robot; static GameFrame frame; static String out;

    static void sleep(int ms) throws Exception { Thread.sleep(ms); }

    static void shot(String name) throws Exception {
        sleep(250);
        SwingUtilities.invokeAndWait(() -> {
            Container c = frame.getContentPane();
            BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics(); c.paint(g); g.dispose();
            try { javax.imageio.ImageIO.write(img, "png", new File(out, name + ".png")); } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    static PixelButton find(Container c, String prefix) {
        for (Component k : c.getComponents()) {
            if (k instanceof PixelButton b && b.isShowing() && b.getLabel().startsWith(prefix)) return b;
            if (k instanceof Container cc) { PixelButton r = find(cc, prefix); if (r != null) return r; }
        }
        return null;
    }

    static void clickAt(Point p) throws Exception {
        robot.mouseMove(p.x, p.y); sleep(80);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); sleep(50);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); sleep(150);
    }

    static void click(String label) throws Exception {
        PixelButton[] b = new PixelButton[1];
        SwingUtilities.invokeAndWait(() -> b[0] = find(frame.getContentPane(), label));
        if (b[0] == null) throw new RuntimeException("button not found: " + label);
        Point p = b[0].getLocationOnScreen();
        clickAt(new Point(p.x + b[0].getWidth() / 3, p.y + b[0].getHeight() / 3));
    }

    static GardenPanel garden() {
        for (Component c : frame.getContentPane().getComponents()) if (c instanceof GardenPanel g) return g;
        return null;
    }

    static double geo(String f) throws Exception { Field fl = GardenPanel.class.getDeclaredField(f); fl.setAccessible(true); return fl.getDouble(garden()); }

    static void clickPlot(int r, int c) throws Exception {
        double gcx = geo("gcx"), gtop = geo("gtop"), gtw = geo("gtw"), gth = geo("gth");
        Point origin = garden().getLocationOnScreen();
        clickAt(new Point((int) (origin.x + gcx + (c - r) * gtw / 2), (int) (origin.y + gtop + (c + r + 1) * gth / 2)));
    }

    public static void main(String[] args) throws Exception {
        out = args[0];
        new File(out).mkdirs();
        Path save = Files.createTempFile("gudo-ui", ".properties"); Files.delete(save);
        robot = new Robot();
        SwingUtilities.invokeAndWait(() -> { frame = new GameFrame(new SaveManager(save)); frame.setVisible(true); });
        sleep(1200);
        shot("1_menu");

        click("HOW TO PLAY"); sleep(300); shot("2_howto");
        click("BACK"); sleep(200);
        click("START"); sleep(400); shot("3_garden_empty");

        // buy seeds through the real dialog
        click("SEEDS"); sleep(300);
        for (int i = 0; i < 12; i++) click("BUY SEED");
        shot("4_seed_dialog");
        System.out.println("money after 12 seeds: " + frame.getData().getMoney() + " (expected 76), seeds: " + frame.getData().getSeeds().total());
        click("CLOSE"); sleep(200);

        // plant by clicking real plots
        PlayerData d = frame.getData();
        for (Flower f : d.getSeeds().ownedFlowers()) { d.getSeeds().select(f); break; }
        clickPlot(0, 0);
        System.out.println("planted at 0,0: " + !d.getPlot(0, 0).isEmpty());
        clickPlot(0, 0); // occupied / still growing -> toast, no change
        // make a nice looking garden state
        SwingUtilities.invokeAndWait(() -> {
            int i = 0;
            for (Flower f : FlowerRegistry.ALL) {
                if (i >= 8) break;
                int r = i / 3, c = i % 3;
                if (!(r == 0 && c == 0)) d.getPlot(r, c).plant(FlowerRegistry.byId(String.format("flower_%02d", 3 + i * 3)), (long) (i % 4) * 4000);
                i++;
            }
            d.getPlot(2, 2).plant(FlowerRegistry.byId("flower_28"), 100000);
            d.unlock("flower_01"); d.unlock("flower_05"); d.unlock("flower_12"); d.unlock("flower_18"); d.unlock("flower_28");
        });
        d.update(1);
        click("FERTILIZER"); sleep(200);
        clickPlot(1, 1);
        shot("5_garden_playing");
        int before = d.getMoney();
        clickPlot(2, 2);            // harvest mature
        System.out.println("harvest money delta: " + (d.getMoney() - before) + " unlocked=" + d.unlockedCount());
        shot("6_after_harvest");
        click("LARGER"); sleep(200);
        System.out.println("garden size after expand: " + d.getGardenSize() + " money " + d.getMoney());
        shot("7_garden_4x4");
        click("FLOWERS"); sleep(300);
        shot("8_collection");
        click("BACK"); sleep(200);
        click("BACK"); sleep(300);
        shot("9_back_to_menu");
        // smallest window + biggest garden + a custom face image
        d.setGardenSize(6);
        java.awt.image.BufferedImage face = new java.awt.image.BufferedImage(200, 200, java.awt.image.BufferedImage.TYPE_INT_RGB);
        Graphics2D fg = face.createGraphics(); fg.setColor(new Color(0xFFCC99)); fg.fillRect(0, 0, 200, 200);
        fg.setColor(Color.BLACK); fg.fillOval(60, 70, 20, 30); fg.fillOval(120, 70, 20, 30); fg.drawArc(60, 110, 80, 50, 200, 140); fg.dispose();
        javax.imageio.ImageIO.write(face, "png", new File("assets/faces/flower_01.png"));
        SwingUtilities.invokeAndWait(() -> { frame.setSize(900, 600); frame.showGarden(); });
        sleep(500);
        for (int r = 0; r < 6; r++) for (int c = 0; c < 6; c++) if (d.getPlot(r, c).isEmpty()) d.getPlot(r, c).plant(FlowerRegistry.byId("flower_01"), 5000 * ((r + c) % 3) + (r == c ? 100000 : 0));
        shot("10_garden_6x6_small");
        SwingUtilities.invokeAndWait(() -> frame.showCollection()); sleep(300);
        shot("11_collection_custom_face");
        frame.saveGame();
        System.out.println("save file exists: " + Files.exists(save) + " -> unlocked in save: " + new SaveManager(save).load().unlockedCount());
        System.exit(0);
    }
}
