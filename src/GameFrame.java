import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * The main window. Holds all screens in a CardLayout and manages WHICH save slot is currently open.
 * The save/load work itself is done by SaveManager (text files); this class only decides when to call it.
 */
public class GameFrame extends JFrame {
    public static final String MENU = "menu", GARDEN = "garden", COLLECTION = "collection",
            HOWTO = "howto", NEW = "new", LOAD = "load";

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final SaveManager saveManager;

    private PlayerData data;                 // the save slot that is open right now (null while only the menu was used)
    private GardenPanel garden;              // rebuilt for every game that is started, so saves never mix
    private FlowerCollectionPanel collection;
    private String lastSaveError;

    public GameFrame(SaveManager saveManager) {
        super("Gudotopia");
        this.saveManager = saveManager;

        root.add(new MainMenuPanel(this), MENU);
        root.add(new HowToPlayPanel(this), HOWTO);
        root.add(new NewGamePanel(this), NEW);
        root.add(new LoadGamePanel(this), LOAD);
        setContentPane(root);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);          // we save first, then exit (see exitGame)
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { exitGame(); }
        });
        setSize(1100, 700);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        showMenu();
    }

    public SaveManager saves() { return saveManager; }
    public PlayerData getData() { return data; }
    public String getLastSaveError() { return lastSaveError; }

    /** Shows the result of the old save.properties import (if any). Call after the window is visible. */
    public void showStartupMessage() {
        String msg = saveManager.takeStartupMessage();
        if (msg != null) JOptionPane.showMessageDialog(this, msg, "Gudotopia", JOptionPane.INFORMATION_MESSAGE);
    }

    // ------------------------------------------------------------------ saving / switching games
    /** Writes the open game to its save file. Returns false (and remembers the reason) if that failed - progress stays in memory. */
    public boolean saveGame() {
        if (data == null) return true;
        try {
            saveManager.save(data);
            lastSaveError = null;
            return true;
        } catch (SaveException e) {
            lastSaveError = e.getMessage();
            System.err.println("Save failed: " + e.getMessage());
            return false;
        }
    }

    /** Before switching to another save the current one is saved; if that fails we refuse to switch. */
    private void saveCurrentBeforeSwitch() {
        if (data != null && !saveGame())
            throw new SaveException("Your current garden could not be saved (" + lastSaveError + "), so nothing was changed.");
    }

    /** NEW: create a brand-new save slot and start playing it. */
    public void createAndStart(String name) {
        saveCurrentBeforeSwitch();
        startGame(saveManager.createSave(name));
    }

    /** LOAD: open an existing save slot. */
    public void loadAndStart(long saveId) {
        saveCurrentBeforeSwitch();
        startGame(saveManager.loadSave(saveId));
    }

    /** Builds fresh garden + collection screens for this save, so nothing from another save can leak in. */
    private void startGame(PlayerData newData) {
        if (garden != null) root.remove(garden);
        if (collection != null) root.remove(collection);
        data = newData;
        garden = new GardenPanel(this, data);
        collection = new FlowerCollectionPanel(this, data);
        root.add(garden, GARDEN);
        root.add(collection, COLLECTION);
        root.revalidate();
        showGarden();
    }

    // ------------------------------------------------------------------ navigation
    public void show(String screen) { cards.show(root, screen); }
    public void showMenu() { show(MENU); }
    public void showNew() { show(NEW); }
    public void showLoad() { show(LOAD); }
    public void showHowTo() { show(HOWTO); }
    public void showGarden() { if (data != null) show(GARDEN); else showMenu(); }
    public void showCollection() { if (data != null) show(COLLECTION); else showMenu(); }

    /** EXIT: save (commit) the open game, then close. If saving fails the player may stay and try again. */
    public void exitGame() {
        if (!saveGame()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Your progress could not be saved:\n" + lastSaveError + "\n\nExit anyway and lose recent progress?",
                    "Save failed", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) return;
        }
        dispose();
        System.exit(0);
    }
}
