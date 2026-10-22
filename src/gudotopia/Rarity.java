package gudotopia;

import java.awt.Color;

/**
 * The six rarity tiers.
 *
 * HOW TO CONFIGURE: change dropChance (percent), reward (money when harvested)
 * or growSeconds (time to fully grow) below. dropChance values should add up
 * to 100 - SeedShop.validateDropChances() prints a warning at startup if not.
 */
public enum Rarity {
    //          label        color                   drop%  reward  growSeconds
    COMMON(     "Common",    new Color(0x7A7A7A),    50.0,    5,     10),
    UNCOMMON(   "Uncommon",  new Color(0x2E9E3E),    25.0,   10,     14),
    RARE(       "Rare",      new Color(0x2D6FD6),    15.0,   20,     18),
    EPIC(       "Epic",      new Color(0x9338C9),     6.0,   40,     22),
    LEGENDARY(  "Legendary", new Color(0xE8860C),     3.5,   75,     26),
    MYTHICAL(   "Mythical",  new Color(0xD6246E),     0.5,  150,     30);

    public final String label;
    public final Color color;
    /** Chance (in percent) that a purchased seed is of this rarity. */
    public final double dropChance;
    /** Money earned when a flower of this rarity is harvested. */
    public final int reward;
    /** Seconds needed for a flower of this rarity to fully grow. */
    public final int growSeconds;

    Rarity(String label, Color color, double dropChance, int reward, int growSeconds) {
        this.label = label;
        this.color = color;
        this.dropChance = dropChance;
        this.reward = reward;
        this.growSeconds = growSeconds;
    }
}
