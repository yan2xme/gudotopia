import java.awt.Color;

/**
 * Immutable definition of one collectible flower.
 * Its face is the picture assets/flowers/<id>.png when that file exists (the classmates); otherwise a cute
 * face is drawn in code from skin / hair / hairStyle (the common flowers).
 * Whether the player has unlocked it is stored in PlayerData, not here.
 */
public final class Flower {
    public final String id;           // e.g. "daisy", "klent" - also the image file name
    public final String name;
    public final String description;
    public final Rarity rarity;
    public final int growthSeconds;
    public final int reward;
    public final Color petalColor;    // petals and bud
    public final Color skin, hair;    // look of the drawn face
    public final int hairStyle;       // 0..3, see PixelArt.drawnFace

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
