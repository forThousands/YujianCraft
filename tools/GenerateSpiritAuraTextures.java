import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reproducibly generates the soft emissive textures used by the volumetric spirit aura. */
public final class GenerateSpiritAuraTextures {
    private static final int SIZE = 16;

    public static void main(String[] args) throws Exception {
        Path project = args.length == 0 ? Path.of(".").toAbsolutePath().normalize()
                : Path.of(args[0]).toAbsolutePath().normalize();
        Path directory = project.resolve("src/main/resources/assets/yujiancraft/textures/effect");
        Files.createDirectories(directory);
        writeShell(directory.resolve("spirit_shell.png"));
        writePulse(directory.resolve("spirit_pulse.png"));
        System.out.println("Generated spirit_shell.png and spirit_pulse.png");
    }

    private static void writeShell(Path output) throws Exception {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double horizontal = Math.abs((x + 0.5D) / SIZE * 2.0D - 1.0D);
                double softFace = 0.30D + 0.70D * Math.pow(1.0D - horizontal, 0.65D);
                double longitudinalShimmer = 0.94D + 0.06D * Math.sin((y + 0.5D) / SIZE * Math.PI);
                image.setRGB(x, y, argb((int) Math.round(255.0D * softFace * longitudinalShimmer)));
            }
        }
        ImageIO.write(image, "PNG", output.toFile());
    }

    private static void writePulse(Path output) throws Exception {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double dx = Math.abs((x + 0.5D) / SIZE * 2.0D - 1.0D);
                double dy = Math.abs((y + 0.5D) / SIZE * 2.0D - 1.0D);
                double glow = Math.exp(-(dx * dx * 2.2D + dy * dy * 5.8D));
                image.setRGB(x, y, argb((int) Math.round(255.0D * glow)));
            }
        }
        ImageIO.write(image, "PNG", output.toFile());
    }

    private static int argb(int alpha) {
        int clamped = Math.max(0, Math.min(255, alpha));
        return clamped << 24 | 0x00FFFFFF;
    }
}
