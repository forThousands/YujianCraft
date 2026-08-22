import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reproducibly generates the selected formal xianxia sword OBJ and its small pixel textures. */
public final class GenerateFormalSwordAssets {
    private record Point(double x, double y, double z) { }
    private record Face(String material, int[] vertices) { }

    private final List<Point> vertices = new ArrayList<>();
    private final List<Face> faces = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Path project = args.length == 0 ? Path.of(".").toAbsolutePath().normalize()
                : Path.of(args[0]).toAbsolutePath().normalize();
        GenerateFormalSwordAssets generator = new GenerateFormalSwordAssets();
        generator.buildSword();
        generator.write(project);
    }

    private void buildSword() {
        addBlade();
        addRadialPrism(0.5, -0.085, -0.055, 0.052, 0.046, 8, "guard");
        addRadialPrism(0.5, -0.055, 0.165, 0.040, 0.037, 8, "grip");
        addRadialPrism(0.5, 0.165, 0.218, 0.061, 0.052, 8, "guard");
        addRadialPrism(0.5, -0.115, -0.082, 0.058, 0.050, 8, "guard");
        addOctahedron(0.5, -0.158, 0.5, 0.058, 0.054, 0.046, "guard");
        addOctahedron(0.5, -0.158, 0.548, 0.024, 0.025, 0.010, "accent");

        // Compact selected guard: two shallow, upturned faceted wings.
        addBeam(0.500, 0.220, 0.410, 0.244, 0.052, 0.075, "guard");
        addBeam(0.410, 0.244, 0.305, 0.218, 0.050, 0.066, "guard");
        addBeam(0.500, 0.220, 0.590, 0.244, 0.052, 0.075, "guard");
        addBeam(0.590, 0.244, 0.695, 0.218, 0.050, 0.066, "guard");
        addOctahedron(0.5, 0.238, 0.5, 0.070, 0.061, 0.060, "guard");
        addOctahedron(0.5, 0.244, 0.563, 0.027, 0.031, 0.012, "accent");
    }

    private void addBlade() {
        double[][] sections = {
                {0.247, 0.175, 0.044, 0.008},
                {0.292, 0.230, 0.052, 0.008},
                {1.570, 0.205, 0.048, 0.007},
                {1.700, 0.145, 0.036, 0.005}
        };
        int[][] rings = new int[sections.length][];
        for (int index = 0; index < sections.length; index++) {
            double y = sections[index][0];
            double halfWidth = sections[index][1] / 2.0;
            double halfRidge = sections[index][2] / 2.0;
            double halfEdge = sections[index][3] / 2.0;
            rings[index] = new int[]{
                    vertex(0.5 - halfWidth, y, 0.5 + halfEdge),
                    vertex(0.5, y, 0.5 + halfRidge),
                    vertex(0.5 + halfWidth, y, 0.5 + halfEdge),
                    vertex(0.5 + halfWidth, y, 0.5 - halfEdge),
                    vertex(0.5, y, 0.5 - halfRidge),
                    vertex(0.5 - halfWidth, y, 0.5 - halfEdge)
            };
        }
        String[] materials = {"blade_dark", "blade_light", "edge", "blade_dark", "blade_light", "edge"};
        for (int section = 0; section < rings.length - 1; section++) {
            for (int side = 0; side < 6; side++) {
                int next = (side + 1) % 6;
                face(materials[side], rings[section][side], rings[section][next],
                        rings[section + 1][next], rings[section + 1][side]);
            }
        }
        int baseCenter = vertex(0.5, sections[0][0], 0.5);
        for (int side = 0; side < 6; side++) {
            int next = (side + 1) % 6;
            face(materials[side], baseCenter, rings[0][next], rings[0][side]);
        }
        int tip = vertex(0.5, 1.840, 0.5);
        int[] last = rings[rings.length - 1];
        for (int side = 0; side < 6; side++) {
            int next = (side + 1) % 6;
            face(materials[side], last[side], last[next], tip);
        }
    }

    private void addRadialPrism(double centerX, double bottom, double top, double radiusX,
                                double radiusZ, int sides, String material) {
        int[] lower = new int[sides];
        int[] upper = new int[sides];
        for (int index = 0; index < sides; index++) {
            double angle = Math.PI * 2.0 * index / sides;
            double x = centerX + Math.cos(angle) * radiusX;
            double z = 0.5 + Math.sin(angle) * radiusZ;
            lower[index] = vertex(x, bottom, z);
            upper[index] = vertex(x, top, z);
        }
        for (int index = 0; index < sides; index++) {
            int next = (index + 1) % sides;
            face(material, lower[index], lower[next], upper[next], upper[index]);
        }
        int lowerCenter = vertex(centerX, bottom, 0.5);
        int upperCenter = vertex(centerX, top, 0.5);
        for (int index = 0; index < sides; index++) {
            int next = (index + 1) % sides;
            face(material, lowerCenter, lower[index], lower[next]);
            face(material, upperCenter, upper[next], upper[index]);
        }
    }

    private void addBeam(double x0, double y0, double x1, double y1,
                         double width, double depth, String material) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double length = Math.sqrt(dx * dx + dy * dy);
        double px = -dy / length * width / 2.0;
        double py = dx / length * width / 2.0;
        double front = 0.5 + depth / 2.0;
        double back = 0.5 - depth / 2.0;
        int a = vertex(x0 + px, y0 + py, front);
        int b = vertex(x1 + px, y1 + py, front);
        int c = vertex(x1 - px, y1 - py, front);
        int d = vertex(x0 - px, y0 - py, front);
        int e = vertex(x0 + px, y0 + py, back);
        int f = vertex(x1 + px, y1 + py, back);
        int g = vertex(x1 - px, y1 - py, back);
        int h = vertex(x0 - px, y0 - py, back);
        face(material, a, b, c, d);
        face(material, h, g, f, e);
        face(material, a, e, f, b);
        face(material, b, f, g, c);
        face(material, c, g, h, d);
        face(material, d, h, e, a);
    }

    private void addOctahedron(double x, double y, double z, double radiusX,
                               double radiusY, double radiusZ, String material) {
        int top = vertex(x, y + radiusY, z);
        int bottom = vertex(x, y - radiusY, z);
        int left = vertex(x - radiusX, y, z);
        int right = vertex(x + radiusX, y, z);
        int front = vertex(x, y, z + radiusZ);
        int back = vertex(x, y, z - radiusZ);
        face(material, top, left, front);
        face(material, top, front, right);
        face(material, top, right, back);
        face(material, top, back, left);
        face(material, bottom, front, left);
        face(material, bottom, right, front);
        face(material, bottom, back, right);
        face(material, bottom, left, back);
    }

    private int vertex(double x, double y, double z) {
        vertices.add(new Point(x, y, z));
        return vertices.size();
    }

    private void face(String material, int... indices) {
        faces.add(new Face(material, indices));
    }

    private void write(Path project) throws IOException {
        Path modelDirectory = project.resolve("src/main/resources/assets/yujiancraft/models/item");
        Path textureDirectory = project.resolve("src/main/resources/assets/yujiancraft/textures/item");
        Files.createDirectories(modelDirectory);
        Files.createDirectories(textureDirectory);

        StringBuilder obj = new StringBuilder("# Generated by tools/GenerateFormalSwordAssets.java\n")
                .append("mtllib formal_flying_sword.mtl\n")
                .append("o formal_flying_sword\n");
        for (Point point : vertices) {
            obj.append(String.format(Locale.ROOT, "v %.6f %.6f %.6f%n", point.x, point.y, point.z));
        }
        String material = "";
        for (Face face : faces) {
            if (!face.material.equals(material)) {
                material = face.material;
                obj.append("usemtl ").append(material).append('\n');
            }
            obj.append('f');
            for (int vertex : face.vertices) obj.append(' ').append(vertex);
            obj.append('\n');
        }
        Files.writeString(modelDirectory.resolve("formal_flying_sword.obj"), obj.toString(), StandardCharsets.UTF_8);

        String mtl = """
                # Generated material bindings; texture keys are supplied by the item model JSON.
                newmtl blade_light
                Kd 1.000 1.000 1.000
                map_Kd #blade_light

                newmtl blade_dark
                Kd 0.720 0.760 0.820
                map_Kd #blade_dark

                newmtl edge
                Kd 1.000 1.000 1.000
                map_Kd #edge

                newmtl guard
                Kd 0.820 0.850 0.900
                map_Kd #guard

                newmtl grip
                Kd 1.000 1.000 1.000
                map_Kd #grip

                newmtl accent
                Kd 1.000 1.000 1.000
                Ka 1.000 1.000 1.000
                map_Kd #accent
                """;
        Files.writeString(modelDirectory.resolve("formal_flying_sword.mtl"), mtl, StandardCharsets.UTF_8);
        writeTextures(textureDirectory);
        System.out.printf(Locale.ROOT, "Generated %d vertices and %d faces%n", vertices.size(), faces.size());
    }

    private static void writeTextures(Path directory) throws IOException {
        writeTexture(directory.resolve("formal_iron_blade.png"), TextureKind.BLADE);
        writeTexture(directory.resolve("formal_iron_edge.png"), TextureKind.EDGE);
        writeTexture(directory.resolve("formal_iron_guard.png"), TextureKind.GUARD);
        writeTexture(directory.resolve("formal_iron_grip.png"), TextureKind.GRIP);
        writeTexture(directory.resolve("formal_iron_accent.png"), TextureKind.ACCENT);
    }

    private enum TextureKind { BLADE, EDGE, GUARD, GRIP, ACCENT }

    private static void writeTexture(Path output, TextureKind kind) throws IOException {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                int color = switch (kind) {
                    case BLADE -> {
                        int ridge = 228 - Math.min(58, Math.abs(x - 15) * 4);
                        int grain = ((x * 13 + y * 7) & 3) - 1;
                        yield rgb(ridge + grain, ridge + 5 + grain, ridge + 12 + grain);
                    }
                    case EDGE -> {
                        int value = 236 + ((x + y) & 1) * 10;
                        yield rgb(value, Math.min(255, value + 3), 255);
                    }
                    case GUARD -> {
                        int bevel = (x < 3 || y < 3) ? 154 : (x > 28 || y > 28) ? 72 : 108;
                        int grain = ((x * 5 + y * 11) & 7) - 3;
                        yield rgb(bevel + grain, bevel + 7 + grain, bevel + 14 + grain);
                    }
                    case GRIP -> {
                        boolean braid = Math.floorMod(x - y, 8) <= 1 || Math.floorMod(x + y, 8) <= 1;
                        yield braid ? rgb(43, 27, 20) : rgb(78 + ((x + y) & 3) * 3, 48, 34);
                    }
                    case ACCENT -> {
                        int distance = Math.abs(x - 15) + Math.abs(y - 15);
                        int light = Math.max(0, 16 - distance) * 5;
                        yield rgb(34 + light / 3, 178 + light / 2, Math.min(255, 210 + light));
                    }
                };
                image.setRGB(x, y, color);
            }
        }
        ImageIO.write(image, "PNG", output.toFile());
    }

    private static int rgb(int red, int green, int blue) {
        return 0xFF000000 | clamp(red) << 16 | clamp(green) << 8 | clamp(blue);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
