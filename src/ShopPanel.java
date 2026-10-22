import javax.swing.*;
import java.awt.*;

/** The right-hand "Buy:" panel: FERTILIZER, SEEDS, LARGER PLOT (+ FLOWERS collection button). */
public class ShopPanel extends JPanel {
    private final PixelButton fertilizer, seeds, larger, collection;

    public ShopPanel(Runnable onFertilizer, Runnable onSeeds, Runnable onLarger, Runnable onCollection) {
        setLayout(null);
        setOpaque(false);
        fertilizer = new PixelButton("FERTILIZER $" + PlayerData.FERTILIZER_COST, onFertilizer);
        seeds = new PixelButton("SEEDS $" + PlayerData.SEED_COST, onSeeds);
        larger = new PixelButton("LARGER PLOT", onLarger);
        collection = new PixelButton("FLOWERS", onCollection);
        add(fertilizer); add(seeds); add(larger); add(collection);
    }

    /** Update labels (prices change as the garden grows) and the fertilizer highlight. */
    public void refresh(PlayerData d, boolean fertilizerMode) {
        fertilizer.setActive(fertilizerMode);
        int cost = d.nextExpandCost();
        larger.setLabel(cost < 0 ? "MAX SIZE" : "LARGER PLOT $" + cost);
        larger.setEnabled(cost >= 0);
    }

    private int buttonHeight() { return (int) Math.min(60, getHeight() / 7.2); }

    @Override public void doLayout() {
        int bh = buttonHeight(), gap = (int) (bh * 0.35), w = getWidth();
        int y = (int) (bh * 0.95);
        for (PixelButton b : new PixelButton[]{fertilizer, seeds, larger}) {
            b.setBounds(0, y, w, bh + 6);
            y += bh + gap;
        }
        collection.setBounds(0, y + gap, w, bh + 6);
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        GameRenderer.smooth(g);
        GameRenderer.text(g, "Buy:", getWidth() - 8, (int) (buttonHeight() * 0.7), buttonHeight() * 0.6f, new Color(0x1B1B1B), null, 1);
        g.dispose();
    }
}
