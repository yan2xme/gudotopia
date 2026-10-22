import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Creates all the little pixel-art pictures in code (no image files needed).
 * Images are drawn on tiny canvases with anti-aliasing OFF and scaled up with nearest-neighbour,
 * which gives the chunky pixel look.
 *
 * Classmate faces: if assets/faces/<flowerId>.png (or .jpg) exists it is used as the face,
 * otherwise a placeholder face is generated. A missing/broken image never crashes the game.
 */
public final class PixelArt {
    private static final Color DARK = new Color(0x2B1D1D);
    private static final Color G = new Color(0x4CAF50), GD = new Color(0x2E7D32), GL = new Color(0x81C784);
    private static final Map<String, BufferedImage> CACHE = new HashMap<>();
    private static final Map<String, BufferedImage> FACES = new HashMap<>();
    private static final String[] FACE_DIRS = { "assets/faces", "../assets/faces", "Gudotopia/assets/faces" };
    private static final String[] EXTS = { ".png", ".jpg", ".jpeg" };

    private PixelArt() {}

    /** Small image cache. (Not computeIfAbsent: building one image may need another cached image.) */
    private static BufferedImage cached(String key, java.util.function.Function<String, BufferedImage> builder) {
        BufferedImage img = CACHE.get(key);
        if (img == null) { img = builder.apply(key); CACHE.put(key, img); }
        return img;
    }

    private static BufferedImage canvas(int w, int h) { return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB); }

    private static Graphics2D pixelG(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        return g;
    }

    private static void rect(Graphics2D g, Color c, int x, int y, int w, int h) { g.setColor(c); g.fillRect(x, y, w, h); }
    private static void oval(Graphics2D g, Color c, double x, double y, double w, double h) { g.setColor(c); g.fill(new Ellipse2D.Double(x, y, w, h)); }

    // ------------------------------------------------------------------ classmate faces
    /** Returns the real face image from assets/faces, or null when there is none. */
    public static BufferedImage loadFace(Flower f) {
        if (FACES.containsKey(f.id)) return FACES.get(f.id);
        BufferedImage found = null;
        outer:
        for (String dir : FACE_DIRS)
            for (String ext : EXTS) {
                File file = new File(dir, f.id + ext);
                if (file.isFile()) {
                    try {
                        found = ImageIO.read(file);
                        if (found != null) break outer;
                    } catch (Exception e) {
                        System.err.println("Could not read face image " + file + ": " + e);
                    }
                }
            }
        FACES.put(f.id, found);
        return found;
    }

    /** Flower head (petals + face), 24x24 pixels, or 96x96 when a real face image is used. */
    public static BufferedImage head(Flower f) {
        return cached("head_" + f.id, k -> renderHead(f));
    }

    private static BufferedImage renderHead(Flower f) {
        BufferedImage face = loadFace(f);
        int scale = face != null ? 4 : 1;
        BufferedImage img = canvas(24 * scale, 24 * scale);
        Graphics2D g = img.createGraphics();
        g.scale(scale, scale);
        Object aa = face != null ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        // petals: dark outline first, then colour
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4, cx = 12 + Math.cos(a) * 7.5, cy = 12 + Math.sin(a) * 7.5;
            oval(g, DARK, cx - 4.5, cy - 4.5, 9, 9);
        }
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4, cx = 12 + Math.cos(a) * 7.5, cy = 12 + Math.sin(a) * 7.5;
            oval(g, i % 2 == 0 ? f.petalColor : f.petalColor.brighter(), cx - 3.5, cy - 3.5, 7, 7);
        }
        oval(g, DARK, 5, 5, 14, 14);

        Shape faceShape = new Ellipse2D.Double(6, 6, 12, 12);
        if (face != null) {
            g.setClip(faceShape);
            g.drawImage(face, 6, 6, 12, 12, null);
            g.setClip(null);
        } else {
            oval(g, f.skin, 6, 6, 12, 12);
            g.setClip(faceShape);
            rect(g, f.hair, 5, 5, 14, 4);                         // hair on top
            switch (f.hairStyle) {
                case 0 -> { rect(g, f.hair, 6, 9, 3, 1); rect(g, f.hair, 15, 9, 3, 1); }
                case 1 -> { rect(g, f.hair, 5, 8, 7, 2); rect(g, f.hair, 5, 9, 2, 4); }
                case 2 -> { rect(g, f.hair, 5, 5, 3, 10); rect(g, f.hair, 16, 5, 3, 10); }
                default -> { rect(g, f.hair, 5, 9, 2, 2); rect(g, f.hair, 17, 9, 2, 2); }
            }
            g.setClip(null);
            if (f.hairStyle == 3) { rect(g, f.hair, 7, 4, 2, 2); rect(g, f.hair, 11, 3, 2, 3); rect(g, f.hair, 15, 4, 2, 2); }
            rect(g, DARK, 9, 12, 1, 2);                            // eyes
            rect(g, DARK, 14, 12, 1, 2);
            rect(g, new Color(0xFF9A9A), 8, 15, 2, 1);             // cheeks
            rect(g, new Color(0xFF9A9A), 14, 15, 2, 1);
            rect(g, new Color(0xA83232), 10, 16, 1, 1);            // smile
            rect(g, new Color(0xA83232), 11, 17, 2, 1);
            rect(g, new Color(0xA83232), 13, 16, 1, 1);
        }
        g.dispose();
        return img;
    }

    // ------------------------------------------------------------------ plant stages (32x40, bottom-centre = base)
    /** Soil mound + stem + leaves for stage 0..3. */
    public static BufferedImage stem(int stage) {
        return cached("stem_" + stage, k -> {
            BufferedImage img = canvas(32, 40);
            Graphics2D g = pixelG(img);
            oval(g, DARK, 8, 34, 16, 6);
            oval(g, new Color(0x6D4C41), 9, 35, 14, 4);
            if (stage == 0) {
                rect(g, DARK, 14, 32, 4, 4);
                rect(g, new Color(0xE0C080), 15, 33, 2, 2);
            } else if (stage == 1) {
                rect(g, G, 15, 29, 2, 7);
                rect(g, GD, 16, 29, 1, 7);
                rect(g, G, 10, 29, 5, 2); rect(g, GL, 9, 28, 3, 1);
                rect(g, G, 17, 27, 5, 2); rect(g, GL, 20, 26, 3, 1);
            } else {
                rect(g, G, 15, 21, 2, 15);
                rect(g, GD, 16, 21, 1, 15);
                rect(g, G, 6, 31, 9, 3); rect(g, GL, 4, 30, 4, 2); rect(g, GD, 8, 34, 5, 1);
                rect(g, G, 17, 27, 9, 3); rect(g, GL, 24, 26, 4, 2); rect(g, GD, 18, 30, 5, 1);
            }
            g.dispose();
            return img;
        });
    }

    /** A closed flower bud in the flower's colour (stage 2), same 32x40 frame as stem(). */
    public static BufferedImage bud(Flower f) {
        return cached("bud_" + f.id, k -> {
            BufferedImage img = canvas(32, 40);
            Graphics2D g = pixelG(img);
            rect(g, DARK, 12, 12, 8, 10); rect(g, DARK, 11, 13, 10, 8);
            rect(g, f.petalColor, 13, 13, 6, 8); rect(g, f.petalColor, 12, 14, 8, 6);
            rect(g, f.petalColor.brighter(), 13, 13, 2, 3);
            rect(g, GD, 13, 20, 6, 2);
            g.dispose();
            return img;
        });
    }

    // ------------------------------------------------------------------ icons
    public static BufferedImage coin() {
        return cached("coin", k -> {
            BufferedImage img = canvas(16, 16);
            Graphics2D g = pixelG(img);
            oval(g, new Color(0x6B4A00), 0, 0, 16, 16);
            oval(g, new Color(0xFFC107), 1, 1, 14, 14);
            oval(g, new Color(0xE69F00), 3, 3, 10, 10);
            oval(g, new Color(0xFFD54F), 4, 3, 8, 9);
            rect(g, new Color(0xFFF3B0), 4, 4, 2, 3);
            rect(g, new Color(0xB77900), 7, 5, 2, 6);
            g.dispose();
            return img;
        });
    }

    public static BufferedImage seedBag() {
        return cached("bag", k -> {
            BufferedImage img = canvas(16, 16);
            Graphics2D g = pixelG(img);
            oval(g, DARK, 1, 4, 14, 12);
            oval(g, new Color(0xC99A4B), 2, 5, 12, 10);
            rect(g, DARK, 5, 2, 6, 4);
            rect(g, new Color(0xE8C77A), 6, 3, 4, 2);
            rect(g, new Color(0xF2D98F), 4, 7, 3, 4);
            rect(g, G, 7, 8, 3, 3);
            g.dispose();
            return img;
        });
    }

    // ------------------------------------------------------------------ main-menu characters (original art)
    public static BufferedImage farmer() {
        return cached("farmer", k -> {
            BufferedImage img = canvas(26, 32);
            Graphics2D g = pixelG(img);
            Color skin = new Color(0xF1C27D), hair = new Color(0x2B1B12);
            // body: plaid shirt + overalls
            rect(g, DARK, 3, 21, 20, 11);
            rect(g, new Color(0xC0503E), 4, 22, 18, 10);
            for (int i = 0; i < 18; i += 4) rect(g, new Color(0x8E2F22), 4 + i, 22, 1, 10);
            for (int j = 0; j < 10; j += 4) rect(g, new Color(0x8E2F22), 4, 22 + j, 18, 1);
            rect(g, DARK, 7, 25, 12, 7);
            rect(g, new Color(0x3A6EA5), 8, 26, 10, 6);
            rect(g, new Color(0x3A6EA5), 7, 22, 2, 4); rect(g, new Color(0x3A6EA5), 17, 22, 2, 4);
            rect(g, new Color(0xF5D76E), 8, 26, 2, 2); rect(g, new Color(0xF5D76E), 16, 26, 2, 2);
            // head
            oval(g, DARK, 5, 6, 16, 16);
            oval(g, skin, 6, 7, 14, 14);
            rect(g, hair, 6, 9, 2, 5); rect(g, hair, 18, 9, 2, 5);
            rect(g, DARK, 10, 13, 2, 3); rect(g, DARK, 15, 13, 2, 3);
            rect(g, Color.WHITE, 10, 13, 1, 1); rect(g, Color.WHITE, 15, 13, 1, 1);
            rect(g, new Color(0xFF9A9A), 8, 17, 2, 1); rect(g, new Color(0xFF9A9A), 17, 17, 2, 1);
            rect(g, DARK, 11, 18, 5, 1); rect(g, Color.WHITE, 12, 18, 3, 1);
            // straw hat
            rect(g, DARK, 1, 6, 24, 4); rect(g, DARK, 6, 1, 14, 6);
            rect(g, new Color(0xE9C46A), 2, 7, 22, 2); rect(g, new Color(0xE9C46A), 7, 2, 12, 5);
            rect(g, new Color(0xC0392B), 7, 5, 12, 2);
            // red bandana
            rect(g, DARK, 7, 21, 12, 3);
            rect(g, new Color(0xD32F2F), 8, 21, 10, 2);
            rect(g, DARK, 10, 23, 6, 2); rect(g, new Color(0xD32F2F), 11, 23, 4, 1);
            g.dispose();
            return img;
        });
    }

    public static BufferedImage cat() {
        return cached("cat", k -> {
            BufferedImage img = canvas(24, 18);
            Graphics2D g = pixelG(img);
            Color white = Color.WHITE, shade = new Color(0xD9E4F5), pink = new Color(0xF7A8B8);
            // ears
            g.setColor(DARK);
            g.fillPolygon(new int[]{2, 9, 2}, new int[]{0, 5, 8}, 3);
            g.fillPolygon(new int[]{22, 15, 22}, new int[]{0, 5, 8}, 3);
            g.setColor(white);
            g.fillPolygon(new int[]{3, 8, 3}, new int[]{2, 5, 7}, 3);
            g.fillPolygon(new int[]{21, 16, 21}, new int[]{2, 5, 7}, 3);
            g.setColor(pink);
            g.fillPolygon(new int[]{4, 6, 4}, new int[]{3, 5, 6}, 3);
            g.fillPolygon(new int[]{20, 18, 20}, new int[]{3, 5, 6}, 3);
            // head
            oval(g, DARK, 0, 3, 24, 15);
            oval(g, white, 1, 4, 22, 13);
            rect(g, shade, 3, 14, 18, 2);
            rect(g, DARK, 6, 8, 2, 3); rect(g, DARK, 16, 8, 2, 3);
            rect(g, pink, 3, 11, 2, 1); rect(g, pink, 19, 11, 2, 1);
            rect(g, pink, 11, 11, 2, 1);
            rect(g, DARK, 10, 12, 1, 1); rect(g, DARK, 13, 12, 1, 1); rect(g, DARK, 11, 13, 2, 1);
            g.dispose();
            return img;
        });
    }

    public static BufferedImage sunflower() {
        return cached("sunflower", k -> {
            BufferedImage img = canvas(24, 32);
            Graphics2D g = pixelG(img);
            // stem + leaves
            rect(g, DARK, 11, 18, 4, 14); rect(g, G, 12, 18, 2, 14);
            rect(g, DARK, 4, 24, 8, 4); rect(g, G, 5, 25, 6, 2);
            rect(g, DARK, 14, 21, 8, 4); rect(g, G, 15, 22, 6, 2);
            // petals
            for (int i = 0; i < 10; i++) {
                double a = i * Math.PI / 5, cx = 12 + Math.cos(a) * 8, cy = 11 + Math.sin(a) * 8;
                oval(g, DARK, cx - 4, cy - 4, 8, 8);
            }
            for (int i = 0; i < 10; i++) {
                double a = i * Math.PI / 5, cx = 12 + Math.cos(a) * 8, cy = 11 + Math.sin(a) * 8;
                oval(g, new Color(0xFFD23F), cx - 3, cy - 3, 6, 6);
            }
            oval(g, DARK, 5, 4, 14, 14);
            oval(g, new Color(0x8D5A2B), 6, 5, 12, 12);
            rect(g, Color.WHITE, 9, 9, 2, 3); rect(g, Color.WHITE, 14, 9, 2, 3);
            rect(g, DARK, 9, 10, 2, 2); rect(g, DARK, 14, 10, 2, 2);
            rect(g, DARK, 10, 14, 5, 1); rect(g, DARK, 9, 13, 1, 1); rect(g, DARK, 15, 13, 1, 1);
            g.dispose();
            return img;
        });
    }

    /** Draws an image scaled to w x h: crisp (nearest-neighbour) for pixel art, smooth for big photos. */
    public static void draw(Graphics2D g, Image img, double x, double y, double w, double h) {
        Object old = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        boolean photo = img.getWidth(null) >= 64;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                photo ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(img, (int) Math.round(x), (int) Math.round(y), (int) Math.round(w), (int) Math.round(h), null);
        if (old != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, old);
    }

    /** Same image but as a dark silhouette (for locked flowers). */
    public static BufferedImage silhouette(BufferedImage src, Color tint) {
        BufferedImage out = canvas(src.getWidth(), src.getHeight());
        for (int y = 0; y < src.getHeight(); y++)
            for (int x = 0; x < src.getWidth(); x++) {
                int a = src.getRGB(x, y) >>> 24;
                if (a > 0) out.setRGB(x, y, (a << 24) | (tint.getRGB() & 0xFFFFFF));
            }
        return out;
    }

    public static BufferedImage lockedHead(Flower f) {
        return cached("locked_" + f.id, k -> silhouette(head(f), new Color(0x3A3F55)));
    }
}
