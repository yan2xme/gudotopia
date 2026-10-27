import java.util.ArrayList;
import java.util.List;

/**
 * FERTILIZER shop: buy a RANDOM fertilizer, see your stock, and click one to use it
 * (the garden then applies it to the next plot you click).
 */
public class FertilizerDialog extends ItemDialog {
    public FertilizerDialog(GardenPanel owner, PlayerData data) { super(owner, data); }

    @Override protected String title() { return "FERTILIZER SHOP"; }
    @Override protected String buyLabel() { return "BUY FERTILIZER $" + FertilizerRegistry.SHOP_PRICE; }
    @Override protected String listHeader() { return "YOUR FERTILIZER  (click one to use it)"; }
    @Override protected String ratesHeader() { return "Chance per purchase"; }
    @Override protected PlayerData.Result doBuy() { return data.buyFertilizer(); }

    private Entry entryOf(Fertilizer f, int count) {
        return new Entry(f.name, f.effectText() + " (worth $" + f.price + ")", f.rarity, count, f,
                (g, x, y, size) -> PixelArt.draw(g, PixelArt.fertilizerBag(f), x, y, size, size));
    }

    @Override protected Entry entryFor(PlayerData.Result r) { return entryOf(r.fertilizer, 0); }

    @Override protected List<Entry> entries() {
        List<Entry> list = new ArrayList<>();
        for (Fertilizer f : data.getFertilizers().owned()) list.add(entryOf(f, data.getFertilizers().count(f)));
        return list;
    }

    @Override protected List<RateLine> rateLines() {
        List<RateLine> list = new ArrayList<>();
        for (Fertilizer f : FertilizerRegistry.ALL)
            list.add(new RateLine(f.color, f.name.replace(" Fertilizer", "") + "  " + Math.round(FertilizerRegistry.chanceOf(f)) + "%  " + "(+" + Math.round(f.growthBonus * 100) + "% speed)"));
        return list;
    }

    /** Clicking a fertilizer in your stock picks it as the active tool. */
    @Override protected boolean onEntryClicked(Entry e) {
        owner.selectFertilizer((Fertilizer) e.payload);
        return true;
    }
}
