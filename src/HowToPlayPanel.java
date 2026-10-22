import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Simple instructions screen. */
public class HowToPlayPanel extends ScreenPanel {
    private static final String[] STEPS = {
        "1.  Open SEEDS and buy seeds for $2. Every seed is a RANDOM flower - rare ones are hard to find!",
        "2.  Pick a seed from your seed list, then click an empty plot to plant it.",
        "3.  Flowers grow by themselves. Hover over a plot to see how long is left.",
        "4.  Click a fully grown flower to harvest it and earn money.",
        "5.  FERTILIZER ($30): click it, then click a growing plant to speed it up.",
        "6.  LARGER PLOT: expand the garden to make room for more flowers.",
        "7.  Every flower is one of our classmates! Harvest a flower once to unlock it forever in the FLOWERS collection. Can you find them all?"
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
        float size = Math.max(13f, h * 0.031f);
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
