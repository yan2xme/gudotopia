package gudotopia;

/**
 * One square of soil. Holds at most one growing flower.
 * Pure game logic - no drawing in here.
 */
public class GardenPlot {
    private Flower flower;
    private long elapsedMs;

    public boolean isEmpty() {
        return flower == null;
    }

    public Flower flower() {
        return flower;
    }

    public long elapsedMs() {
        return elapsedMs;
    }

    public void plant(Flower f) {
        this.flower = f;
        this.elapsedMs = 0;
    }

    /** Used when loading a save. */
    public void restore(Flower f, long elapsed) {
        this.flower = f;
        this.elapsedMs = f == null ? 0 : Math.max(0, Math.min(elapsed, f.growMs));
    }

    /** Advances growth. Called regularly by the game timer. */
    public void tick(long deltaMs) {
        if (flower != null && !isReady()) {
            elapsedMs = Math.min(elapsedMs + deltaMs, flower.growMs);
        }
    }

    /** Growth progress from 0.0 (just planted) to 1.0 (fully grown). */
    public double progress() {
        if (flower == null) return 0;
        return Math.min(1.0, (double) elapsedMs / flower.growMs);
    }

    public boolean isReady() {
        return flower != null && elapsedMs >= flower.growMs;
    }

    public long remainingMs() {
        return flower == null ? 0 : Math.max(0, flower.growMs - elapsedMs);
    }

    /** 0 = seed, 1 = sprout, 2 = growing plant, 3 = fully grown. -1 = empty. */
    public int stage() {
        if (flower == null) return -1;
        double p = progress();
        if (p >= 1.0) return 3;
        if (p >= 0.5) return 2;
        if (p >= 0.2) return 1;
        return 0;
    }

    /** Fertilizer skips half of the flower's total growth time. */
    public void fertilize() {
        if (flower != null && !isReady()) {
            elapsedMs = Math.min(flower.growMs, elapsedMs + flower.growMs / 2);
        }
    }

    /** Removes and returns the flower, resetting the plot. Returns null if not ready. */
    public Flower harvest() {
        if (!isReady()) return null;
        Flower f = flower;
        flower = null;
        elapsedMs = 0;
        return f;
    }
}
