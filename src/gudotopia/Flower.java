package gudotopia;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Definition of one collectible flower (= one classmate).
 *
 * TO ADD / EDIT CLASSMATES: change the add(...) calls in the static block.
 * Each flower has an id like "classmate07". To give it a real face, drop an
 * image called  assets/faces/classmate07.png  (png/jpg) into the project folder.
 * If the image is missing a drawn placeholder face is used instead.
 *
 * Unlock status is NOT stored here - it lives in PlayerData (and the save file).
 */
public final class Flower {

    /** Every flower in the game, in collection order. */
    public static final List<Flower> ALL;

    static {
        List<Flower> list = new ArrayList<>();
        // ---- Common (10) ----
        add(list, "Daisy", Rarity.COMMON);
        add(list, "Tulip", Rarity.COMMON);
        add(list, "Marigold", Rarity.COMMON);
        add(list, "Pansy", Rarity.COMMON);
        add(list, "Poppy", Rarity.COMMON);
        add(list, "Violet", Rarity.COMMON);
        add(list, "Cosmos", Rarity.COMMON);
        add(list, "Aster", Rarity.COMMON);
        add(list, "Primrose", Rarity.COMMON);
        add(list, "Zinnia", Rarity.COMMON);
        // ---- Uncommon (8) ----
        add(list, "Rose", Rarity.UNCOMMON);
        add(list, "Lily", Rarity.UNCOMMON);
        add(list, "Lavender", Rarity.UNCOMMON);
        add(list, "Iris", Rarity.UNCOMMON);
        add(list, "Jasmine", Rarity.UNCOMMON);
        add(list, "Daffodil", Rarity.UNCOMMON);
        add(list, "Begonia", Rarity.UNCOMMON);
        add(list, "Azalea", Rarity.UNCOMMON);
        // ---- Rare (6) ----
        add(list, "Sunflower", Rarity.RARE);
        add(list, "Orchid", Rarity.RARE);
        add(list, "Peony", Rarity.RARE);
        add(list, "Camellia", Rarity.RARE);
        add(list, "Dahlia", Rarity.RARE);
        add(list, "Hibiscus", Rarity.RARE);
        // ---- Epic (3) ----
        add(list, "Magnolia", Rarity.EPIC);
        add(list, "Lotus", Rarity.EPIC);
        add(list, "Foxglove", Rarity.EPIC);
        // ---- Legendary (2) ----
        add(list, "Anemone", Rarity.LEGENDARY);
        add(list, "Gardenia", Rarity.LEGENDARY);
        // ---- Mythical (1) ----
        add(list, "Moonflower", Rarity.MYTHICAL);
        ALL = Collections.unmodifiableList(list);
    }

    private static void add(List<Flower> list, String flowerName, Rarity rarity) {
        int index = list.size();
        String number = String.format("%02d", index + 1);
        String id = "classmate" + number;
        String classmate = "Classmate " + number;   // <- replace with a real name later
        String description = "Placeholder face for " + classmate + ". Put assets/faces/" + id
                + ".png in the project folder to show the real face!";
        list.add(new Flower(index, id, flowerName, classmate, rarity, description));
    }

    // ------------------------------------------------------------------

    public final int index;            // position in ALL
    public final String id;            // unique id, also the image file name
    public final String name;          // flower name
    public final String classmateName; // whose face it is
    public final String description;
    public final Rarity rarity;
    public final int reward;           // money when harvested
    public final long growMs;          // growth time in milliseconds
    public final Color petalColor;

    private BufferedImage faceImage;
    private boolean faceLoadTried;

    public Flower(int index, String id, String name, String classmateName, Rarity rarity, String description) {
        this.index = index;
        this.id = id;
        this.name = name;
        this.classmateName = classmateName;
        this.rarity = rarity;
        this.description = description;
        this.reward = rarity.reward;
        this.growMs = rarity.growSeconds * 1000L;
        // Golden-ratio hue steps give every flower a clearly different petal color.
        this.petalColor = Color.getHSBColor((index * 0.618034f) % 1f, 0.55f, 1.0f);
    }

    public static Flower byId(String id) {
        if (id == null) return null;
        for (Flower f : ALL) {
            if (f.id.equals(id)) return f;
        }
        return null;
    }

    public static List<Flower> ofRarity(Rarity rarity) {
        List<Flower> result = new ArrayList<>();
        for (Flower f : ALL) {
            if (f.rarity == rarity) result.add(f);
        }
        return result;
    }

    /**
     * The classmate's face picture, or null when no image file exists
     * (callers then draw a placeholder). Never throws.
     */
    public BufferedImage faceImage() {
        if (!faceLoadTried) {
            faceLoadTried = true;
            faceImage = loadFace();
        }
        return faceImage;
    }

    private BufferedImage loadFace() {
        String[] extensions = {"png", "jpg", "jpeg"};
        for (String ext : extensions) {
            String file = id + "." + ext;
            try {
                File f = new File("assets/faces", file);
                if (f.isFile()) {
                    BufferedImage img = ImageIO.read(f);
                    if (img != null) return img;
                }
                URL url = Flower.class.getResource("/assets/faces/" + file);
                if (url != null) {
                    BufferedImage img = ImageIO.read(url);
                    if (img != null) return img;
                }
            } catch (Exception e) {
                // Broken image file: ignore and fall back to the placeholder.
            }
        }
        return null;
    }
}
