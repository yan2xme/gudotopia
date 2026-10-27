import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** LOAD GAME screen: lists every save slot read from the database; pick one and press LOAD. */
public class LoadGamePanel extends ScreenPanel {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final GameFrame frame;
    private final PixelButton load, back;
    private List<SaveInfo> saves = new ArrayList<>();
    private int selected = -1, hover = -1, scroll = 0;
    private String error = "";

    public LoadGamePanel(GameFrame frame) {
        this.frame = frame;
        load = new PixelButton("LOAD", this::loadSelected);
        back = new PixelButton("BACK", frame::showMenu);
        add(load); add(back);
        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { hover = rowAt(e.getX(), e.getY()); }
            @Override public void mouseExited(MouseEvent e) { hover = -1; }
            @Override public void mousePressed(MouseEvent e) {
                int i = rowAt(e.getX(), e.getY());
                if (i >= 0) { selected = i; if (e.getClickCount() >= 2) loadSelected(); }
            }
            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                scroll = Math.max(0, Math.min(Math.max(0, saves.size() - visibleRows()), scroll + e.getWheelRotation()));
            }
        };
        addMouseListener(m); addMouseMotionListener(m); addMouseWheelListener(m);
    }

    /** Re-reads the list from the database every time the screen is opened. */
    @Override protected void onShown() {
        try {
            saves = frame.saves().listSaves();
            error = "";
        } catch (SaveException e) {
            saves = new ArrayList<>();
            error = e.getMessage();
        }
        selected = saves.isEmpty() ? -1 : 0;
        scroll = 0;
    }

    private void loadSelected() {
        if (selected < 0 || selected >= saves.size()) return;
        try {
            frame.loadAndStart(saves.get(selected).id());
        } catch (SaveException e) {
            error = e.getMessage();
        }
    }

    // ---- list geometry (shared by painting and mouse handling)
    private int listX() { return (int) (getWidth() * 0.12); }
    private int listY() { return (int) (getHeight() * 0.20); }
    private int listW() { return (int) (getWidth() * 0.76); }
    private int rowH() { return Math.max(56, (int) (getHeight() * 0.13)); }
    private int rowGap() { return Math.max(6, getHeight() / 70); }
    private int visibleRows() { return Math.max(1, ((int) (getHeight() * 0.55) + rowGap()) / (rowH() + rowGap())); }

    private int rowAt(int mx, int my) {
        if (mx < listX() || mx > listX() + listW() || my < listY()) return -1;
        int row = (my - listY()) / (rowH() + rowGap());
        if ((my - listY()) % (rowH() + rowGap()) > rowH() || row >= visibleRows()) return -1;
        int idx = scroll + row;
        return idx < saves.size() ? idx : -1;
    }

    @Override public void doLayout() {
        int w = getWidth(), h = getHeight();
        back.setBounds(16, 16, (int) (w * 0.17) + 4, (int) (h * 0.10) + 4);
        load.setBounds(w / 2 - (int) (w * 0.13), (int) (h * 0.85), (int) (w * 0.26) + 4, (int) (h * 0.11) + 4);
    }

    @Override protected void paintScreen(Graphics2D g, int w, int h) {
        GameRenderer.hills(g, w, h);
        load.setEnabled(selected >= 0);
        GameRenderer.text(g, "LOAD GAME", w / 2, (int) (h * 0.115), h * 0.07f, Color.WHITE, new Color(0x1F3D22), 0);

        if (saves.isEmpty()) {
            GameRenderer.box(g, listX(), listY(), listW(), (int) (h * 0.3), new Color(0xFFF1CF), Math.max(4, h / 90));
            String l1 = error.isEmpty() ? "No saved gardens yet!" : "Could not read your saves:";
            String l2 = error.isEmpty() ? "Go back and press NEW to plant your first garden." : error;
            GameRenderer.text(g, l1, w / 2, listY() + (int) (h * 0.12), h * 0.045f, new Color(0x3B2B1D), null, 0);
            float s2 = GameRenderer.fitSize(g, l2, h * 0.032f, listW() - 40);
            GameRenderer.text(g, l2, w / 2, listY() + (int) (h * 0.2), s2, error.isEmpty() ? new Color(0x7A6A55) : new Color(0xC62828), null, 0);
            return;
        }

        int rows = visibleRows();
        for (int row = 0; row < rows && scroll + row < saves.size(); row++) {
            int i = scroll + row;
            SaveInfo s = saves.get(i);
            int x = listX(), y = listY() + row * (rowH() + rowGap()), rh = rowH();
            Color fill = i == hover ? new Color(0xFFF8E3) : new Color(0xFFF1CF);
            GameRenderer.box(g, x, y, listW(), rh, fill, Math.max(3, rh / 16));
            if (i == selected) {                                             // yellow frame around the chosen save
                g.setColor(new Color(0xFFD23F)); g.setStroke(new BasicStroke(4));
                g.drawRect(x + 5, y + 5, listW() - 10, rh - 10); g.setStroke(new BasicStroke(1));
            }
            GameRenderer.text(g, s.name(), x + 22, y + (int) (rh * 0.40), rh * 0.34f, new Color(0x2B1D1D), null, -1);
            String line = "$" + s.money() + "   |   Garden " + s.gardenSize() + "x" + s.gardenSize()
                    + "   |   Flowers " + s.discovered() + "/" + FlowerRegistry.ALL.size();
            GameRenderer.text(g, line, x + 22, y + (int) (rh * 0.78), rh * 0.24f, new Color(0x4A6A3A), null, -1);
            GameRenderer.text(g, "Last saved", x + listW() - 22, y + (int) (rh * 0.36), rh * 0.2f, new Color(0x8A7A65), null, 1);
            GameRenderer.text(g, WHEN.format(Instant.ofEpochMilli(s.lastPlayed())), x + listW() - 22, y + (int) (rh * 0.7), rh * 0.26f, new Color(0x3B2B1D), null, 1);
        }
        if (saves.size() > rows)
            GameRenderer.text(g, "scroll for more (" + saves.size() + " saves)", listX() + listW(), listY() + rows * (rowH() + rowGap()) + 14, h * 0.022f, Color.WHITE, GameRenderer.BLACK, 1);
        if (!error.isEmpty())
            GameRenderer.text(g, error, w / 2, (int) (h * 0.83), h * 0.03f, new Color(0xC62828), Color.WHITE, 0);
    }
}
