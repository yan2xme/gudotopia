# Gudotopia - Java Swing gardening game

Plain Java 17+ / Swing / Java2D. No libraries, no build tool.

## Run in IntelliJ IDEA
1. File > Open... > choose this `Gudotopia` folder (create a project from existing sources if asked).
2. Right-click the `src` folder > Mark Directory as > Sources Root. (Project SDK: Java 17 or newer.)
3. Open `src/Main.java` and press the green run arrow.
4. Run Configuration > Working directory must be the project root (the default), so `assets/faces/` is found.

## Run from a terminal
    ./run.sh            (Linux/macOS)        run.bat  (Windows)

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
| SaveManager | save/load (`~/.gudotopia/save.properties`), safe on corrupt files |
| PixelArt / GameRenderer / PixelButton | generated sprites, drawing helpers, custom button |

## Tweaking
* Drop rates, rewards, growth times: `Rarity.java` (rates must total 100 - checked at startup).
* Flower names/rarities: table in `FlowerRegistry.java`.
* Prices & fertilizer strength: constants at the top of `PlayerData.java`.
* Real faces: see `assets/faces/README.txt`.

## Tests (optional)
    javac -d out src/*.java test/*.java
    java -Djava.awt.headless=true -cp out GameTest
