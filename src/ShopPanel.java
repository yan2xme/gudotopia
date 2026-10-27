import javax.swing.*;
import java.awt.*;

/**
 * The right-hand panel. Top group "Buy:" = FERTILIZER, SEEDS, LARGER PLOT.
 * Bottom group = garden tools and menu: WATER, FLOWERS (collection), SAVE.
 */
public class ShopPanel extends JPanel {
    private final PixelButton fertilizer, seeds, larger, water, collection, save;

    public ShopPanel(Runnable onFertilizer, Runnable onSeeds, Runnable onLarger,
                     Runnable onWater, Runnable onCollection, Runnable onSave) {
        setLayout(null);
        setOpaque(false);
        fertilizer = new PixelButton("FERTILIZER $" + FertilizerRegistry.SHOP_PRICE, onFertilizer);
        seeds = new PixelButton("SEEDS $" + PlayerData.SEED_COST, onSeeds);
        larger = new PixelButton("LARGER PLOT", onLarger);
        water = new PixelButton("WATER", onWater);
        collection = new PixelButton("FLOWERS", onCollection);
        save = new PixelButton("SAVE", onSave);
        add(fertilizer); add(seeds); add(larger); add(water); add(collection); add(save);
    }

    /** Update labels (the garden price grows) and highlight the tool that is switched on. */
    public void refresh(PlayerData d, boolean waterOn, boolean fertilizerOn) {
        water.setActive(waterOn);
        fertilizer.setActive(fertilizerOn);
        int cost = d.nextExpandCost();
        larger.setLabel(cost < 0 ? "MAX SIZE" : "LARGER PLOT $" + cost);
        larger.setEnabled(cost >= 0);
    }

    private int buttonHeight() { return (int) Math.min(58, getHeight() / 9.1); }

    @Override public void doLayout() {
        int bh = buttonHeight(), gap = (int) (bh * 0.3), w = getWidth();
        int y = (int) (bh * 0.95);
        for (PixelButton b : new PixelButton[]{fertilizer, seeds, larger}) { b.setBounds(0, y, w, bh + 6); y += bh + gap; }
        y += gap;                                          // extra space between the two groups
        for (PixelButton b : new PixelButton[]{water, collection, save}) { b.setBounds(0, y, w, bh + 6); y += bh + gap; }
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        GameRenderer.smooth(g);
        GameRenderer.text(g, "Buy:", getWidth() - 8, (int) (buttonHeight() * 0.7), buttonHeight() * 0.6f, new Color(0x1B1B1B), null, 1);
        g.dispose();
    }
}
