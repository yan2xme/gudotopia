import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Title screen. Uses the pictures in assets/menu/ (ground, title logo, banana cat, farmer, white cat);
 * any missing picture is replaced by built-in drawn art. Layout follows the reference mock-up.
 */
public class MainMenuPanel extends ScreenPanel {
    private final PixelButton start, howTo;

    public MainMenuPanel(GameFrame frame) {
        start = new PixelButton("START", frame::showGarden);
        howTo = new PixelButton("HOW TO PLAY?", frame::showHowTo);
        add(start);
        add(howTo);
    }

    @Override public void doLayout() {
        int w = getWidth(), h = getHeight();
        int bw = (int) (w * 0.32), bh = (int) (h * 0.15), y = (int) (h * 0.62);
        start.setBounds((int) (w * 0.163), y, bw, bh);
        howTo.setBounds((int) (w * 0.542), y, bw, bh);
    }

    /** Draws an image at the given size with crisp pixels (pixelArt=true) or smooth scaling (logo). */
    private static void drawImage(Graphics2D g, BufferedImage img, double x, double y, double w, double h, boolean pixelArt) {
        Object old = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                pixelArt ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(img, (int) Math.round(x), (int) Math.round(y), (int) Math.round(w), (int) Math.round(h), null);
        if (old != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, old);
    }

    @Override protected void paintScreen(Graphics2D g, int w, int h) {
        // ---- ground (grass + soil), full width along the bottom
        BufferedImage ground = MenuArt.get("ground", false);
        if (ground != null) {
            double gh = Math.min(h * 0.26, w * ground.getHeight() / (double) ground.getWidth());
            drawImage(g, ground, 0, h - gh, w, gh, true);
        } else {
            GameRenderer.groundStrip(g, w, h);
        }

        // ---- title position (centre of screen, like the reference)
        BufferedImage title = MenuArt.get("title", true);
        double tw, th;
        if (title != null) {
            tw = w * 0.76;
            th = tw * title.getHeight() / title.getWidth();
            if (th > h * 0.27) { th = h * 0.27; tw = th * title.getWidth() / title.getHeight(); }
        } else {
            tw = w * 0.76; th = h * 0.2;
        }
        double titleTop = h * 0.376, titleBottomY = titleTop + th;
        double standY = titleTop + th * 0.09;            // characters stand just behind the top edge of the logo

        // ---- characters (drawn BEFORE the title so the logo overlaps their feet)
        double farmerTop = standY;
        boolean haveFarmer = drawCharacter(g, "farmer", w * 0.50, standY, h * 0.27, 1.3, true);
        if (haveFarmer) farmerTop = standY - h * 0.27 - Math.abs(Math.sin(time * 2 + 1.3)) * h * 0.27 * 0.03;
        if (!drawCharacter(g, "banana_cat", w * 0.335, standY + th * 0.22, h * 0.30, 0.0, true))
            drawFallback(g, PixelArt.sunflower(), w * 0.335, standY, h * 0.27, 0.0);
        if (!drawCharacter(g, "white_cat", w * 0.70, standY, h * 0.155, 2.4, true))
            drawFallback(g, PixelArt.cat(), w * 0.70, standY, h * 0.155, 2.4);
        if (!haveFarmer) { drawFallback(g, PixelArt.farmer(), w * 0.50, standY, h * 0.31, 1.3); farmerTop = standY - h * 0.31; }

        // three little stars over the farmer's head
        for (int i = -1; i <= 1; i++) {
            double sy = farmerTop - h * 0.005 + Math.abs(i) * h * 0.02 + Math.sin(time * 3 + i) * h * 0.006;
            double sx = w * 0.5 + i * w * 0.04;
            GameRenderer.sparkle(g, sx, sy, h * 0.026, new Color(0x7A5200));
            GameRenderer.sparkle(g, sx, sy, h * 0.019, new Color(0xFFD23F));
        }

        // ---- title logo
        if (title != null) {
            drawImage(g, title, (w - tw) / 2, titleTop, tw, th, false);
        } else {
            float size = (float) Math.min(w * 0.108, h * 0.24);
            GameRenderer.title(g, "GUDOTOPIA", w / 2, (int) (h * 0.57), size, time);
        }
    }

    /** Draws assets/menu/<name>.png standing at (cx, bottomY) with the given height. Returns false if it is missing. */
    private boolean drawCharacter(Graphics2D g, String name, double cx, double bottomY, double height, double phase, boolean pixel) {
        BufferedImage img = MenuArt.get(name, true);
        if (img == null) return false;
        double width = height * img.getWidth() / img.getHeight();
        double bob = Math.abs(Math.sin(time * 2 + phase)) * height * 0.03;
        drawImage(g, img, cx - width / 2, bottomY - height - bob, width, height, pixel);
        return true;
    }

    private void drawFallback(Graphics2D g, Image img, double cx, double bottomY, double height, double phase) {
        double scale = height / img.getHeight(null);
        double bob = Math.abs(Math.sin(time * 2 + phase)) * height * 0.04;
        PixelArt.draw(g, img, cx - img.getWidth(null) * scale / 2, bottomY - height - bob, img.getWidth(null) * scale, height);
    }
}
