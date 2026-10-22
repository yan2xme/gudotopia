import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads the main-menu pictures from assets/menu/ (ground.png, title.png, banana_cat.png, white_cat.png, farmer.png).
 * Transparent padding is cropped away so the pictures can be positioned exactly.
 * If a file is missing, get() returns null and the menu falls back to the built-in drawn art.
 */
public final class MenuArt {
    private static final Map<String, BufferedImage> CACHE = new HashMap<>();
    private static final String[] DIRS = { "assets/menu", "../assets/menu", "Gudotopia/assets/menu" };

    private MenuArt() {}

    public static BufferedImage get(String name, boolean cropTransparent) {
        String key = name + cropTransparent;
        if (CACHE.containsKey(key)) return CACHE.get(key);
        BufferedImage img = null;
        for (String dir : DIRS) {
            File f = new File(dir, name + ".png");
            if (!f.isFile()) continue;
            try {
                img = ImageIO.read(f);
                if (img != null && cropTransparent) img = crop(img);
                if (img != null) break;
            } catch (Exception e) {
                System.err.println("Could not read " + f + ": " + e);
            }
        }
        CACHE.put(key, img);
        return img;
    }

    /** Crops to the bounding box of the non-transparent pixels. */
    private static BufferedImage crop(BufferedImage src) {
        int minX = src.getWidth(), minY = src.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < src.getHeight(); y++)
            for (int x = 0; x < src.getWidth(); x++)
                if ((src.getRGB(x, y) >>> 24) > 16) {
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                }
        if (maxX < 0) return src;
        return src.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }
}
