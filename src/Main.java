import javax.swing.*;

/** Entry point. Run this class. */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SaveManager saves;
            try {
                saves = new SaveManager();          // opens (or creates) ~/.gudotopia/gudotopia.db
            } catch (SaveException e) {
                JOptionPane.showMessageDialog(null,
                        "Gudotopia could not open its database:\n" + e.getMessage()
                        + "\n\nIf the message mentions 'No suitable driver', add lib/sqlite-jdbc-3.46.1.3.jar\n"
                        + "to the project (or open pom.xml as a Maven project).",
                        "Gudotopia - database problem", JOptionPane.ERROR_MESSAGE);
                return;
            }
            GameFrame frame = new GameFrame(saves);
            frame.setVisible(true);
            frame.showStartupMessage();             // tells the player if an old save.properties was imported
        });
    }
}
