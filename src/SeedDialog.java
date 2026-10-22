import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * Pop-up seed shop (drawn on top of the garden):
 *  - BUY SEED $2 gives one RANDOM flower seed (rarer = lower drop rate)
 *  - the grid lists your seeds; click one to select it for planting
 */
public class SeedDialog extends JPanel {
    private final PlayerData data;
    private final GardenPanel owner;
    private final PixelButton buy, close;
    private Flower lastSeed;          // the seed received by the latest purchase
    private int scrollRows, hoverIndex = -1;
    private Rectangle dlg = new Rectangle(), grid = new Rectangle();
    private static final int PAD = 18, CELL_W = 86, CELL_H = 94, GAP = 8;

    public SeedDialog(GardenPanel owner, PlayerData data) {
        this.owner = owner;
        this.data = data;
        setLayout(null);
        setOpaque(false);
        setVisible(false);
        buy = new PixelButton("BUY SEED $" + PlayerData.SEED_COST, this::buySeed);
        close = new PixelButton("CLOSE", this::closeDialog);
        add(buy); add(close);

        MouseAdapter m = new MouseAdapter() {   // having listeners also stops clicks from reaching the garden below
            @Override public void mousePressed(MouseEvent e) { clickCell(e.getPoint()); }
            @Override public void mouseMoved(MouseEvent e) { hoverIndex = cellAt(e.getPoint()); repaint(); }
            @Override public void mouseWheelMoved(MouseWheelEvent e) { scrollRows = Math.max(0, scrollRows + e.getWheelRotation()); repaint(); }
        };
        addMouseListener(m); addMouseMotionListener(m); addMouseWheelListener(m);
    }

    public void open() { scrollRows = 0; setVisible(true); repaint(); }
    private void closeDialog() { setVisible(false); owner.repaint(); }

    private void buySeed() {
        PlayerData.Result r = data.buySeed();
        if (r.ok) { lastSeed = r.flower; owner.autosave(); }
        else owner.toast(r.message, true);
        repaint();
    }

    private int cols() { return Math.max(1, (grid.width + GAP) / (CELL_W + GAP)); }

    private int cellAt(Point p) {
        if (!grid.contains(p)) return -1;
        int col = (p.x - grid.x) / (CELL_W + GAP), row = (p.y - grid.y) / (CELL_H + GAP);
        if ((p.x - grid.x) % (CELL_W + GAP) > CELL_W || (p.y - grid.y) % (CELL_H + GAP) > CELL_H || col >= cols()) return -1;
        int idx = (row + scrollRows) * cols() + col;
        return idx < data.getSeeds().ownedFlowers().size() ? idx : -1;
    }

    private void clickCell(Point p) {
        int idx = cellAt(p);
        if (idx < 0) return;
        Flower f = data.getSeeds().ownedFlowers().get(idx);
        data.getSeeds().select(f);
        owner.toast("Selected " + f.name + " seed - click an empty plot to plant it.", false);
        owner.autosave();
        closeDialog();
    }

    @Override public void doLayout() {
        int w = getWidth(), h = getHeight();
        int dw = Math.min(740, w - 40), dh = Math.min(500, h - 40);
        dlg = new Rectangle((w - dw) / 2, (h - dh) / 2, dw, dh);
        int leftW = (int) (dw * 0.36);
        buy.setBounds(dlg.x + PAD, dlg.y + 76, leftW - PAD, 58);
        close.setBounds(dlg.x + dw / 2 - 75, dlg.y + dh - 62, 150, 50);
        grid = new Rectangle(dlg.x + leftW + PAD, dlg.y + 104, dw - leftW - PAD * 2, dh - 104 - 74);
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        GameRenderer.smooth(g);
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(0, 0, getWidth(), getHeight());
        GameRenderer.box(g, dlg.x, dlg.y, dlg.width, dlg.height, new Color(0xFFF1CF), 6);
        GameRenderer.text(g, "SEED SHOP", dlg.x + dlg.width / 2, dlg.y + 50, 34, new Color(0x4A9A4E), new Color(0x1F3D22), 0);

        int leftW = (int) (dlg.width * 0.36);
        // --- last purchase card
        int cx = dlg.x + PAD, cy = dlg.y + 146, cw = leftW - PAD, ch = 108;
        GameRenderer.box(g, cx, cy, cw, ch, new Color(0xFFFFFF), 4);
        if (lastSeed == null) {
            GameRenderer.text(g, "Buy a seed to", cx + cw / 2, cy + ch / 2 - 4, 15, new Color(0x555555), null, 0);
            GameRenderer.text(g, "discover a flower!", cx + cw / 2, cy + ch / 2 + 16, 15, new Color(0x555555), null, 0);
        } else {
            PixelArt.draw(g, PixelArt.head(lastSeed), cx + 12, cy + 18, 64, 64);
            float nameSize = GameRenderer.fitSize(g, lastSeed.name, 18, cw - 100);
            GameRenderer.text(g, lastSeed.name, cx + 86, cy + 44, nameSize, new Color(0x2B1D1D), null, -1);
            GameRenderer.text(g, lastSeed.rarity.label, cx + 86, cy + 68, 16, lastSeed.rarity.color.darker(), null, -1);
            GameRenderer.text(g, "You got:", cx + 10, cy + 16, 12, new Color(0x777777), null, -1);
        }
        // --- drop rates
        int ry = cy + ch + 26;
        GameRenderer.text(g, "Drop rates", cx, ry, 14, new Color(0x3B2B1D), null, -1);
        for (Rarity r : Rarity.values()) {
            ry += 19;
            g.setColor(r.color);
            g.fillRect(cx, ry - 11, 12, 12);
            g.setColor(GameRenderer.BLACK);
            g.drawRect(cx, ry - 11, 12, 12);
            GameRenderer.text(g, r.label + "  " + r.dropPercent + "%", cx + 20, ry, 13, new Color(0x3B2B1D), null, -1);
        }
        // money
        GameRenderer.coin(g, cx, dlg.y + dlg.height - 52, 22);
        GameRenderer.text(g, "" + data.getMoney(), cx + 30, dlg.y + dlg.height - 34, 18, new Color(0x3B2B1D), null, -1);

        // --- seed grid
        int gx = grid.x;
        GameRenderer.text(g, "YOUR SEEDS  (click one to select)", gx, dlg.y + 92, 15, new Color(0x3B2B1D), null, -1);
        List<Flower> owned = data.getSeeds().ownedFlowers();
        if (owned.isEmpty()) {
            GameRenderer.text(g, "No seeds yet - buy some!", grid.x + grid.width / 2, grid.y + grid.height / 2, 16, new Color(0x777777), null, 0);
        }
        int cols = cols(), rows = Math.max(1, (grid.height + GAP) / (CELL_H + GAP));
        int maxScroll = Math.max(0, (owned.size() + cols - 1) / cols - rows);
        scrollRows = Math.min(scrollRows, maxScroll);
        Flower selected = data.getSeeds().getSelected();
        Shape oldClip = g.getClip();
        g.setClip(grid);
        for (int i = scrollRows * cols; i < owned.size() && i < (scrollRows + rows) * cols; i++) {
            Flower f = owned.get(i);
            int col = i % cols, row = i / cols - scrollRows;
            int x = grid.x + col * (CELL_W + GAP), y = grid.y + row * (CELL_H + GAP);
            boolean sel = f == selected;
            GameRenderer.box(g, x, y, CELL_W, CELL_H, i == hoverIndex ? new Color(0xFFF7DD) : Color.WHITE, 3);
            if (sel) { g.setColor(new Color(0xFFD23F)); g.setStroke(new BasicStroke(3)); g.drawRect(x + 3, y + 3, CELL_W - 6, CELL_H - 6); g.setStroke(new BasicStroke(1)); }
            PixelArt.draw(g, PixelArt.head(f), x + (CELL_W - 48) / 2.0, y + 8, 48, 48);
            float ns = GameRenderer.fitSize(g, f.name, 12, CELL_W - 10);
            GameRenderer.text(g, f.name, x + CELL_W / 2, y + 70, ns, new Color(0x2B1D1D), null, 0);
            g.setColor(f.rarity.color);
            g.fillRect(x + 6, y + CELL_H - 14, CELL_W - 12, 6);
            GameRenderer.text(g, "x" + data.getSeeds().count(f), x + CELL_W - 8, y + 24, 14, Color.WHITE, GameRenderer.BLACK, 1);
        }
        g.setClip(oldClip);
        if (maxScroll > 0) GameRenderer.text(g, "scroll for more", grid.x + grid.width, grid.y + grid.height + 14, 11, new Color(0x777777), null, 1);
        g.dispose();
    }
}
