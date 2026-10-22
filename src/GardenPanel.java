import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The gameplay screen: isometric garden, shop on the right, money at the bottom.
 * Handles mouse clicks on individual plots and forwards the rules to PlayerData.
 */
public class GardenPanel extends ScreenPanel {
    private static final long AUTOSAVE_MS = 15_000;

    private final GameFrame frame;
    private final PlayerData data;
    private final PixelButton back;
    private final ShopPanel shop;
    private final SeedDialog seedDialog;

    private boolean fertilizerMode;
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
        shop = new ShopPanel(this::toggleFertilizer, this::openSeeds, this::buyLargerPlot, frame::showCollection);
        seedDialog = new SeedDialog(this, data);
        add(seedDialog);   // added first => painted on top of everything else
        add(back);
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

        // ESC cancels fertilizer mode / closes the seed shop
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        getActionMap().put("cancel", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (seedDialog.isVisible()) seedDialog.setVisible(false); else fertilizerMode = false;
            }
        });
    }

    // ------------------------------------------------------------------ actions
    private void openSeeds() { fertilizerMode = false; seedDialog.open(); }

    private void toggleFertilizer() {
        if (fertilizerMode) { fertilizerMode = false; return; }
        if (data.getMoney() < PlayerData.FERTILIZER_COST) { toast("Not enough money! Fertilizer costs $" + PlayerData.FERTILIZER_COST + ".", true); return; }
        boolean anyGrowing = false;
        for (int r = 0; r < data.getGardenSize(); r++)
            for (int c = 0; c < data.getGardenSize(); c++) if (!data.getPlot(r, c).isEmpty() && !data.getPlot(r, c).isMature()) anyGrowing = true;
        if (!anyGrowing) { toast("Plant a seed first - fertilizer only works on growing plants.", true); return; }
        fertilizerMode = true;
    }

    private void buyLargerPlot() {
        PlayerData.Result r = data.expandGarden();
        toast(r.message, !r.ok);
        if (r.ok) autosave();
    }

    private void clickCell(int r, int c) {
        GardenPlot p = data.getPlot(r, c);
        PlayerData.Result res;
        if (p.isMature()) {
            res = data.harvest(r, c);
            if (res.ok) addFloater("+$" + res.amount, r, c, res.newDiscovery ? new Color(0xFFD23F) : new Color(0x7CFF7C));
        } else if (fertilizerMode) {
            res = data.fertilize(r, c);
            if (!res.ok && data.getMoney() < PlayerData.FERTILIZER_COST) fertilizerMode = false;
        } else if (p.isEmpty()) {
            res = data.plant(r, c);
        } else {
            res = null;
            toast(p.getFlower().name + " is still growing - " + (p.remainingMs() / 1000 + 1) + "s left. Use FERTILIZER to speed it up.", true);
        }
        if (res != null) {
            toast(res.message, !res.ok);
            if (res.ok) autosave();
        }
    }

    public void toast(String text, boolean error) {
        toastText = text;
        toastError = error;
        toastUntil = System.currentTimeMillis() + 3200;
    }

    public void autosave() { frame.saveGame(); sinceSave = 0; }

    private void addFloater(String text, int r, int c, Color color) {
        computeGeometry();
        Floater f = new Floater();
        f.text = text; f.color = color;
        f.x = gcx + (c - r) * gtw / 2;
        f.y = gtop + (c + r + 1) * gth / 2 - gtw * 0.45;
        floaters.add(f);
    }

    @Override protected void onTick(long dtMs) {
        data.update(dtMs);                               // plants grow (real elapsed time, never blocks the UI)
        shop.refresh(data, fertilizerMode);
        for (Iterator<Floater> it = floaters.iterator(); it.hasNext(); ) {
            Floater f = it.next();
            f.age += dtMs / 1000.0;
            if (f.age > 1.4) it.remove();
        }
        sinceSave += dtMs;
        if (sinceSave > AUTOSAVE_MS) autosave();
    }

    // ------------------------------------------------------------------ geometry
    private int shopWidth() { return (int) Math.max(220, Math.min(300, getWidth() * 0.24)); }

    private void computeGeometry() {
        int w = getWidth(), h = getHeight();
        gn = data.getGardenSize();
        double left = w * 0.03, right = w - shopWidth() - w * 0.05;
        double topY = h * 0.22, bottomY = h * 0.87;
        double availW = right - left, availH = bottomY - topY;
        gtw = Math.min(availW / gn, availH / (gn / 2.0 + 0.4));
        gth = gtw / 2;
        gd = gtw * 0.4;
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
        back.setBounds(16, 16, (int) (w * 0.17) + 4, (int) (h * 0.10) + 4);
        int sw = shopWidth();
        shop.setBounds(w - sw - (int) (w * 0.03), (int) (h * 0.08), sw, (int) (h * 0.68));
    }

    private static Polygon diamond(double cx, double cy, double hw, double hh) {
        return new Polygon(new int[]{(int) (cx - hw), (int) cx, (int) (cx + hw), (int) cx},
                           new int[]{(int) cy, (int) (cy - hh), (int) cy, (int) (cy + hh)}, 4);
    }

    @Override protected void paintScreen(Graphics2D g, int w, int h) {
        GameRenderer.hills(g, w, h);
        computeGeometry();
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
                double x = cellX(r, c), y = cellY(r, c) + gth * 0.12;
                GameRenderer.plant(g, p, x, y, gtw * 0.44, time);
                if (p.isMature()) {
                    double s = Math.sin(time * 4 + r * 2 + c) * 0.5 + 0.5;
                    GameRenderer.sparkle(g, x + gtw * 0.2, y - gtw * 0.55, gtw * 0.03 + s * gtw * 0.03, new Color(0xFFF59D));
                    GameRenderer.sparkle(g, x - gtw * 0.22, y - gtw * 0.38, gtw * 0.06 - s * gtw * 0.03, new Color(0xFFFFFF));
                } else {                                      // growth bar
                    int bw = (int) (gtw * 0.34), bh = Math.max(4, (int) (gtw * 0.04));
                    int bx = (int) (x - bw / 2.0), by = (int) (cellY(r, c) + gth * 0.2);
                    g.setColor(GameRenderer.BLACK); g.fillRect(bx - 2, by - 2, bw + 4, bh + 4);
                    g.setColor(new Color(0x5B5B5B)); g.fillRect(bx, by, bw, bh);
                    g.setColor(new Color(0x7CFF7C)); g.fillRect(bx, by, (int) (bw * p.progress()), bh);
                }
            }

        paintHoverInfo(g);
        for (Floater f : floaters) {
            float a = (float) Math.max(0, 1 - f.age / 1.4);
            Color col = new Color(f.color.getRed(), f.color.getGreen(), f.color.getBlue(), (int) (255 * a));
            GameRenderer.text(g, f.text, (int) f.x, (int) (f.y - f.age * 50), h * 0.05f, col, new Color(0, 0, 0, (int) (200 * a)), 0);
        }
        paintHud(g, w, h);
        paintToast(g, w, h);
    }

    private void paintSlab(Graphics2D g) {
        double half = gn * gtw / 2, deep = gn * gth;
        double lx = gcx - half, rx = gcx + half, my = gtop + deep / 2, by = gtop + deep;
        // left side (lighter) and right side (darker)
        Path2D left = new Path2D.Double();
        left.moveTo(lx, my); left.lineTo(gcx, by); left.lineTo(gcx, by + gd); left.lineTo(lx, my + gd); left.closePath();
        Path2D right = new Path2D.Double();
        right.moveTo(gcx, by); right.lineTo(rx, my); right.lineTo(rx, my + gd); right.lineTo(gcx, by + gd); right.closePath();
        g.setPaint(new GradientPaint(0, (float) my, new Color(0xA85A2E), 0, (float) (by + gd), new Color(0x4A2410)));
        g.fill(left);
        g.setPaint(new GradientPaint(0, (float) my, new Color(0x8A4824), 0, (float) (by + gd), new Color(0x3A1C0C)));
        g.fill(right);
        g.setPaint(null);
        // black top plate = the grid lines between the tiles
        Polygon top = new Polygon(new int[]{(int) lx, (int) gcx, (int) rx, (int) gcx}, new int[]{(int) my, (int) gtop, (int) my, (int) by}, 4);
        g.setColor(GameRenderer.BLACK);
        g.fillPolygon(top);
        g.setStroke(new BasicStroke(Math.max(2f, (float) gtw * 0.02f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(left); g.draw(right);
        g.setStroke(new BasicStroke(1));
    }

    private void paintTile(Graphics2D g, int r, int c) {
        double x = cellX(r, c), y = cellY(r, c);
        GardenPlot p = data.getPlot(r, c);
        double hw = gtw / 2 * 0.9, hh = gth / 2 * 0.9;
        g.setColor(new Color(0x63BA68));
        g.fillPolygon(diamond(x, y, hw, hh));
        g.setColor(new Color(0x82D486));                    // light top-left edge
        g.setStroke(new BasicStroke(Math.max(1.5f, (float) gtw * 0.012f)));
        g.drawLine((int) (x - hw), (int) y, (int) x, (int) (y - hh));
        g.setStroke(new BasicStroke(1));
        // soil patch in the middle (darker when something is planted)
        g.setColor(p.isEmpty() ? new Color(0x8B5A33) : new Color(0x5E3B22));
        g.fillPolygon(diamond(x, y, hw * 0.55, hh * 0.55));
        g.setColor(new Color(0x3A2312));
        g.drawPolygon(diamond(x, y, hw * 0.55, hh * 0.55));

        if (r == hoverR && c == hoverC) {
            Color outline = Color.WHITE;
            if (p.isMature()) outline = new Color(0xFFD23F);
            else if (fertilizerMode && !p.isEmpty()) outline = new Color(0xFFEB3B);
            
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

    /** Small label above the hovered plot explaining what a click will do. */
    private void paintHoverInfo(Graphics2D g) {
        if (hoverR < 0 || seedDialog.isVisible()) return;
        GardenPlot p = data.getPlot(hoverR, hoverC);
        String t;
        if (p.isEmpty()) {
            Flower sel = data.getSeeds().getSelected();
            t = sel == null ? "Empty plot - open SEEDS to pick a seed" : "Click to plant " + sel.name;
        } else if (p.isMature()) t = p.getFlower().name + " is ready! Click to harvest (+$" + p.getFlower().reward + ")";
        else t = p.getFlower().name + " (" + p.getFlower().rarity.label + ") - " + (p.remainingMs() / 1000 + 1) + "s left"
                + (fertilizerMode ? "  - click to fertilize ($" + PlayerData.FERTILIZER_COST + ")" : "");
        float size = Math.max(12f, getHeight() * 0.026f);
        g.setFont(GameRenderer.font(size));
        int tw = g.getFontMetrics().stringWidth(t) + 24, th = (int) (size * 1.9);
        int x = (int) Math.max(8, Math.min(getWidth() - tw - 8, cellX(hoverR, hoverC) - tw / 2.0));
        int y = (int) (cellY(hoverR, hoverC) - gtw * 0.95);
        GameRenderer.box(g, x, Math.max(4, y), tw, th, new Color(0xFFF1CF), 3);
        GameRenderer.text(g, t, x + tw / 2, Math.max(4, y) + (int) (th * 0.68), size, new Color(0x2B1D1D), null, 0);
    }

    /** Bottom bar: coins, seed count, selected seed (like the reference screenshot). */
    private void paintHud(Graphics2D g, int w, int h) {
        int icon = (int) (h * 0.075);
        float size = h * 0.05f;
        int y = (int) (h * 0.885), baseline = y + (int) (icon * 0.72);
        int x = (int) gcx - (int) (w * 0.22);
        GameRenderer.coin(g, x, y, icon);
        GameRenderer.text(g, String.valueOf(data.getMoney()), x + icon + 8, baseline, size, Color.WHITE, GameRenderer.BLACK, -1);
        x += (int) (w * 0.17);
        GameRenderer.seedBag(g, x, y, icon);
        GameRenderer.text(g, String.valueOf(data.getSeeds().total()), x + icon + 8, baseline, size, Color.WHITE, GameRenderer.BLACK, -1);
        x += (int) (w * 0.12);
        Flower sel = data.getSeeds().getSelected();
        if (sel != null) {
            PixelArt.draw(g, PixelArt.head(sel), x, y, icon, icon);
            GameRenderer.text(g, sel.name + " x" + data.getSeeds().count(sel), x + icon + 8, baseline, size * 0.6f, Color.WHITE, GameRenderer.BLACK, -1);
        } else {
            GameRenderer.text(g, "No seed selected", x, baseline, size * 0.6f, Color.WHITE, GameRenderer.BLACK, -1);
        }
    }

    private void paintToast(Graphics2D g, int w, int h) {
        String t = null;
        float alpha = 1f;
        long left = toastUntil - System.currentTimeMillis();
        if (fertilizerMode) t = "FERTILIZER ON: click a growing plant ($" + PlayerData.FERTILIZER_COST + "). Click FERTILIZER or press ESC to cancel.";
        if (left > 0 && !toastText.isEmpty()) { t = toastText; alpha = Math.min(1f, left / 500f); }
        if (t == null) return;
        float size = Math.max(13f, h * 0.03f);
        g.setFont(GameRenderer.font(size));
        int minX = back.getX() + back.getWidth() + 12;          // keep clear of the BACK button
        int tw = Math.min(w - minX - 12, g.getFontMetrics().stringWidth(t) + 40), th = (int) (size * 2.1);
        int x = Math.max(minX, (int) gcx - tw / 2), y = (int) (h * 0.025);
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        boolean err = left > 0 && toastError;
        GameRenderer.box(g, Math.max(8, x), y, tw, th, err ? new Color(0xE57373) : (fertilizerMode && left <= 0 ? new Color(0xFFE082) : new Color(0xFFF1CF)), 3);
        float fs = GameRenderer.fitSize(g, t, size, tw - 24);
        GameRenderer.text(g, t, Math.max(8, x) + tw / 2, y + (int) (th * 0.67), fs, err ? Color.WHITE : new Color(0x2B1D1D), err ? new Color(0x5A1010) : null, 0);
        g.setComposite(old);
    }
}
