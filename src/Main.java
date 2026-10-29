import javax.swing.*;

/** Entry point. Run this class. */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SaveManager saves;
            try {
                saves = SaveManager.getInstance();  // uses (or creates) ~/.gudotopia/saves/
            } catch (SaveException e) {
                JOptionPane.showMessageDialog(null,
                        "Gudotopia could not open its save folder:\n" + e.getMessage(),
                        "Gudotopia - save problem", JOptionPane.ERROR_MESSAGE);
                return;
            }
            GameFrame frame = new GameFrame(saves);
            frame.setVisible(true);
            frame.showStartupMessage();             // tells the player if an old save.properties was imported
        });
    }
}
