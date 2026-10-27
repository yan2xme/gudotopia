/**
 * One square of soil. It remembers:
 *  - whether the soil has been WATERED (required before planting)
 *  - the FERTILIZER effect on it (optional; may be applied before or after planting)
 *  - the planted flower, how far it has grown, and - once fully grown - the DEADLINE by which it must be harvested.
 *
 * All timing uses real timestamps (milliseconds since 1970, "epoch ms"), not a running timer, so growth and
 * death are still calculated correctly after switching screens or closing and reopening the game.
 */
public class GardenPlot {
    public enum Event { NONE, MATURED, DIED }

    private boolean watered;
    private Fertilizer fertilizer;
    private Flower flower;            // null = nothing planted
    private long growthMs;            // how much "growth time" the flower has accumulated (fertilizer makes it count faster)
    private long updatedMs;           // the moment growthMs was last brought up to date
    private long deathDeadlineMs;     // when a fully grown flower dies (0 = not fully grown yet)

    // ---------------------------------------------------------------- state queries
    public boolean isEmpty() { return flower == null; }
    public boolean isWatered() { return watered; }
    public Flower getFlower() { return flower; }
    public Fertilizer getFertilizer() { return fertilizer; }
    public long getGrowthMs() { return growthMs; }
    public long getUpdatedMs() { return updatedMs; }
    public long getDeathDeadlineMs() { return deathDeadlineMs; }

    public boolean isMature() { return flower != null && growthMs >= flower.growthMillis(); }

    /** 0.0 (just planted) ... 1.0 (fully grown). */
    public double progress() {
        return flower == null ? 0 : Math.min(1.0, growthMs / (double) flower.growthMillis());
    }

    /** Real milliseconds until the flower is fully grown (takes the fertilizer speed-up into account). */
    public long remainingMs() {
        if (flower == null || isMature()) return 0;
        double speed = fertilizer == null ? 1.0 : fertilizer.speedMultiplier();
        return (long) Math.ceil((flower.growthMillis() - growthMs) / speed);
    }

    /** Milliseconds left before a fully grown flower dies, or -1 if it is not fully grown. */
    public long msUntilDeath(long nowMs) {
        return isMature() ? Math.max(0, deathDeadlineMs - nowMs) : -1;
    }

    /** 0 = seed, 1 = sprout, 2 = growing, 3 = fully grown, -1 = nothing planted. */
    public int stage() {
        if (flower == null) return -1;
        if (isMature()) return 3;
        double p = progress();
        return p < 0.15 ? 0 : (p < 0.45 ? 1 : 2);
    }

    // ---------------------------------------------------------------- actions
    public void water() { watered = true; }

    public void setFertilizer(Fertilizer f) { this.fertilizer = f; }

    /** Plants a flower. The caller (PlayerData) has already checked that the soil is watered. */
    public void plant(Flower f, long nowMs) {
        flower = f;
        growthMs = 0;
        deathDeadlineMs = 0;
        updatedMs = nowMs;
    }

    /** Back to bare, dry soil with no fertilizer - used after harvest and after a flower dies. */
    public void reset() {
        flower = null;
        watered = false;
        fertilizer = null;
        growthMs = 0;
        deathDeadlineMs = 0;
    }

    /** Used by the database loader to rebuild a plot exactly as it was saved. */
    public void restore(boolean watered, Flower f, Fertilizer fert, long growthMs, long updatedMs, long deathDeadlineMs) {
        this.watered = watered;
        this.flower = f;
        this.fertilizer = fert;
        this.growthMs = f == null ? 0 : Math.max(0, growthMs);
        this.updatedMs = updatedMs;
        this.deathDeadlineMs = f == null ? 0 : Math.max(0, deathDeadlineMs);
        if (isMature() && this.deathDeadlineMs == 0) this.deathDeadlineMs = updatedMs + GameConfig.DEATH_MS;
    }

    // ---------------------------------------------------------------- time
    /**
     * Brings this plot up to date with the clock: grows the flower, notices the moment it became fully grown
     * (starting the death countdown from THAT moment, not from planting) and detects death.
     * Returns DIED if the flower died - the caller must then call reset().
     */
    public Event advance(long nowMs) {
        if (flower == null) { updatedMs = nowMs; return Event.NONE; }
        if (nowMs <= updatedMs) return Event.NONE;      // no time has passed (or the clock moved backwards)

        Event result = Event.NONE;
        if (!isMature()) {
            double speed = fertilizer == null ? 1.0 : fertilizer.speedMultiplier();
            long total = flower.growthMillis();
            long elapsed = nowMs - updatedMs;
            long needed = total - growthMs;
            if (elapsed * speed >= needed) {
                // It finished growing somewhere inside this interval: work out exactly when.
                long timeToGrow = (long) Math.ceil(needed / speed);
                deathDeadlineMs = updatedMs + timeToGrow + GameConfig.DEATH_MS;
                growthMs = total;
                result = Event.MATURED;
            } else {
                growthMs += Math.round(elapsed * speed);
            }
        }
        updatedMs = nowMs;
        if (isMature() && nowMs >= deathDeadlineMs) return Event.DIED;
        return result;
    }
}
