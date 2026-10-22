import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** A custom-painted green pixel button (thick black border, white text) - no grey Swing look. */
public class PixelButton extends JComponent {
    private String label;
    private final Runnable action;
    private boolean hover, pressed, active;

    public PixelButton(String label, Runnable action) {
        this.label = label;
        this.action = action;
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFocusable(false);
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hover = false; pressed = false; repaint(); }
            @Override public void mousePressed(MouseEvent e) { if (isEnabled()) { pressed = true; repaint(); } }
            @Override public void mouseReleased(MouseEvent e) {
                boolean fire = pressed && contains(e.getPoint());
                pressed = false;
                repaint();
                if (fire && isEnabled()) PixelButton.this.action.run();
            }
        });
    }

    public void setLabel(String s) { if (!s.equals(label)) { label = s; repaint(); } }
    public String getLabel() { return label; }
    /** Draws a yellow frame (used while a tool such as fertilizer is switched on). */
    public void setActive(boolean a) { if (a != active) { active = a; repaint(); } }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        GameRenderer.smooth(g);
        int b = Math.max(3, getHeight() / 12);       // leave room for the drop shadow
        GameRenderer.button(g, 0, 0, getWidth() - b, getHeight() - b, label, hover, pressed, isEnabled(), active);
        g.dispose();
    }
}
