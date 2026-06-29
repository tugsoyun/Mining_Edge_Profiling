package src.io;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import src.model.Vertex;

/**
 * Exports computed routes for visualisation in CloudCompare.
 *
 * Supported formats:
 *   - XYZ  (plain coordinate list)
 *   - PLY  (coloured point cloud)
 *
 * Colour strategy for PLY export
 * ───────────────────────────────
 * CloudCompare meshes are typically displayed in grey-scale (intensity-
 * mapped from the scanner return or a scalar field), or occasionally in
 * earth tones when RGB is present. The colours that stand out most against
 * that background are highly saturated, warm hues that have no overlap with
 * grey. A vivid, slightly warm YELLOW-GREEN (lime) works well because:
 *
 *   • It contrasts against both dark-grey rock and light-grey chalk surfaces.
 *   • It is easily distinguishable from the blue water/shadow regions visible
 *     in the point cloud.
 *   • It differs from the red/orange axis indicators CloudCompare draws.
 *
 * The colour is fixed (not random) so that multiple exported paths can be
 * visually separated by loading them into different layers.  A different
 * high-contrast colour is cycled through for each path index 0–5 to make
 * session-level comparisons easy without any manual re-colouring in
 * CloudCompare.
 *
 * If you need a specific colour, call
 *   exportPathPLY(path, filename, r, g, b)
 * directly.
 */
public class PathExporter {

    /**
     * Pre-defined high-contrast colours for paths 0–5.
     * All chosen to stand out against a grey/blue-grey mesh in CloudCompare.
     *
     *   0 — Lime yellow-green  (255, 230,   0)
     *   1 — Hot magenta        (255,   0, 200)
     *   2 — Cyan               (  0, 255, 230)
     *   3 — Vivid orange       (255, 120,   0)
     *   4 — Electric violet    (180,   0, 255)
     *   5 — Spring green       (  0, 255, 100)
     */
    private static final int[][] CONTRAST_COLORS = {
        {255, 230,   0},   // lime yellow
        {255,   0, 200},   // hot magenta
        {  0, 255, 230},   // cyan
        {255, 120,   0},   // vivid orange
        {180,   0, 255},   // electric violet
        {  0, 255, 100},   // spring green
    };

    /** Counter so successive calls without an explicit colour auto-cycle. */
    private static int colorIndex = 0;

    // -------------------------------------------------------------------------
    // XYZ export
    // -------------------------------------------------------------------------

    /**
     * Exports a path as a plain XYZ file (one "x y z" line per vertex).
     * CloudCompare can open this directly as a text file.
     *
     * @param path     vertex IDs in path order
     * @param vertices full vertex list (indexed by ID)
     * @param filename destination file path
     */
    public static void exportPathXYZ(
            List<Integer> path,
            List<Vertex> vertices,
            String filename) throws IOException {

        try (PrintWriter out = new PrintWriter(filename)) {
            for (int id : path) {
                Vertex v = vertices.get(id);
                out.printf("%.6f %.6f %.6f%n", v.x, v.y, v.z);
            }
        }
    }

    // -------------------------------------------------------------------------
    // PLY export — auto colour
    // -------------------------------------------------------------------------

    /**
     * Exports a path as a PLY point cloud with an automatically assigned
     * high-contrast colour.  Each successive call cycles through the palette
     * defined in {@link #CONTRAST_COLORS}, so paths exported in the same
     * session will each get a distinct colour when loaded together in
     * CloudCompare.
     *
     * @param path     vertices in path order
     * @param filename destination file path
     */
    public static void exportPathPLY(
            List<Vertex> path,
            String filename) throws IOException {

        int[] color = CONTRAST_COLORS[colorIndex % CONTRAST_COLORS.length];
        colorIndex++;
        exportPathPLY(path, filename, color[0], color[1], color[2]);
    }

    // -------------------------------------------------------------------------
    // PLY export — explicit colour
    // -------------------------------------------------------------------------

    /**
     * Exports a path as a PLY point cloud with an explicit RGB colour.
     *
     * <p>Use this overload when you need a specific colour — for example to
     * match a legend in a report, or to override the auto-cycle palette.</p>
     *
     * @param path     vertices in path order
     * @param filename destination file path
     * @param r        red channel   0–255
     * @param g        green channel 0–255
     * @param b        blue channel  0–255
     */
    public static void exportPathPLY(
            List<Vertex> path,
            String filename,
            int r, int g, int b) throws IOException {

        // Clamp channels to valid range
        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));

        try (PrintWriter out = new PrintWriter(filename)) {
            // PLY header
            out.println("ply");
            out.println("format ascii 1.0");
            out.println("element vertex " + path.size());
            out.println("property float x");
            out.println("property float y");
            out.println("property float z");
            out.println("property uchar red");
            out.println("property uchar green");
            out.println("property uchar blue");
            out.println("end_header");

            // Vertex data
            for (Vertex v : path) {
                out.printf("%.6f %.6f %.6f %d %d %d%n",
                        v.x, v.y, v.z, r, g, b);
            }
        }
    }
}