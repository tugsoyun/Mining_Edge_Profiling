package src.terrain;

import src.model.Vertex;
import src.io.LasLoader;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads point cloud files into a {@link Vertex} list for the rest of
 * the pipeline (subsampling → ground filtering → meshing).
 *
 * <h3>Supported formats</h3>
 * <ul>
 *   <li><b>.las / .laz</b> — read via {@link LasLoader}, which uses
 *       laszip4j (a pure-Java port of LASzip). No PDAL install, no
 *       native binaries — works identically whether the file is
 *       compressed or not.</li>
 *   <li><b>.xyz / .txt</b> — whitespace-delimited text, read directly.
 *       Useful as a saved intermediate to skip LAS/LAZ parsing on
 *       repeat runs.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 *   List&lt;Vertex&gt; points = PointCloudLoader.readPointCloud("data/survey.las");
 *   // or
 *   List&lt;Vertex&gt; points = PointCloudLoader.readPointCloud("data/survey.laz");
 * </pre>
 */
public class PointCloudLoader {

    private PointCloudLoader() { /* utility class */ }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Reads a point cloud file into a list of vertices.
     *
     * @param filePath path to a .las, .laz, .xyz, or .txt file
     * @return list of vertices with X, Y, Z coordinates from the file
     * @throws IOException if the file cannot be found, read, or is an
     *         unsupported format
     */
    public static List<Vertex> readPointCloud(String filePath) throws IOException {
        File f = new File(filePath);
        if (!f.exists()) {
            throw new FileNotFoundException("File not found: " + f.getAbsolutePath());
        }

        String lower = filePath.toLowerCase();

        if (lower.endsWith(".xyz") || lower.endsWith(".txt")) {
            System.out.println("PointCloudLoader: reading XYZ text file directly.");
            return readXYZFile(f);
        }

        if (lower.endsWith(".las") || lower.endsWith(".laz")) {
            System.out.printf("PointCloudLoader: reading %s via laszip4j...%n", f.getName());
            return LasLoader.load(filePath);
        }

        throw new IOException(
            "Unsupported file type: " + filePath + "\n" +
            "Accepted extensions: .las  .laz  .xyz  .txt");
    }

    // -------------------------------------------------------------------------
    // XYZ text reader
    // -------------------------------------------------------------------------

    /**
     * Reads a whitespace-delimited XYZ text file (one "x y z" line per point).
     * Skips header lines that start with non-numeric characters.
     */
    private static List<Vertex> readXYZFile(File file) throws IOException {
        List<Vertex> points = new ArrayList<>();
        int id = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || (!Character.isDigit(line.charAt(0))
                        && line.charAt(0) != '-')) {
                    continue; // skip header/comment lines
                }
                String[] parts = line.split("\\s+");
                if (parts.length < 3) continue;
                try {
                    double x = Double.parseDouble(parts[0]);
                    double y = Double.parseDouble(parts[1]);
                    double z = Double.parseDouble(parts[2]);
                    points.add(new Vertex(id++, x, y, z));
                } catch (NumberFormatException ignored) {
                    // skip malformed lines
                }
            }
        }

        System.out.printf("  XYZ reader: loaded %d points.%n", points.size());
        return points;
    }

    // =========================================================================
    // Interactive entry point
    // =========================================================================

    /**
     * Interactive CLI entry point. Prompts for a point cloud file and loads
     * it into {@link SessionState#rawPoints}. Offers to save the result as
     * XYZ so future sessions can skip LAS/LAZ parsing.
     *
     * <p>Can be called standalone or from {@code Main}:
     * <pre>
     *   PointCloudLoader.run(scanner, state);
     * </pre>
     */
    public static void run(java.util.Scanner sc, SessionState state) {
        final String LAS_DIR = "data/las/";
        new java.io.File(LAS_DIR).mkdirs();

        while (true) {
            System.out.println("\n─────────────────────────────────────");
            System.out.println(" LOAD POINT CLOUD");
            System.out.println("─────────────────────────────────────");
            if (state.rawPoints != null) {
                System.out.printf("  Currently loaded: %d raw points.%n", state.rawPoints.size());
                System.out.print("  Reload? [y/n]: ");
                if (!sc.nextLine().trim().equalsIgnoreCase("y")) return;
            }
            System.out.printf("Directory: %s%n", new java.io.File(LAS_DIR).getAbsolutePath());
            System.out.println("Accepted: .las  .laz  .xyz  .txt");
            System.out.print("Filename [or 'back']: ");
            String input = sc.nextLine().trim();
            if (input.equalsIgnoreCase("back") || input.equalsIgnoreCase("b")) return;

            java.io.File f = new java.io.File(LAS_DIR + input);
            if (!f.exists()) {
                System.out.printf("  Not found: %s%n", f.getAbsolutePath());
                continue;
            }
            try {
                List<Vertex> points = readPointCloud(f.getPath());
                System.out.printf("  Loaded %d points.%n", points.size());
                state.rawPoints = points;
                offerSaveXYZ(sc, points, "raw", LAS_DIR);
                return;
            } catch (java.io.IOException e) {
                System.out.printf("  Error: %s%n", e.getMessage());
            }
        }
    }

    private static void offerSaveXYZ(java.util.Scanner sc, List<Vertex> points,
                                      String label, String dir) {
        System.out.printf("Save %s points (%d) as XYZ for reuse? [y/n]: ", label, points.size());
        if (!sc.nextLine().trim().equalsIgnoreCase("y")) return;
        String def = label + "_" + System.currentTimeMillis() + ".xyz";
        System.out.printf("  Filename [default: %s]: ", def);
        String name = sc.nextLine().trim();
        if (name.isEmpty()) name = def;
        if (!name.toLowerCase().endsWith(".xyz")) name += ".xyz";
        try (java.io.PrintWriter pw = new java.io.PrintWriter(dir + name)) {
            for (Vertex v : points) pw.printf("%.6f %.6f %.6f%n", v.x, v.y, v.z);
            System.out.printf("  Saved to %s%n", new java.io.File(dir + name).getAbsolutePath());
        } catch (java.io.IOException e) {
            System.out.printf("  Save failed: %s%n", e.getMessage());
        }
    }
}