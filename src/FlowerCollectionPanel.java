import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/** The flower encyclopedia: a grid of every flower; locked ones are dark silhouettes. */
public class FlowerCollectionPanel extends ScreenPanel {
    private final PlayerData data;
    private final PixelButton back;
    private int hover = -1;

    public FlowerCollectionPanel(GameFrame frame, PlayerData data) {
        this.data = data;
        back = new PixelButton("BACK", frame::showGarden);
        add(back);
        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { hover = cardAt(e.getX(), e.getY()); }
            @Override public void mouseExited(MouseEvent e) { hover = -1; }
        };
        addMouseMotionListener(m);
        addMouseListener(m);
    }

    @Override public void doLayout() {
        back.setBounds(16, 16, (int) (getWidth() * 0.15) + 4, (int) (getHeight() * 0.09) + 4);
    }

    // ---- grid geometry (shared by painting and hit testing) ----
    private int cols, cardW, cardH, gridX, gridY, gap;

    private void computeGrid() {
        int w = getWidth(), h = getHeight(), n = FlowerRegistry.ALL.size();
        gap = Math.max(6, w / 120);
        int areaX = (int) (w * 0.04), areaY = (int) (h * 0.19), areaW = (int) (w * 0.92), areaH = (int) (h * 0.67);
        double best = -1;
        for (int c = 4; c <= 10; c++) {
            int rows = (n + c - 1) / c;
            int cw = (areaW - (c - 1) * gap) / c, ch = (areaH - (rows - 1) * gap) / rows;
            double score = Math.min(cw, ch * 0.8);
            if (score > best) { best = score; cols = c; cardW = cw; cardH = ch; }
        }
        int rows = (n + cols - 1) / cols;
        gridX = areaX + (areaW - (cols * cardW + (cols - 1) * gap)) / 2;
        gridY = areaY + (areaH - (rows * cardH + (rows - 1) * gap)) / 2;
    }

    private int cardAt(int mx, int my) {
        computeGrid();
        int col = (mx - gridX) / (cardW + gap), row = (my - gridY) / (cardH + gap);
        if (mx < gridX || my < gridY || (mx - gridX) % (cardW + gap) > cardW || (my - gridY) % (cardH + gap) > cardH || col >= cols) return -1;
        int idx = row * cols + col;
        return idx < FlowerRegistry.ALL.size() ? idx : -1;
    }

    @Override protected void paintScreen(Graphics2D g, int w, int h) {
        GameRenderer.hills(g, w, h);
        computeGrid();
        List<Flower> all = FlowerRegistry.ALL;

        GameRenderer.text(g, "FLOWER COLLECTION", w / 2, (int) (h * 0.085), h * 0.065f, Color.WHITE, new Color(0x1F3D22), 0);
        // progress text + bar
        int found = data.unlockedCount(), total = all.size();
        GameRenderer.text(g, "Flowers Discovered: " + found + " / " + total, w / 2, (int) (h * 0.135), h * 0.034f, Color.WHITE, new Color(0x2B4E8C), 0);
        int bw = (int) (w * 0.3), bh = Math.max(8, h / 70), bx = (w - bw) / 2, by = (int) (h * 0.15);
        g.setColor(GameRenderer.BLACK); g.fillRect(bx - 3, by - 3, bw + 6, bh + 6);
        g.setColor(new Color(0x3A3F55)); g.fillRect(bx, by, bw, bh);
        g.setColor(new Color(0xFFD23F)); g.fillRect(bx, by, bw * found / total, bh);

        for (int i = 0; i < all.size(); i++) {
            Flower f = all.get(i);
            boolean open = data.isUnlocked(f);
            int x = gridX + (i % cols) * (cardW + gap), y = gridY + (i / cols) * (cardH + gap);
            Color fill = open ? new Color(0xFFF1CF) : new Color(0x59607A);
            if (i == hover) fill = fill.brighter();
            GameRenderer.box(g, x, y, cardW, cardH, fill, Math.max(3, cardH / 28));
            if (open) {                                         // rarity colour stripe
                g.setColor(f.rarity.color);
                g.fillRect(x + 4, y + cardH - 10, cardW - 8, 6);
            }
            int img = (int) Math.min(cardW * 0.62, cardH * 0.50);
            PixelArt.draw(g, open ? PixelArt.head(f) : PixelArt.lockedHead(f), x + (cardW - img) / 2.0, y + cardH * 0.07, img, img);
            if (!open) GameRenderer.text(g, "?", x + cardW / 2, (int) (y + cardH * 0.07 + img * 0.68), img * 0.55f, new Color(0xB9C0DA), null, 0);
            int ty = (int) (y + cardH * 0.07 + img + cardH * 0.15);
            if (open) {
                float ns = GameRenderer.fitSize(g, f.name, cardH * 0.13f, cardW - 10);
                GameRenderer.text(g, f.name, x + cardW / 2, ty, ns, new Color(0x2B1D1D), null, 0);
                float rs = GameRenderer.fitSize(g, f.rarity.label, cardH * 0.11f, cardW - 10);
                GameRenderer.text(g, f.rarity.label, x + cardW / 2, ty + (int) (cardH * 0.13), rs, f.rarity.color.darker(), null, 0);
            } else {
                GameRenderer.text(g, "???", x + cardW / 2, ty, cardH * 0.13f, new Color(0xD5DAEE), null, 0);
                GameRenderer.text(g, "Locked", x + cardW / 2, ty + (int) (cardH * 0.13), cardH * 0.1f, new Color(0xA9B0CC), null, 0);
            }
        }

        // detail bar for the hovered flower
        String detail = "Hover over a flower to see its details.";
        if (hover >= 0) {
            Flower f = all.get(hover);
            detail = data.isUnlocked(f)
                    ? f.name + " (" + f.rarity.label + ")  -  sells for $" + f.reward + ", grows in " + f.growthSeconds + "s.  " + f.description
                    : "??? (" + f.rarity.label + ")  -  not discovered yet. Harvest this flower once to unlock it!";
        }
        int dh = (int) (h * 0.075), dy = (int) (h * 0.89);
        GameRenderer.box(g, (int) (w * 0.04), dy, (int) (w * 0.92), dh, new Color(0xFFF1CF), 4);
        float ds = GameRenderer.fitSize(g, detail, h * 0.028f, (int) (w * 0.89));
        GameRenderer.text(g, detail, w / 2, dy + dh / 2 + (int) (ds * 0.33), ds, new Color(0x3B2B1D), null, 0);
    }
}
