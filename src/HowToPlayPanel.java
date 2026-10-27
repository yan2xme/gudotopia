import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Simple instructions screen. */
public class HowToPlayPanel extends ScreenPanel {
    private static final String[] STEPS = {
        "SEEDS:  Click SEEDS, then BUY SEED ($" + PlayerData.SEED_COST + "). Every seed is a RANDOM flower - rare ones are hard to find. Your seeds are kept in your seed bag.",
        "WATER:  Click WATER, then click an empty plot to soak the soil (it turns dark). Dry soil can NOT be planted. Click WATER again, or press ESC, to put the can away.",
        "FERTILIZER (optional):  Click FERTILIZER to buy a RANDOM fertilizer ($" + FertilizerRegistry.SHOP_PRICE + "), then click one in your stock to use it. Click a WATERED empty plot (or a growing plant) to apply it. Worm = +50% growth speed, Manure = +100%. One fertilizer per plot.",
        "PLANT:  With no tool selected, click a watered plot. A random seed from your bag is planted - more copies of a seed means it is picked more often. The order is: water, (fertilize), plant.",
        "GROW & DIE:  Flowers grow by themselves, even while you are on another screen or the game is closed. A fully grown flower must be HARVESTED within " + GameConfig.DEATH_MS / 1000 + " seconds or it dies and gives nothing! It droops and flashes when less than " + GameConfig.WARNING_MS / 1000 + " seconds are left.",
        "EARN MONEY:  Click a fully grown flower to harvest it and sell it (Common $5 ... Mythical $150). The soil dries out again, so water it before the next seed. Spend money on seeds, fertilizer and LARGER PLOT.",
        "COLLECTION:  The first time you harvest a flower it is unlocked forever in FLOWERS. Flowers that died do not count.",
        "SAVING:  The game saves automatically (and when you press SAVE or BACK). Every garden is its own save slot: NEW starts a fresh garden, LOAD continues one you made earlier."
    };
    private final PixelButton back;

    public HowToPlayPanel(GameFrame frame) {
        back = new PixelButton("BACK", frame::showMenu);
        add(back);
    }

    @Override public void doLayout() {
        back.setBounds(16, 16, (int) (getWidth() * 0.17) + 4, (int) (getHeight() * 0.1) + 4);
    }

    @Override protected void paintScreen(Graphics2D g, int w, int h) {
        GameRenderer.hills(g, w, h);
        int bx = (int) (w * 0.12), by = (int) (h * 0.14), bw = (int) (w * 0.76), bh = (int) (h * 0.76);
        GameRenderer.box(g, bx, by, bw, bh, new Color(0xFFF1CF), Math.max(4, h / 90));
        GameRenderer.text(g, "HOW TO PLAY", w / 2, by + (int) (h * 0.085), h * 0.06f, new Color(0x4A9A4E), new Color(0x1F3D22), 0);

        int x = bx + (int) (bw * 0.06), top = by + (int) (h * 0.16), maxW = (int) (bw * 0.88);
        int availH = by + bh - (int) (h * 0.04) - top;
        // pick the biggest font size whose wrapped text still fits inside the panel
        float size = Math.max(13f, h * 0.030f);
        FontMetrics fm;
        while (true) {
            g.setFont(GameRenderer.font(size));
            fm = g.getFontMetrics();
            int total = 0;
            for (String step : STEPS) total += wrap(step, fm, maxW).size() * fm.getHeight() + (int) (fm.getHeight() * 0.4);
            if (total <= availH || size <= 9) break;
            size -= 1;
        }
        int y = top;
        for (String step : STEPS) {
            List<String> lines = wrap(step, fm, maxW);
            for (int i = 0; i < lines.size(); i++) {
                g.setColor(new Color(0x3B2B1D));
                g.drawString((i > 0 ? "    " : "") + lines.get(i), x, y);
                y += fm.getHeight();
            }
            y += fm.getHeight() * 0.4;
        }
    }

    private static List<String> wrap(String text, FontMetrics fm, int maxW) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = cur.length() == 0 ? word : cur + " " + word;
            if (fm.stringWidth(test) > maxW && cur.length() > 0) { lines.add(cur.toString()); cur = new StringBuilder(word); }
            else cur = new StringBuilder(test);
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }
}
