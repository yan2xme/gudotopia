/** One square of soil. Holds the planted flower (if any) and how long it has been growing. */
public class GardenPlot {
    private Flower flower;      // null = empty
    private long growthMs;      // how long it has grown so far

    public boolean isEmpty() { return flower == null; }
    public Flower getFlower() { return flower; }
    public long getGrowthMs() { return growthMs; }

    public void plant(Flower f) { this.flower = f; this.growthMs = 0; }
    public void plant(Flower f, long alreadyGrownMs) { this.flower = f; this.growthMs = Math.max(0, alreadyGrownMs); }
    public void clear() { flower = null; growthMs = 0; }

    public boolean isMature() { return flower != null && growthMs >= flower.growthMillis(); }

    /** 0.0 (just planted) ... 1.0 (fully grown). */
    public double progress() {
        if (flower == null) return 0;
        return Math.min(1.0, growthMs / (double) flower.growthMillis());
    }

    public long remainingMs() { return flower == null ? 0 : Math.max(0, flower.growthMillis() - growthMs); }

    /** Called every game tick with the elapsed milliseconds. */
    public void grow(long deltaMs) {
        if (flower != null && !isMature()) growthMs = Math.min(flower.growthMillis(), growthMs + deltaMs);
    }

    /** Fertilizer skips ahead by a fraction of the total growth time. */
    public void fertilize(double fractionOfTotal) {
        if (flower != null) growthMs = Math.min(flower.growthMillis(), growthMs + (long) (flower.growthMillis() * fractionOfTotal));
    }

    /** 0 = seed, 1 = sprout, 2 = growing, 3 = fully grown. */
    public int stage() {
        if (flower == null) return -1;
        if (isMature()) return 3;
        double p = progress();
        return p < 0.15 ? 0 : (p < 0.45 ? 1 : 2);
    }
}
