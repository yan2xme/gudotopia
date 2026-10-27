import java.awt.Color;

/**
 * Immutable definition of one collectible flower (= one classmate).
 * Whether the player has unlocked it is stored in PlayerData, not here.
 */
public final class Flower {
    public final String id;           // e.g. "flower_01" - also the image file name (assets/faces/flower_01.png)
    public final String name;
    public final String description;
    public final Rarity rarity;
    public final int growthSeconds;
    public final int reward;
    public final Color petalColor;
    // Look of the generated placeholder face (used when no image file is supplied)
    public final Color skin;
    public final Color hair;
    public final int hairStyle;

    public Flower(String id, String name, String description, Rarity rarity,
                  Color petalColor, Color skin, Color hair, int hairStyle) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.rarity = rarity;
        this.growthSeconds = rarity.growthSeconds;
        this.reward = rarity.reward;
        this.petalColor = petalColor;
        this.skin = skin;
        this.hair = hair;
        this.hairStyle = hairStyle;
    }

    /** Growth time in ms (can be scaled for testing with -Dgudotopia.growthScale=...). */
    public long growthMillis() { return Math.max(1L, Math.round(growthSeconds * 1000L * GameConfig.GROWTH_SCALE)); }
}
