import javax.swing.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;

/** NEW GAME screen: type a name for the garden (the save slot) and create it. Never touches other saves. */
public class NewGamePanel extends ScreenPanel {
    private final GameFrame frame;
    private final JTextField field = new JTextField();
    private final PixelButton create, back;
    private String error = "";

    public NewGamePanel(GameFrame frame) {
        this.frame = frame;
        create = new PixelButton("CREATE", this::submit);
        back = new PixelButton("BACK", frame::showMenu);
        add(create); add(back); add(field);

        // pixel-style text box that accepts at most 20 characters
        field.setOpaque(true);
        field.setBackground(Color.WHITE);
        field.setForeground(new Color(0x2B1D1D));
        field.setCaretColor(GameRenderer.BLACK);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GameRenderer.BLACK, 4), BorderFactory.createEmptyBorder(4, 12, 4, 12)));
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override public void replace(FilterBypass fb, int off, int len, String text, AttributeSet a) throws BadLocationException {
                String cur = fb.getDocument().getText(0, fb.getDocument().getLength());
                String next = cur.substring(0, off) + (text == null ? "" : text) + cur.substring(off + len);
                if (next.length() <= SaveManager.MAX_NAME_LENGTH) super.replace(fb, off, len, text, a);
            }
            @Override public void insertString(FilterBypass fb, int off, String text, AttributeSet a) throws BadLocationException {
                replace(fb, off, 0, text, a);
            }
        });
        field.addActionListener(e -> submit());                 // pressing Enter also creates the game
    }

    @Override protected void onShown() {
        field.setText("");
        error = "";
        SwingUtilities.invokeLater(field::requestFocusInWindow);
    }

    private void submit() {
        String problem = SaveManager.validateName(field.getText());
        if (problem != null) { error = problem; return; }
        try {
            frame.createAndStart(field.getText());              // creates the save slot as a save file, then opens the garden
        } catch (SaveException e) {
            error = e.getMessage();                             // e.g. "A save called ... already exists"
        }
    }

    private int boxX() { return (int) (getWidth() * 0.2); }
    private int boxY() { return (int) (getHeight() * 0.2); }

    @Override public void doLayout() {
        int w = getWidth(), h = getHeight();
        back.setBounds(16, 16, (int) (w * 0.17) + 4, (int) (h * 0.10) + 4);
        field.setFont(GameRenderer.font(Math.max(16f, h * 0.045f)));
        field.setBounds(boxX() + (int) (w * 0.06), boxY() + (int) (h * 0.30), (int) (w * 0.48), (int) (h * 0.10));
        create.setBounds(w / 2 - (int) (w * 0.12), boxY() + (int) (h * 0.575), (int) (w * 0.24) + 4, (int) (h * 0.12) + 4);
    }

    @Override protected void paintScreen(Graphics2D g, int w, int h) {
        GameRenderer.hills(g, w, h);
        GameRenderer.box(g, boxX(), boxY(), (int) (w * 0.6), (int) (h * 0.74), new Color(0xFFF1CF), Math.max(4, h / 90));
        GameRenderer.text(g, "NEW GAME", w / 2, boxY() + (int) (h * 0.11), h * 0.065f, new Color(0x4A9A4E), new Color(0x1F3D22), 0);
        GameRenderer.text(g, "Name your garden:", boxX() + (int) (w * 0.06), boxY() + (int) (h * 0.265), h * 0.034f, new Color(0x3B2B1D), null, -1);
        GameRenderer.text(g, "Each name is its own save slot. Max " + SaveManager.MAX_NAME_LENGTH + " characters.", w / 2, boxY() + (int) (h * 0.46), h * 0.024f, new Color(0x7A6A55), null, 0);
        if (!error.isEmpty()) {
            float size = GameRenderer.fitSize(g, error, h * 0.03f, (int) (w * 0.56));
            GameRenderer.text(g, error, w / 2, boxY() + (int) (h * 0.53), size, new Color(0xC62828), null, 0);
        }
    }
}
