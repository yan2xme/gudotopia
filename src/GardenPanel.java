import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The gameplay screen: isometric garden, shop on the right, money/inventory at the bottom.
 * Handles clicks on individual plots and forwards the rules to PlayerData; saving goes through GameFrame -> SaveManager.
 *
 * How a click on a plot behaves:
 *   - a fully grown flower      -> harvest it (always)
 *   - WATER tool is on          -> water the (empty) plot
 *   - a FERTILIZER is selected  -> apply it to the plot
 *   - no tool                   -> plant a random seed on an empty watered plot
 */
public class GardenPanel extends ScreenPanel {
    private enum Tool { NONE, WATER, FERTILIZER }

    private final GameFrame frame;
    private final PlayerData data;
    private final PixelButton back, sound;
    private final ShopPanel shop;
    private final SeedDialog seedDialog;
    private final FertilizerDialog fertilizerDialog;

    private Tool tool = Tool.NONE;
    private Fertilizer activeFertilizer;
    private int hoverR = -1, hoverC = -1;
    private String toastText = "";
    private boolean toastError;
    private long toastUntil;
    private long sinceSave;

    private static final class Floater { String text; double x, y, age; Color color; }
    private final List<Floater> floaters = new ArrayList<>();

    // isometric geometry (recomputed every paint / mouse event so the window can be resized)
    private double gcx, gtop, gtw, gth, gd;
    private int gn;

    public GardenPanel(GameFrame frame, PlayerData data) {
        this.frame = frame;
        this.data = data;

        back = new PixelButton("BACK", () -> { autosave(); frame.showMenu(); });
        sound = new PixelButton(soundLabel(), this::toggleSound);
        shop = new ShopPanel(this::openFertilizer, this::openSeeds, this::buyLargerPlot,
                this::toggleWater, frame::showCollection, this::manualSave);
        seedDialog = new SeedDialog(this, data);
        fertilizerDialog = new FertilizerDialog(this, data);
        add(seedDialog);        // dialogs are added first => painted on top of everything else
        add(fertilizerDialog);
        add(back);
        add(sound);
        add(shop);

        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { updateHover(e.getX(), e.getY()); }
            @Override public void mouseExited(MouseEvent e) { hoverR = hoverC = -1; }
            @Override public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                updateHover(e.getX(), e.getY());
                if (hoverR >= 0) clickCell(hoverR, hoverC);
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);

        // ESC closes a shop window, otherwise puts the current tool away
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        getActionMap().put("cancel", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (seedDialog.isVisible()) seedDialog.setVisible(false);
                else if (fertilizerDialog.isVisible()) fertilizerDialog.setVisible(false);
                else dropTool();
            }
        });

        // Time that passed while this save was closed may already have killed flowers: tell the player.
        data.update();
        handleEvents(data.drainEvents());
    }

    // ------------------------------------------------------------------ tools & shop buttons
    private static String soundLabel() { return AudioManager.getInstance().isMuted() ? "SOUND OFF" : "SOUND ON"; }

    private void toggleSound() {
        AudioManager.getInstance().toggleMute();
        sound.setLabel(soundLabel());
    }

    private void dropTool() { tool = Tool.NONE; activeFertilizer = null; }

    private void toggleWater() {
        boolean wasOn = tool == Tool.WATER;
        dropTool();
        if (!wasOn) tool = Tool.WATER;
    }

    private void openSeeds() { dropTool(); seedDialog.open(); }
    private void openFertilizer() { fertilizerDialog.open(); }

    /** Called by the fertilizer shop when the player clicks a fertilizer in their stock. */
    public void selectFertilizer(Fertilizer f) {
        tool = Tool.FERTILIZER;
        activeFertilizer = f;
        toast("Using " + f.name + ". Click a watered empty plot or a growing plant.", false);
    }

    private void buyLargerPlot() {
        PlayerData.Result r = data.expandGarden();
        toast(r.message, !r.ok);
        AudioManager.getInstance().play(r.ok ? "buy" : "error");
        if (r.ok) autosave();
    }

    private void manualSave() {
        if (frame.saveGame()) toast("Game saved to \"" + data.getSaveName() + "\"!", false);
        else toast("Could not save: " + frame.getLastSaveError(), true);
    }

    // ------------------------------------------------------------------ clicking plots
    private void clickCell(int r, int c) {
        data.update();                                    // flowers may have just died or ripened
        handleEvents(data.drainEvents());
        GardenPlot p = data.getPlot(r, c);
        PlayerData.Result res = null;
        String sfx = null;                                // sound played when the action succeeds
        if (p.isMature()) {
            res = data.harvest(r, c);
            sfx = res.newDiscovery ? "discover" : "harvest";
            if (res.ok) addFloater("+$" + res.amount, r, c, res.newDiscovery ? new Color(0xFFD23F) : new Color(0x7CFF7C));
        } else if (tool == Tool.WATER) {
            res = data.water(r, c);
            sfx = "water";
        } else if (tool == Tool.FERTILIZER) {
            res = data.applyFertilizer(r, c, activeFertilizer);
            sfx = "fertilize";
        } else if (p.isEmpty()) {
            res = data.plant(r, c);
            sfx = "plant";
        } else {
            toast(p.getFlower().name + " is still growing - " + (p.remainingMs() / 1000 + 1) + "s left. Fertilizer makes it faster!", true);
            AudioManager.getInstance().play("error");
        }
        if (res != null) {
            toast(res.message, !res.ok);
            AudioManager.getInstance().play(res.ok ? sfx : "error");
            if (res.ok) autosave();                       // every important action is written to the save file
        }
    }

    /** Tells the player about flowers that ripened or died by themselves. */
    private void handleEvents(List<PlayerData.PlotEvent> events) {
        int died = 0;
        PlayerData.PlotEvent lastDied = null, lastRipe = null;
        for (PlayerData.PlotEvent e : events) {
            if (e.type == GardenPlot.Event.DIED) {
                died++; lastDied = e;
                if (getWidth() > 0) addFloater("withered!", e.row, e.col, new Color(0xFF6B6B));
            } else lastRipe = e;
        }
        if (died > 1) toast(died + " flowers withered because they were not harvested in time!", true);
        else if (died == 1) toast("Your " + lastDied.flowerName + " withered - it was not harvested in time! No reward. Water the soil again to replant.", true);
        else if (lastRipe != null) toast(lastRipe.flowerName + " is ready! Harvest it within " + GameConfig.DEATH_MS / 1000 + " seconds.", false);
        if (died > 0) { autosave(); AudioManager.getInstance().play("wither"); }                         // store the reset plots right away
    }

    public void toast(String text, boolean error) {
        toastText = text;
        toastError = error;
        toastUntil = System.currentTimeMillis() + 3600;
    }

    /** Writes the game to its save file (through GameFrame). A failed save is shown, never ignored. */
    public void autosave() {
        sinceSave = 0;
        if (!frame.saveGame()) toast("Could not save: " + frame.getLastSaveError(), true);
    }

    private void addFloater(String text, int r, int c, Color color) {
        computeGeometry();
        Floater f = new Floater();
        f.text = text; f.color = color;
        f.x = gcx + (c - r) * gtw / 2;
        f.y = gtop + (c + r + 1) * gth / 2 - gtw * 0.45;
        floaters.add(f);
    }

    @Override protected void onTick(long dtMs) {
        data.update();                                    // plants grow from real timestamps (never blocks the UI)
        handleEvents(data.drainEvents());
        if (tool == Tool.FERTILIZER && (activeFertilizer == null || data.getFertilizers().count(activeFertilizer) == 0)) {
            if (activeFertilizer != null) toast("You used up your last " + activeFertilizer.name + ".", false);
            dropTool();
        }
        shop.refresh(data, tool == Tool.WATER, tool == Tool.FERTILIZER);
        for (Iterator<Floater> it = floaters.iterator(); it.hasNext(); ) {
            Floater f = it.next();
            f.age += dtMs / 1000.0;
            if (f.age > 1.4) it.remove();
        }
        sinceSave += dtMs;
        if (sinceSave > GameConfig.AUTOSAVE_MS) autosave();
    }

    // ------------------------------------------------------------------ geometry
    private int shopWidth() { return (int) Math.max(220, Math.min(300, getWidth() * 0.24)); }

    private void computeGeometry() {
        int w = getWidth(), h = getHeight();
        gn = data.getGardenSize();
        double left = w * 0.03, right = w - shopWidth() - w * 0.05;
        double topY = h * 0.22, bottomY = h * 0.87;
        double availW = right - left, availH = bottomY - topY;
        gtw = Math.min(availW / gn, availH / (gn / 2.0 + 0.5));
        gth = gtw / 2;
        gd = gtw * 0.5;                                       // how deep the block is
        gcx = (left + right) / 2;
        gtop = topY + (availH - (gn * gth + gd)) / 2;
    }

    private double cellX(int r, int c) { return gcx + (c - r) * gtw / 2; }
    private double cellY(int r, int c) { return gtop + (c + r + 1) * gth / 2; }

    private void updateHover(int mx, int my) {
        computeGeometry();
        double a = (mx - gcx) / (gtw / 2), b = (my - gtop) / (gth / 2);
        double gc = (a + b) / 2, gr = (b - a) / 2;
        if (gc >= 0 && gr >= 0 && gc < gn && gr < gn && !shop.getBounds().contains(mx, my)) { hoverC = (int) gc; hoverR = (int) gr; }
        else hoverR = hoverC = -1;
    }

    // ------------------------------------------------------------------ layout & painting
    @Override public void doLayout() {
        int w = getWidth(), h = getHeight();
        seedDialog.setBounds(0, 0, w, h);
        fertilizerDialog.setBounds(0, 0, w, h);
        back.setBounds(16, 16, (int) (w * 0.17) + 4, (int) (h * 0.10) + 4);
        sound.setBounds(16, back.getY() + back.getHeight() + 8, back.getWidth(), (int) (h * 0.07) + 4);
        int sw = shopWidth();
        shop.setBounds(w - sw - (int) (w * 0.03), (int) (h * 0.07), sw, (int) (h * 0.80));
    }

    private static Polygon diamond(double cx, double cy, double hw, double hh) {
        return new Polygon(new int[]{(int) (cx - hw), (int) cx, (int) (cx + hw), (int) cx},
                           new int[]{(int) cy, (int) (cy - hh), (int) cy, (int) (cy + hh)}, 4);
    }

    @Override protected void paintScreen(Graphics2D g, int w, int h) {
        GameRenderer.hills(g, w, h);
        computeGeometry();
        long now = data.now();
        paintSlab(g);

        // soil tiles first ...
        for (int r = 0; r < gn; r++) for (int c = 0; c < gn; c++) paintTile(g, r, c);
        // ... then plants, back to front, so nearer plants overlap farther ones
        for (int sum = 0; sum <= 2 * gn - 2; sum++)
            for (int r = 0; r < gn; r++) {
                int c = sum - r;
                if (c < 0 || c >= gn) continue;
                GardenPlot p = data.getPlot(r, c);
                if (p.isEmpty()) continue;
                paintPlant(g, p, r, c, now);
            }

        for (Floater f : floaters) {
            float a = (float) Math.max(0, 1 - f.age / 1.4);
            Color col = new Color(f.color.getRed(), f.color.getGreen(), f.color.getBlue(), (int) (255 * a));
            GameRenderer.text(g, f.text, (int) f.x, (int) (f.y - f.age * 50), h * 0.05f, col, new Color(0, 0, 0, (int) (200 * a)), 0);
        }
        paintHud(g, w, h);
        int bannerBottom = paintToast(g, w, h);
        paintHoverInfo(g, now, bannerBottom);
    }

    private void paintPlant(Graphics2D g, GardenPlot p, int r, int c, long now) {
        double x = cellX(r, c), y = cellY(r, c) + gth * 0.12;
        long untilDeath = p.msUntilDeath(now);
        boolean warning = p.isMature() && untilDeath <= GameConfig.WARNING_MS;
        boolean wilted = warning && ((long) (time * 3) % 2 == 0);          // blinks while the flower is nearly dead
        GameRenderer.plant(g, p, x, y, gtw * 0.44, time, wilted);

        int bw = (int) (gtw * 0.34), bh = Math.max(4, (int) (gtw * 0.04));
        int bx = (int) (x - bw / 2.0), by = (int) (cellY(r, c) + gth * 0.2);
        double fraction; Color barColor;
        if (p.isMature()) {                                                  // ripe: bar = time left before it dies
            fraction = Math.min(1.0, untilDeath / (double) GameConfig.DEATH_MS);
            barColor = warning ? new Color(0xFF4A4A) : (fraction < 0.6 ? new Color(0xFFD23F) : new Color(0x7CFF7C));
            double s = Math.sin(time * 4 + r * 2 + c) * 0.5 + 0.5;
            if (!warning) {
                GameRenderer.sparkle(g, x + gtw * 0.2, y - gtw * 0.55, gtw * 0.03 + s * gtw * 0.03, new Color(0xFFF59D));
                GameRenderer.sparkle(g, x - gtw * 0.22, y - gtw * 0.38, gtw * 0.06 - s * gtw * 0.03, Color.WHITE);
            } else {                                  // red countdown at the corner of the head (small, so a full garden stays readable)
                GameRenderer.text(g, (untilDeath / 1000 + 1) + "s", (int) (x + gtw * 0.21), (int) (y - gtw * 0.5),
                        Math.max(12f, (float) (gtw * 0.13)), new Color(0xFF3B3B), Color.WHITE, 0);
            }
        } else {                                                             // growing: bar = growth progress
            fraction = p.progress();
            barColor = new Color(0x7CFF7C);
        }
        g.setColor(GameRenderer.BLACK); g.fillRect(bx - 2, by - 2, bw + 4, bh + 4);
        g.setColor(new Color(0x5B5B5B)); g.fillRect(bx, by, bw, bh);
        g.setColor(barColor); g.fillRect(bx, by, (int) (bw * fraction), bh);
    }

    /**
     * The grass block the garden sits on. Each of its three visible faces is a small pixel texture from PixelArt
     * that is stretched onto the face with an AffineTransform (which is what gives the chunky, slanted pixels).
     */
    private void paintSlab(Graphics2D g) {
        double half = gn * gtw / 2, deep = gn * gth;
        double lx = gcx - half, rx = gcx + half, my = gtop + deep / 2, by = gtop + deep;
        double t = gn * PixelArt.TEXELS;                      // texture pixels along one edge of the block
        double ux = half / t, uy = deep / 2 / t, vy = gd / PixelArt.TEXELS;
        g.drawImage(PixelArt.grassTop(gn), new AffineTransform(ux, uy, -ux, uy, gcx, gtop), null);        // top
        g.drawImage(PixelArt.dirtSide(gn, false), new AffineTransform(ux, uy, 0, vy, lx, my), null);      // left side
        g.drawImage(PixelArt.dirtSide(gn, true), new AffineTransform(ux, -uy, 0, vy, gcx, by), null);     // right side

        // thin dark lines between the plots, then the black outline of the block
        g.setColor(new Color(0, 0, 0, 55));
        g.setStroke(new BasicStroke(Math.max(2f, (float) gtw * 0.02f)));
        for (int i = 1; i < gn; i++) {
            double dx = i * gtw / 2, dy = i * gth / 2;
            g.drawLine((int) (gcx + dx), (int) (gtop + dy), (int) (lx + dx), (int) (my + dy));
            g.drawLine((int) (gcx - dx), (int) (gtop + dy), (int) (rx - dx), (int) (my + dy));
        }
        Path2D outline = new Path2D.Double();
        outline.moveTo(lx, my); outline.lineTo(gcx, gtop); outline.lineTo(rx, my); outline.lineTo(rx, my + gd);
        outline.lineTo(gcx, by + gd); outline.lineTo(lx, my + gd); outline.closePath();
        outline.moveTo(lx, my); outline.lineTo(gcx, by); outline.lineTo(rx, my);      // front edges of the top
        outline.moveTo(gcx, by); outline.lineTo(gcx, by + gd);                        // front corner
        g.setColor(GameRenderer.BLACK);
        g.setStroke(new BasicStroke(Math.max(2f, (float) gtw * 0.025f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(outline);
        g.setStroke(new BasicStroke(1));
    }

    // little offsets (fractions of the soil patch) used for cracks, droplets and fertilizer speckles
    private static final double[][] SPOTS = { {-0.45, 0.0}, {0.4, 0.1}, {0.0, -0.45}, {0.05, 0.45}, {-0.2, 0.22}, {0.2, -0.2}, {-0.15, -0.2}, {0.22, 0.28} };

    private void paintTile(Graphics2D g, int r, int c) {
        double x = cellX(r, c), y = cellY(r, c);
        GardenPlot p = data.getPlot(r, c);
        double hw = gtw / 2 * 0.9, hh = gth / 2 * 0.9;         // (the grass around the soil is part of the block texture)

        // the soil: DRY = light dusty tan with cracks, WET (watered) = dark with blue droplets
        double sw = hw * 0.66, sh = hh * 0.66;
        int dot = Math.max(2, (int) (gtw * 0.022));
        if (p.isWatered()) {
            g.setColor(new Color(0x4A2D18)); g.fillPolygon(diamond(x, y, sw, sh));
            g.setColor(new Color(0x2A180B)); g.drawPolygon(diamond(x, y, sw, sh));
            g.setColor(new Color(0x6FB4EA));
            for (int i = 0; i < 4; i++) g.fillRect((int) (x + SPOTS[i][0] * sw), (int) (y + SPOTS[i][1] * sh), dot, dot);
        } else {
            g.setColor(new Color(0xCDA874)); g.fillPolygon(diamond(x, y, sw, sh));
            g.setColor(new Color(0x8E6B3E)); g.drawPolygon(diamond(x, y, sw, sh));
            for (int i = 0; i < 3; i++) {
                int px = (int) (x + SPOTS[i + 4][0] * sw), py = (int) (y + SPOTS[i + 4][1] * sh);
                g.drawLine(px, py, px + dot * 2, py + dot);
            }
        }
        // active fertilizer: speckles in its colour on the soil
        if (p.getFertilizer() != null) {
            for (int i = 0; i < SPOTS.length; i++) {
                int px = (int) (x + SPOTS[i][0] * sw * 0.9), py = (int) (y + SPOTS[i][1] * sh * 0.9);
                g.setColor(GameRenderer.BLACK); g.fillRect(px - 1, py - 1, dot + 2, dot + 2);
                g.setColor(p.getFertilizer().color); g.fillRect(px, py, dot, dot);
            }
        }

        if (r == hoverR && c == hoverC) {
            Color outline = Color.WHITE;
            if (p.isMature()) outline = new Color(0xFFD23F);
            else if (tool == Tool.WATER) outline = new Color(0x6FB4EA);
            else if (tool == Tool.FERTILIZER) outline = new Color(0xFFEB3B);
            g.setColor(outline);
            g.setStroke(new BasicStroke(Math.max(3f, (float) gtw * 0.025f)));
            g.drawPolygon(diamond(x, y, hw, hh));
            g.setStroke(new BasicStroke(1));
        } else if (p.isMature()) {
            g.setColor(new Color(255, 235, 59, 150));
            g.setStroke(new BasicStroke(Math.max(2f, (float) gtw * 0.015f)));
            g.drawPolygon(diamond(x, y, hw, hh));
            g.setStroke(new BasicStroke(1));
        }
    }

    /** Small label above the hovered plot explaining its state and what a click will do. minY = bottom of the banner. */
    private void paintHoverInfo(Graphics2D g, long now, int minY) {
        if (hoverR < 0 || seedDialog.isVisible() || fertilizerDialog.isVisible()) return;
        GardenPlot p = data.getPlot(hoverR, hoverC);
        String fert = p.getFertilizer() == null ? "" : "  [" + p.getFertilizer().name + "]";
        String t;
        if (p.isEmpty()) {
            if (!p.isWatered()) t = tool == Tool.WATER ? "Dry soil - click to water it" : "Dry soil - use WATER before planting";
            else if (tool == Tool.FERTILIZER) t = "Watered soil" + fert + " - click to add " + activeFertilizer.name;
            else t = "Watered soil" + fert + (data.getSeeds().total() > 0 ? " - click to plant a random seed" : " - buy SEEDS to plant");
        } else if (p.isMature()) {
            t = p.getFlower().name + " is ready! Click to harvest (+$" + p.getFlower().reward + ") - dies in " + (p.msUntilDeath(now) / 1000 + 1) + "s";
        } else {
            t = p.getFlower().name + " (" + p.getFlower().rarity.label + ") - " + (p.remainingMs() / 1000 + 1) + "s left" + fert
                + (tool == Tool.FERTILIZER ? " - click to fertilize" : "");
        }
        int y = Math.max(minY + 6, (int) (cellY(hoverR, hoverC) - gtw * 0.95));
        // the buttons are drawn on top of the garden, so the label must stay beside them, never under them
        int left = y < sound.getY() + sound.getHeight() + 6 ? sound.getX() + sound.getWidth() + 8 : 8;
        int right = shop.getX() - 8;
        float size = GameRenderer.fitSize(g, t, Math.max(12f, getHeight() * 0.026f), right - left - 24);
        g.setFont(GameRenderer.font(size));
        int tw = g.getFontMetrics().stringWidth(t) + 24, th = (int) (size * 1.9);
        int x = (int) Math.max(left, Math.min(right - tw, cellX(hoverR, hoverC) - tw / 2.0));
        GameRenderer.box(g, x, y, tw, th, new Color(0xFFF1CF), 3);
        GameRenderer.text(g, t, x + tw / 2, y + (int) (th * 0.68), size, new Color(0x2B1D1D), null, 0);
    }

    /** Bottom bar: coins, seeds, fertilizers and the tool in your hand (like the reference screenshot). */
    private void paintHud(Graphics2D g, int w, int h) {
        int icon = (int) (h * 0.075);
        float size = h * 0.05f, small = h * 0.03f;
        int y = (int) (h * 0.885), baseline = y + (int) (icon * 0.72);
        String money = String.valueOf(data.getMoney());
        String seeds = String.valueOf(data.getSeeds().total());
        String ferts = String.valueOf(data.getFertilizers().total());
        String toolText = tool == Tool.WATER ? "Tool: WATER"
                : tool == Tool.FERTILIZER ? "Tool: " + activeFertilizer.name + " x" + data.getFertilizers().count(activeFertilizer)
                : "Tool: none (click to plant)";
        g.setFont(GameRenderer.font(size));
        FontMetrics fm = g.getFontMetrics();
        g.setFont(GameRenderer.font(small));
        int toolW = g.getFontMetrics().stringWidth(toolText);
        int gap = (int) (w * 0.03), pad = 8;
        int total = 3 * (icon + pad) + fm.stringWidth(money) + fm.stringWidth(seeds) + fm.stringWidth(ferts) + 3 * gap + toolW;
        int x = Math.max(12, (int) gcx - total / 2);            // centred under the garden, but never off the left edge
        GameRenderer.coin(g, x, y, icon);
        GameRenderer.text(g, money, x + icon + pad, baseline, size, Color.WHITE, GameRenderer.BLACK, -1);
        x += icon + pad + fm.stringWidth(money) + gap;
        GameRenderer.seedBag(g, x, y, icon);
        GameRenderer.text(g, seeds, x + icon + pad, baseline, size, Color.WHITE, GameRenderer.BLACK, -1);
        x += icon + pad + fm.stringWidth(seeds) + gap;
        Fertilizer shown = activeFertilizer != null ? activeFertilizer : FertilizerRegistry.ALL.get(0);
        PixelArt.draw(g, PixelArt.icon(shown.id, PixelArt.fertilizerBag(shown)), x, y, icon, icon);
        GameRenderer.text(g, ferts, x + icon + pad, baseline, size, Color.WHITE, GameRenderer.BLACK, -1);
        x += icon + pad + fm.stringWidth(ferts) + gap;
        small = GameRenderer.fitSize(g, toolText, small, w - 12 - x);      // shrink if a big wallet leaves little room
        GameRenderer.text(g, toolText, x, baseline, small, tool == Tool.NONE ? Color.WHITE : new Color(0xFFE23B), GameRenderer.BLACK, -1);
    }

    /**
     * The message banner at the top: the latest toast, or the hint of the tool in your hand.
     * It stays between the BACK button and the shop; a long message wraps onto two lines.
     * Returns the y of its bottom edge (0 when nothing is shown).
     */
    private int paintToast(Graphics2D g, int w, int h) {
        String t = null;
        float alpha = 1f;
        long msLeft = toastUntil - System.currentTimeMillis();
        if (tool == Tool.WATER) t = "WATER ON: click empty plots to water them. Click WATER or press ESC to stop.";
        else if (tool == Tool.FERTILIZER) t = activeFertilizer.name + " selected: click a watered empty plot or a growing plant. ESC to stop.";
        boolean showingToast = msLeft > 0 && !toastText.isEmpty();
        if (showingToast) { t = toastText; alpha = Math.min(1f, msLeft / 500f); }
        if (t == null) return 0;
        int left = back.getX() + back.getWidth() + 12, right = shop.getX() - 12;
        float size = Math.max(13f, h * 0.03f);
        List<String> lines;
        while (true) {                                          // wrap; only shrink the text if two lines are not enough
            g.setFont(GameRenderer.font(size));
            lines = GameRenderer.wrap(t, g.getFontMetrics(), right - left - 28);
            if (lines.size() <= 2 || size <= 10) break;
            size -= 1;
        }
        FontMetrics fm = g.getFontMetrics();
        int tw = 28, lineH = (int) (size * 1.25), pad = (int) (size * 0.45);
        for (String line : lines) tw = Math.max(tw, fm.stringWidth(line) + 28);
        int th = 2 * pad + lines.size() * lineH;
        int x = Math.max(left, Math.min(right - tw, (int) gcx - tw / 2)), y = (int) (h * 0.025);
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        boolean err = showingToast && toastError;
        Color fill = err ? new Color(0xE57373) : (showingToast ? new Color(0xFFF1CF) : new Color(0xFFE082));
        GameRenderer.box(g, x, y, tw, th, fill, 3);
        for (int i = 0; i < lines.size(); i++)
            GameRenderer.text(g, lines.get(i), x + tw / 2, y + pad + (int) (size * 0.95) + i * lineH, size,
                    err ? Color.WHITE : new Color(0x2B1D1D), err ? new Color(0x5A1010) : null, 0);
        g.setComposite(old);
        return y + th;
    }
}
