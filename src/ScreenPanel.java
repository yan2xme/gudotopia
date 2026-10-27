import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;

/**
 * Base class of every screen. Paints the animated sky, runs a Swing Timer (~30 fps) while visible
 * and calls onTick() so screens can animate / update the game WITHOUT blocking the UI thread.
 */
public abstract class ScreenPanel extends JPanel {
    protected double time;                 // seconds since start (for animations)
    private final Timer timer;
    private long last = System.nanoTime();

    protected ScreenPanel() {
        setLayout(null);
        setOpaque(true);
        timer = new Timer(33, e -> {
            long now = System.nanoTime();
            long dt = (now - last) / 1_000_000L;
            last = now;
            tick(dt);
            repaint();
        });
        addHierarchyListener(ev -> {
            if ((ev.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) { timer.start(); onShown(); } else timer.stop();
            }
        });
    }

    /** Advance animation + game by dtMs milliseconds (also callable directly from tests). */
    public void tick(long dtMs) {
        time += Math.min(dtMs, 100) / 1000.0;   // clouds should not jump after a long pause
        onTick(dtMs);
    }

    protected void onTick(long dtMs) { }

    /** Called each time this screen becomes visible (e.g. to reset a text field or refresh a list). */
    protected void onShown() { }

    @Override protected final void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        GameRenderer.smooth(g);
        GameRenderer.sky(g, getWidth(), getHeight());
        GameRenderer.clouds(g, getWidth(), getHeight(), time);
        paintScreen(g, getWidth(), getHeight());
        g.dispose();
    }

    protected abstract void paintScreen(Graphics2D g, int w, int h);
}
