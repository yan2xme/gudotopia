import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** SEEDS shop: buy a RANDOM seed for $2 and look at your seed bag. (Planting needs no seed selection.) */
public class SeedDialog extends ItemDialog {
    public SeedDialog(GardenPanel owner, PlayerData data) { super(owner, data); }

    @Override protected String title() { return "SEED SHOP"; }
    @Override protected String buyLabel() { return "BUY SEED $" + PlayerData.SEED_COST; }
    @Override protected String listHeader() { return "YOUR SEEDS  (a random one is planted)"; }
    @Override protected String ratesHeader() { return "Drop rates"; }
    @Override protected PlayerData.Result doBuy() { return data.buySeed(); }

    private Entry entryOf(Flower f, int count) {
        return new Entry(f.name, f.rarity.label + ", sells for $" + f.reward + ", grows in " + f.growthSeconds + "s", f.rarity, count, f,
                (g, x, y, size) -> PixelArt.draw(g, PixelArt.head(f), x, y, size, size));
    }

    @Override protected Entry entryFor(PlayerData.Result r) { return entryOf(r.flower, 0); }

    @Override protected List<Entry> entries() {
        List<Entry> list = new ArrayList<>();
        for (Flower f : data.getSeeds().ownedFlowers()) list.add(entryOf(f, data.getSeeds().count(f)));
        return list;
    }

    @Override protected List<RateLine> rateLines() {
        List<RateLine> list = new ArrayList<>();
        for (Rarity r : Rarity.values()) list.add(new RateLine(r.color, r.label + "  " + r.dropPercent + "%"));
        return list;
    }
}
