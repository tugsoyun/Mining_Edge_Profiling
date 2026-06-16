package src.io;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import src.model.Vertex;

/**
 * Exports a path (list of vertices) to a PLY file as a polyline.
 *
 * <p>The output uses the PLY "edge" element to connect consecutive
 * vertices, which renderers like MeshLab display as a line. A single
 * random RGB colour is assigned to every vertex in the path so the
 * route stands out clearly when overlaid on the source mesh.</p>
 *
 * <p>Usage:
 * <pre>
 *   List&lt;Vertex&gt; path = Dijkstra.findPath(graph, 0, 1921, CostFunction.byFuel());
 *   PathExporter.export(path, "output/path.ply");
 *
 *   // Or supply your own colour:
 *   PathExporter.export(path, "output/path.ply", 255, 80, 0);
 * </pre>
 * </p>
 */
public class PathExport {

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Writes the path to a PLY file with a randomly chosen colour.
     *
     * @param path     ordered list of vertices (from Dijkstra.findPath)
     * @param filePath destination file path, e.g. "output/path.ply"
     * @throws IOException          if the file cannot be written
     * @throws IllegalArgumentException if the path has fewer than 2 vertices
     */
    public static void export(List<Vertex> path, String filePath) throws IOException {
        Random rng = new Random();
        int r = rng.nextInt(256);
        int g = rng.nextInt(256);
        int b = rng.nextInt(256);
        export(path, filePath, r, g, b);
    }

    /**
     * Writes the path to a PLY file with an explicit RGB colour.
     *
     * @param path     ordered list of vertices
     * @param filePath destination file path
     * @param r        red channel   [0–255]
     * @param g        green channel [0–255]
     * @param b        blue channel  [0–255]
     * @throws IOException              if the file cannot be written
     * @throws IllegalArgumentException if the path has fewer than 2 vertices,
     *                                  or any colour channel is out of range
     */
    public static void export(
            List<Vertex> path,
            String filePath,
            int r, int g, int b) throws IOException {

        if (path == null || path.size() < 2) {
            throw new IllegalArgumentException(
                "Path must contain at least 2 vertices to form a line.");
        }
        validateChannel("r", r);
        validateChannel("g", g);
        validateChannel("b", b);

        int vertexCount = path.size();
        int edgeCount   = path.size() - 1;  // one segment per consecutive pair

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writePlyHeader(writer, vertexCount, edgeCount);
            writeVertices(writer, path, r, g, b);
            writeEdges(writer, edgeCount);
        }

        System.out.printf(
            "Exported path with %d vertices and %d edges to: %s  (colour: rgb(%d, %d, %d))%n",
            vertexCount, edgeCount, filePath, r, g, b);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static void writePlyHeader(
            BufferedWriter writer,
            int vertexCount,
            int edgeCount) throws IOException {

        writer.write("ply\n");
        writer.write("format ascii 1.0\n");
        writer.write("comment exported by PathExporter\n");

        // Vertex element: position + colour
        writer.write("element vertex " + vertexCount + "\n");
        writer.write("property float x\n");
        writer.write("property float y\n");
        writer.write("property float z\n");
        writer.write("property uchar red\n");
        writer.write("property uchar green\n");
        writer.write("property uchar blue\n");

        // Edge element: index pair connecting consecutive vertices
        writer.write("element edge " + edgeCount + "\n");
        writer.write("property int vertex1\n");
        writer.write("property int vertex2\n");

        writer.write("end_header\n");
    }

    private static void writeVertices(
            BufferedWriter writer,
            List<Vertex> path,
            int r, int g, int b) throws IOException {

        for (Vertex v : path) {
            writer.write(String.format(
                "%f %f %f %d %d %d%n",
                v.x, v.y, v.z,
                r, g, b));
        }
    }

    /**
     * Writes sequential index pairs: (0,1), (1,2), ..., (n-2, n-1).
     * Indices are local to this PLY file (0-based position in the path),
     * not the original mesh vertex IDs.
     */
    private static void writeEdges(BufferedWriter writer, int edgeCount) throws IOException {
        for (int i = 0; i < edgeCount; i++) {
            writer.write(String.format("%d %d%n", i, i + 1));
        }
    }

    private static void validateChannel(String name, int value) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException(
                String.format("Colour channel '%s' must be in [0, 255], got %d.", name, value));
        }
    }
}