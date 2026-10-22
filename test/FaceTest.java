import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.*;

public class FaceTest {
    public static void main(String[] a) throws Exception {
        BufferedImage face = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = face.createGraphics(); g.setColor(new Color(0x66CCFF)); g.fillRect(0, 0, 200, 200);
        g.setColor(Color.RED); g.fillOval(50, 60, 100, 100); g.dispose();
        ImageIO.write(face, "png", new File("assets/faces/flower_01.png"));
        Files.writeString(Path.of("assets/faces/flower_02.png"), "this is not an image");   // corrupt file must not crash
        Flower f1 = FlowerRegistry.byId("flower_01"), f2 = FlowerRegistry.byId("flower_02");
        BufferedImage h1 = PixelArt.head(f1), h2 = PixelArt.head(f2);
        System.out.println("custom face head size: " + h1.getWidth() + "  (96 = real image used)");
        System.out.println("corrupt file falls back to placeholder size: " + h2.getWidth() + "  (24 = placeholder)");
        ImageIO.write(h1, "png", new File(a[0], "custom_face_head.png"));
        Files.delete(Path.of("assets/faces/flower_01.png")); Files.delete(Path.of("assets/faces/flower_02.png"));
    }
}
