import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * Pop-up shop window drawn over the garden, shared by the SEEDS and FERTILIZER shops.
 * Left side: BUY button, the item you just received and the drop chances.
 * Right side: a grid of what you own. Subclasses supply the items and what clicking one does.
 */
public abstract class ItemDialog extends JPanel {

    /** Draws a small icon. */
    protected interface IconPainter { void paint(Graphics2D g, double x, double y, double size); }

    /** One thing shown in the dialog (a seed type or a fertilizer type). */
    protected static final class Entry {
        final String name, detail; final Rarity rarity; final int count; final Object payload; final IconPainter icon;
        Entry(String name, String detail, Rarity rarity, int count, Object payload, IconPainter icon) {
            this.name = name; this.detail = detail; this.rarity = rarity; this.count = count; this.payload = payload; this.icon = icon;
        }
    }

    /** One coloured line of the "drop rates" list. */
    protected static final class RateLine {
        final Color color; final String text;
        RateLine(Color color, String text) { this.color = color; this.text = text; }
    }

    protected final PlayerData data;
    protected final GardenPanel owner;
    private final PixelButton buy, close;
    private Entry lastEntry;                    // what the latest purchase gave
    private int scrollRows, hoverIndex = -1;
    private Rectangle dlg = new Rectangle(), grid = new Rectangle();
    private static final int PAD = 18, CELL_W = 86, CELL_H = 94, GAP = 8;

    protected ItemDialog(GardenPanel owner, PlayerData data) {
        this.owner = owner;
        this.data = data;
        setLayout(null);
        setOpaque(false);
        setVisible(false);
        buy = new PixelButton("", this::buyOne);
        close = new PixelButton("CLOSE", this::closeDialog);
        add(buy); add(close);

        MouseAdapter m = new MouseAdapter() {   // having listeners also stops clicks from reaching the garden below
            @Override public void mousePressed(MouseEvent e) { clickCell(e.getPoint()); }
            @Override public void mouseMoved(MouseEvent e) { hoverIndex = cellAt(e.getPoint()); repaint(); }
            @Override public void mouseWheelMoved(MouseWheelEvent e) { scrollRows = Math.max(0, scrollRows + e.getWheelRotation()); repaint(); }
        };
        addMouseListener(m); addMouseMotionListener(m); addMouseWheelListener(m);
    }

    // ---- what a subclass must say
    protected abstract String title();
    protected abstract String buyLabel();
    protected abstract String listHeader();
    protected abstract String ratesHeader();
    protected abstract PlayerData.Result doBuy();
    protected abstract Entry entryFor(PlayerData.Result result);
    protected abstract List<Entry> entries();
    protected abstract List<RateLine> rateLines();
    /** Called when the player clicks an item in the grid; return true to close the dialog afterwards. */
    protected boolean onEntryClicked(Entry e) { return false; }

    public void open() { scrollRows = 0; lastEntry = null; setVisible(true); repaint(); }
    private void closeDialog() { setVisible(false); owner.repaint(); }

    private void buyOne() {
        PlayerData.Result r = doBuy();
        if (r.ok) { lastEntry = entryFor(r); owner.autosave(); }
        else owner.toast(r.message, true);
        repaint();
    }

    private int cols() { return Math.max(1, (grid.width + GAP) / (CELL_W + GAP)); }

    private int cellAt(Point p) {
        if (!grid.contains(p)) return -1;
        int col = (p.x - grid.x) / (CELL_W + GAP), row = (p.y - grid.y) / (CELL_H + GAP);
        if ((p.x - grid.x) % (CELL_W + GAP) > CELL_W || (p.y - grid.y) % (CELL_H + GAP) > CELL_H || col >= cols()) return -1;
        int idx = (row + scrollRows) * cols() + col;
        return idx < entries().size() ? idx : -1;
    }

    private void clickCell(Point p) {
        int idx = cellAt(p);
        if (idx < 0) return;
        if (onEntryClicked(entries().get(idx))) closeDialog();
    }

    @Override public void doLayout() {
        int w = getWidth(), h = getHeight();
        int dw = Math.min(740, w - 40), dh = Math.min(500, h - 40);
        dlg = new Rectangle((w - dw) / 2, (h - dh) / 2, dw, dh);
        int leftW = (int) (dw * 0.36);
        buy.setLabel(buyLabel());
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
        GameRenderer.text(g, title(), dlg.x + dlg.width / 2, dlg.y + 50, 34, new Color(0x4A9A4E), new Color(0x1F3D22), 0);

        int leftW = (int) (dlg.width * 0.36);
        // --- card with the item you just received
        int cx = dlg.x + PAD, cy = dlg.y + 146, cw = leftW - PAD, ch = 108;
        GameRenderer.box(g, cx, cy, cw, ch, Color.WHITE, 4);
        if (lastEntry == null) {
            GameRenderer.text(g, "Buy one to see", cx + cw / 2, cy + ch / 2 - 4, 15, new Color(0x555555), null, 0);
            GameRenderer.text(g, "what you get!", cx + cw / 2, cy + ch / 2 + 16, 15, new Color(0x555555), null, 0);
        } else {
            lastEntry.icon.paint(g, cx + 12, cy + 18, 64);
            float ns = GameRenderer.fitSize(g, lastEntry.name, 18, cw - 100);
            GameRenderer.text(g, lastEntry.name, cx + 86, cy + 44, ns, new Color(0x2B1D1D), null, -1);
            GameRenderer.text(g, lastEntry.rarity.label, cx + 86, cy + 68, 16, lastEntry.rarity.color.darker(), null, -1);
            if (!lastEntry.detail.isEmpty()) {
                float ds = GameRenderer.fitSize(g, lastEntry.detail, 12, cw - 96);
                GameRenderer.text(g, lastEntry.detail, cx + 86, cy + 90, ds, new Color(0x4A6A3A), null, -1);
            }
            GameRenderer.text(g, "You got:", cx + 10, cy + 16, 12, new Color(0x777777), null, -1);
        }
        // --- drop chances
        int ry = cy + ch + 26;
        GameRenderer.text(g, ratesHeader(), cx, ry, 14, new Color(0x3B2B1D), null, -1);
        for (RateLine line : rateLines()) {
            ry += 19;
            g.setColor(line.color);
            g.fillRect(cx, ry - 11, 12, 12);
            g.setColor(GameRenderer.BLACK);
            g.drawRect(cx, ry - 11, 12, 12);
            GameRenderer.text(g, line.text, cx + 20, ry, 13, new Color(0x3B2B1D), null, -1);
        }
        GameRenderer.coin(g, cx, dlg.y + dlg.height - 52, 22);
        GameRenderer.text(g, "" + data.getMoney(), cx + 30, dlg.y + dlg.height - 34, 18, new Color(0x3B2B1D), null, -1);

        // --- inventory grid
        GameRenderer.text(g, listHeader(), grid.x, dlg.y + 92, 15, new Color(0x3B2B1D), null, -1);
        List<Entry> owned = entries();
        if (owned.isEmpty())
            GameRenderer.text(g, "Nothing yet - buy some!", grid.x + grid.width / 2, grid.y + grid.height / 2, 16, new Color(0x777777), null, 0);
        int cols = cols(), rows = Math.max(1, (grid.height + GAP) / (CELL_H + GAP));
        int maxScroll = Math.max(0, (owned.size() + cols - 1) / cols - rows);
        scrollRows = Math.min(scrollRows, maxScroll);
        Shape oldClip = g.getClip();
        g.setClip(grid);
        for (int i = scrollRows * cols; i < owned.size() && i < (scrollRows + rows) * cols; i++) {
            Entry e = owned.get(i);
            int col = i % cols, row = i / cols - scrollRows;
            int x = grid.x + col * (CELL_W + GAP), y = grid.y + row * (CELL_H + GAP);
            GameRenderer.box(g, x, y, CELL_W, CELL_H, i == hoverIndex ? new Color(0xFFF7DD) : Color.WHITE, 3);
            e.icon.paint(g, x + (CELL_W - 48) / 2.0, y + 8, 48);
            float ns = GameRenderer.fitSize(g, e.name, 12, CELL_W - 8);
            GameRenderer.text(g, e.name, x + CELL_W / 2, y + 70, ns, new Color(0x2B1D1D), null, 0);
            g.setColor(e.rarity.color);
            g.fillRect(x + 6, y + CELL_H - 14, CELL_W - 12, 6);
            GameRenderer.text(g, "x" + e.count, x + CELL_W - 8, y + 24, 14, Color.WHITE, GameRenderer.BLACK, 1);
        }
        g.setClip(oldClip);
        if (hoverIndex >= 0 && hoverIndex < owned.size() && !owned.get(hoverIndex).detail.isEmpty()) {
            Entry e = owned.get(hoverIndex);
            float ds = GameRenderer.fitSize(g, e.name + ": " + e.detail, 12, grid.width - 80);
            GameRenderer.text(g, e.name + ": " + e.detail, grid.x, grid.y + grid.height + 14, ds, new Color(0x3B2B1D), null, -1);
        }
        if (maxScroll > 0) GameRenderer.text(g, "scroll", grid.x + grid.width, grid.y + grid.height + 14, 11, new Color(0x777777), null, 1);
        g.dispose();
    }
}
