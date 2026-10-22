import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** The main window. Holds all screens in a CardLayout and owns the player data + save manager. */
public class GameFrame extends JFrame {
    public static final String MENU = "menu", GARDEN = "garden", COLLECTION = "collection", HOWTO = "howto";

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final SaveManager saveManager;
    private final PlayerData data;

    public GameFrame() { this(new SaveManager()); }

    public GameFrame(SaveManager saveManager) {
        super("Gudotopia");
        this.saveManager = saveManager;
        this.data = saveManager.load();

        root.add(new MainMenuPanel(this), MENU);
        root.add(new GardenPanel(this, data), GARDEN);
        root.add(new FlowerCollectionPanel(this, data), COLLECTION);
        root.add(new HowToPlayPanel(this), HOWTO);
        setContentPane(root);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { saveGame(); }
        });
        setSize(1100, 700);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        showMenu();
    }

    public PlayerData getData() { return data; }
    public void saveGame() { saveManager.save(data); }

    public void show(String screen) { cards.show(root, screen); }
    public void showMenu() { show(MENU); }
    public void showGarden() { show(GARDEN); }
    public void showCollection() { show(COLLECTION); }
    public void showHowTo() { show(HOWTO); }
}
