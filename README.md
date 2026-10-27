# Gudotopia - Java Swing gardening game

Java 17+ / Swing / Java2D with SQLite saves. Art is drawn in code.

## Requirements
Java 17 or newer. The game stores saves in **SQLite** through JDBC (`org.xerial:sqlite-jdbc 3.46.1.3`, already included in `lib/`).

## Run in IntelliJ IDEA
**Option A - Maven (recommended):** File > Open... > select `pom.xml` > "Open as Project". IntelliJ downloads the SQLite driver itself.
Then open `src/Main.java` and press the green run arrow. (Project SDK: Java 17+.)

**Option B - no Maven:** File > Open the `Gudotopia` folder, right-click `src` > Mark Directory as > Sources Root, then
File > Project Structure > Libraries > + > Java > choose `lib/sqlite-jdbc-3.46.1.3.jar`. Run `src/Main.java`.
(The folder `src/gudotopia/` holds old unused draft files; ignore or delete it.)

Run configuration > Working directory must be the project root (the default).

## Run from a terminal
    ./run.sh            (Linux/macOS)        run.bat  (Windows)

## Where is the data?
`~/.gudotopia/gudotopia.db` (Windows: `C:\Users\<you>\.gudotopia\gudotopia.db`). Demo queries: `sql/demo_queries.sql`.

## Files (all in `src/`)
| File | Job |
|---|---|
| Main | entry point |
| GameFrame | window, screen switching (CardLayout), owns PlayerData + SaveManager |
| ScreenPanel | base screen: sky, drifting clouds, Swing Timer (~30 fps) |
| MainMenuPanel / HowToPlayPanel | title screen / instructions |
| GardenPanel | isometric garden, mouse clicks on plots, HUD, toasts |
| ShopPanel | right-hand FERTILIZER / SEEDS / LARGER PLOT / FLOWERS buttons |
| SeedDialog | seed shop pop-up: buy random seeds, pick a seed to plant |
| FlowerCollectionPanel | encyclopedia grid, locked = silhouette, "Discovered x / 30" |
| PlayerData | **all game rules**: money, seeds, plant, harvest, fertilize, expand (no Swing) |
| GardenPlot / SeedInventory | plot growth state / owned seeds + selection |
| Flower / Rarity / FlowerRegistry | flower data, **drop rates + rewards**, the 30-flower table |
| SaveManager | SQLite/JDBC database: schema, saves, load/save, one-time import of old `save.properties` |
| NewGamePanel / LoadGamePanel | create a named save / choose a save |
| Fertilizer / FertilizerRegistry / FertilizerInventory / FertilizerDialog | fertilizer items, shop odds, inventory, pop-up |
| GameConfig | timings (30 s death timer etc.) |
| PixelArt / GameRenderer / PixelButton | generated sprites, drawing helpers, custom button |

## Tweaking
* Drop rates, rewards, growth times: `Rarity.java` (rates must total 100 - checked at startup).
* Flower names/rarities: table in `FlowerRegistry.java`.
* Fertilizer price/odds: `FertilizerRegistry.java`. Seed price and expansion costs: top of `PlayerData.java`.
* Timings (JVM options): `-Dgudotopia.deathSeconds=30 -Dgudotopia.warningSeconds=10 -Dgudotopia.growthScale=1.0 -Dgudotopia.autosaveSeconds=15 -Dgudotopia.dir=<folder>`.
* Real faces: see `assets/faces/README.txt`.

## Tests (optional)
    javac -d out -cp "lib/*" src/*.java test/*.java
    java -Djava.awt.headless=true -cp "out:lib/*" GameTest      (game rules)
    java -Djava.awt.headless=true -cp "out:lib/*" DbTest        (SQLite; uses a temp folder)
(`UiCheck` drives the real window with a robot and needs a display.)
