import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Reusable drawing helpers: backgrounds, buttons, panels, text, plants, currency icons. */
public final class GameRenderer {
    private GameRenderer() {}

    public static final Color BLACK = new Color(0x141414);
    public static final Color GREEN = new Color(0x66BB6A);
    public static final Color GREEN_HOVER = new Color(0x7ED481);
    public static final Color GREEN_LIGHT = new Color(0x93DC96);
    public static final Color GREEN_DARK = new Color(0x4A9A4E);

    // ------------------------------------------------------------------ fonts
    private static final Map<Integer, Font> FONTS = new HashMap<>();
    private static Font customBase;
    private static boolean customTried;

    /** Looks for any .ttf in assets/fonts (drop a pixel font there!). Falls back to bold Monospaced. */
    private static Font base() {
        if (!customTried) {
            customTried = true;
            for (String dir : new String[]{"assets/fonts", "../assets/fonts"}) {
                File[] files = new File(dir).listFiles((d, n) -> n.toLowerCase().endsWith(".ttf") || n.toLowerCase().endsWith(".otf"));
                if (files != null && files.length > 0) {
                    try { customBase = Font.createFont(Font.TRUETYPE_FONT, files[0]); break; }
                    catch (Exception e) { System.err.println("Could not load font " + files[0] + ": " + e); }
                }
            }
        }
        return customBase;
    }

    public static Font font(float size) {
        int key = Math.round(size * 2);
        return FONTS.computeIfAbsent(key, k -> {
            Font b = base();
            return b != null ? b.deriveFont(Font.PLAIN, size) : new Font(Font.MONOSPACED, Font.BOLD, 1).deriveFont(size);
        });
    }

    public static Font titleFont(float size) { return new Font(Font.SANS_SERIF, Font.BOLD, 1).deriveFont(size); }

    public static void smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    }

    // ------------------------------------------------------------------ text
    /** Draws centred (align=0), left (-1) or right (1) aligned text with a chunky outline. */
    public static void text(Graphics2D g, String s, int x, int y, float size, Color fill, Color outline, int align) {
        g.setFont(font(size));
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(s);
        int px = align == 0 ? x - w / 2 : (align > 0 ? x - w : x);
        int o = Math.max(1, Math.round(size / 12f));
        if (outline != null) {
            g.setColor(outline);
            for (int dx = -o; dx <= o; dx++) for (int dy = -o; dy <= o; dy++) if (dx != 0 || dy != 0) g.drawString(s, px + dx, y + dy);
        }
        g.setColor(fill);
        g.drawString(s, px, y);
    }

    /** Shrinks the font until the text fits within maxW. */
    public static float fitSize(Graphics2D g, String s, float size, int maxW) {
        while (size > 8) {
            if (g.getFontMetrics(font(size)).stringWidth(s) <= maxW) break;
            size -= 1;
        }
        return size;
    }

    /** The big rainbow GUDOTOPIA title: each letter has a coloured fill, a white outline and a dark rim. */
    public static void title(Graphics2D g, String text, int cx, int baseline, float size, double t) {
        Color[] colors = { new Color(0xF0546A), new Color(0xF47A3A), new Color(0xF6AE2D), new Color(0xF2D93E),
                           new Color(0xA5D63F), new Color(0x4DBE55), new Color(0xB063E0), new Color(0x8E6CE8), new Color(0x4AA3EA) };
        Font f = titleFont(size);
        FontRenderContext frc = g.getFontRenderContext();
        FontMetrics fm = g.getFontMetrics(f);
        int total = fm.stringWidth(text);
        double x = cx - total / 2.0;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            double yy = baseline + Math.sin(t * 2.2 + i * 0.7) * size * 0.03;
            GlyphVector gv = f.createGlyphVector(frc, ch);
            Shape shape = gv.getOutline((float) x, (float) yy);
            g.setStroke(new BasicStroke(size * 0.22f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(0x3B2B3B));
            g.draw(shape);
            g.setStroke(new BasicStroke(size * 0.15f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(Color.WHITE);
            g.draw(shape);
            Color c = colors[i % colors.length];
            g.setPaint(new GradientPaint(0, (float) (yy - size * 0.7), c.brighter(), 0, (float) yy, c.darker()));
            g.fill(shape);
            x += fm.stringWidth(ch);
        }
        g.setStroke(new BasicStroke(1));
    }

    // ------------------------------------------------------------------ backgrounds
    private static final int[][] CLOUD_SHAPE = { {5, 6}, {2, 12}, {0, 17}, {1, 15} };  // {x offset, width} per row
    // fractional x, fractional y, size (px per cloud-pixel as fraction of height), speed
    private static final double[][] CLOUDS = {
        {0.05, 0.10, 0.011, 9}, {0.38, 0.05, 0.015, 6}, {0.70, 0.17, 0.012, 11},
        {0.18, 0.30, 0.009, 5}, {0.55, 0.36, 0.010, 8}, {0.90, 0.08, 0.008, 7}
    };

    /** Banded pixel-style sky gradient. */
    public static void sky(Graphics2D g, int w, int h) {
        Color top = new Color(0x4F86F0), bottom = new Color(0xA6C8FF);
        int bands = 16;
        for (int i = 0; i < bands; i++) {
            float f = i / (float) (bands - 1);
            g.setColor(new Color((int) (top.getRed() + (bottom.getRed() - top.getRed()) * f),
                                 (int) (top.getGreen() + (bottom.getGreen() - top.getGreen()) * f),
                                 (int) (top.getBlue() + (bottom.getBlue() - top.getBlue()) * f)));
            int y0 = i * h / bands, y1 = (i + 1) * h / bands;
            g.fillRect(0, y0, w, y1 - y0 + 1);
        }
    }

    /** White pixel clouds drifting slowly to the right. */
    public static void clouds(Graphics2D g, int w, int h, double t) {
        for (double[] c : CLOUDS) {
            int u = Math.max(3, (int) (h * c[2]));
            int cw = 18 * u;
            double x = ((c[0] * w + t * c[3] * u * 0.5) % (w + cw)) - cw;
            int y = (int) (c[1] * h);
            for (int row = 0; row < CLOUD_SHAPE.length; row++) {
                g.setColor(row == CLOUD_SHAPE.length - 1 ? new Color(0xDCE8FF) : Color.WHITE);
                g.fillRect((int) x + CLOUD_SHAPE[row][0] * u, y + row * u, CLOUD_SHAPE[row][1] * u, u);
            }
        }
    }

    /** Deterministic pseudo-random 0..n-1 for a grid cell (so textures don't flicker). */
    private static int hash(int i, int j, int n) {
        int v = (i * 73856093) ^ (j * 19349663);
        v ^= v >>> 13;
        return Math.abs(v) % n;
    }

    /** Grass strip with a dirt layer, as on the main menu. */
    public static void groundStrip(Graphics2D g, int w, int h) {
        int stripH = (int) (h * 0.17);
        int u = Math.max(4, h / 70);
        int y0 = h - stripH;
        int grassH = Math.max(u * 4, stripH * 2 / 5);
        for (int x = 0, i = 0; x < w; x += u, i++) {
            int blade = hash(i, 1, 4) * u;
            g.setColor(new Color(0x5FB04C));
            g.fillRect(x, y0 + u - blade / 2, u, grassH);
            g.setColor(hash(i, 2, 3) == 0 ? new Color(0x8BCF63) : new Color(0x4B9A3F));
            g.fillRect(x, y0 + u - blade / 2, u, u);
        }
        g.setColor(new Color(0x7B5A48));
        g.fillRect(0, y0 + grassH, w, stripH - grassH + 2);
        for (int y = y0 + grassH, j = 0; y < h; y += u, j++)
            for (int x = 0, i = 0; x < w; x += u, i++) {
                int k = hash(i, j, 7);
                if (k == 0) { g.setColor(new Color(0x6A4A3A)); g.fillRect(x, y, u * 2, u); }
                else if (k == 1) { g.setColor(new Color(0x8C6A55)); g.fillRect(x, y, u, u); }
            }
        g.setColor(new Color(0x4A3328));
        g.fillRect(0, y0 + grassH, w, Math.max(2, u / 2));
    }

    /** Layered green hills used behind the garden. */
    public static void hills(Graphics2D g, int w, int h) {
        int u = Math.max(4, h / 60);
        int base = (int) (h * 0.80);
        for (int x = 0, i = 0; x < w; x += u, i++) {
            int top = base - (int) (Math.sin(x * 0.004 + 1) * h * 0.035) - hash(i / 6, 3, 2) * u;
            g.setColor(new Color(0x8FCB5C));
            g.fillRect(x, top, u, h - top);
        }
        for (int x = 0, i = 0; x < w; x += u, i++) {
            int top = base + (int) (h * 0.05) + (int) (Math.sin(x * 0.006) * h * 0.02);
            g.setColor(new Color(0x6FB544));
            g.fillRect(x, top, u, h - top);
            g.setColor(new Color(0x7FC24E));
            if (hash(i, 9, 5) == 0) g.fillRect(x, top + u * 2 + hash(i, 4, 6) * u, u * 2, u);
        }
        g.setColor(new Color(0x58A03A));
        g.fillRect(0, (int) (h * 0.93), w, h);
    }

    // ------------------------------------------------------------------ boxes / buttons
    /** Chunky pixel panel: black border, coloured fill, light top edge, dark bottom edge. */
    public static void box(Graphics2D g, int x, int y, int w, int h, Color fill, int border) {
        g.setColor(new Color(0, 0, 0, 70));
        g.fillRect(x + border, y + border + 1, w, h);
        g.setColor(BLACK);
        g.fillRect(x, y, w, h);
        g.setColor(fill);
        g.fillRect(x + border, y + border, w - 2 * border, h - 2 * border);
        g.setColor(fill.brighter());
        g.fillRect(x + border, y + border, w - 2 * border, Math.max(2, border / 2));
        g.setColor(fill.darker());
        g.fillRect(x + border, y + h - border - Math.max(2, border / 2), w - 2 * border, Math.max(2, border / 2));
    }

    public static void button(Graphics2D g, int x, int y, int w, int h, String label,
                              boolean hover, boolean pressed, boolean enabled, boolean highlighted) {
        int b = Math.max(3, h / 12);
        int off = pressed ? Math.max(1, b / 2) : 0;
        Color fill = !enabled ? new Color(0x8D9A8D) : (hover ? GREEN_HOVER : GREEN);
        box(g, x, y + off, w, h, fill, b);
        if (highlighted) {   // e.g. the fertilizer tool is active: yellow inner frame
            g.setColor(new Color(0xFFE23B));
            g.fillRect(x + b, y + off + b, w - 2 * b, 2);
            g.fillRect(x + b, y + off + h - b - 2, w - 2 * b, 2);
            g.fillRect(x + b, y + off + b, 2, h - 2 * b);
            g.fillRect(x + w - b - 2, y + off + b, 2, h - 2 * b);
        }
        float size = fitSize(g, label, h * 0.42f, w - 4 * b);
        g.setFont(font(size));
        FontMetrics fm = g.getFontMetrics();
        int ty = y + off + (h + fm.getAscent() - fm.getDescent()) / 2;
        text(g, label, x + w / 2, ty, size, enabled ? Color.WHITE : new Color(0xE0E0E0), new Color(0x1F4D22, false), 0);
    }

    // ------------------------------------------------------------------ icons / HUD
    public static void coin(Graphics2D g, int x, int y, int size) { PixelArt.draw(g, PixelArt.coin(), x, y, size, size); }
    public static void seedBag(Graphics2D g, int x, int y, int size) { PixelArt.draw(g, PixelArt.seedBag(), x, y, size, size); }

    // ------------------------------------------------------------------ plants
    /**
     * Draws the plant in a plot. (cx, baseY) is the bottom-centre of the sprite, width its width.
     * t = time in seconds (used for the gentle sway and the bounce of ripe flowers).
     */
    public static void plant(Graphics2D g, GardenPlot p, double cx, double baseY, double width, double t) {
        int stage = p.stage();
        if (stage < 0) return;
        double s = width / 32.0;
        double x = cx - width / 2, y = baseY - 40 * s;
        Flower f = p.getFlower();
        if (stage >= 1) x += Math.sin(t * 2 + f.id.hashCode()) * s * (stage >= 2 ? 0.7 : 0.3);
        PixelArt.draw(g, PixelArt.stem(stage), x, y, 32 * s, 40 * s);
        if (stage == 2) PixelArt.draw(g, PixelArt.bud(f), x, y, 32 * s, 40 * s);
        if (stage == 3) {
            double bob = Math.abs(Math.sin(t * 3 + f.id.hashCode())) * s * 2;
            PixelArt.draw(g, PixelArt.head(f), x + 4 * s, y - bob, 24 * s, 24 * s);
        }
    }

    /** Little 4-point sparkle used on ripe flowers. */
    public static void sparkle(Graphics2D g, double x, double y, double r, Color c) {
        g.setColor(c);
        int ir = (int) Math.max(2, r);
        g.fillRect((int) x - ir, (int) y - ir / 3, ir * 2, Math.max(1, ir * 2 / 3));
        g.fillRect((int) x - ir / 3, (int) y - ir, Math.max(1, ir * 2 / 3), ir * 2);
    }
}
