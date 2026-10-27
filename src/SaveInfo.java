/** A short summary of one save slot, shown in the LOAD screen (one row of the "saves" list). */
public record SaveInfo(long id, String name, long createdAt, long lastPlayed, int money, int gardenSize, int discovered) { }
