package src.io;

/**
 * Exports a Graph's vertices and edges to a self-describing plain-text file.
 *
 * Output format:
 *
 *   # MAP EXPORT
 *   # Generated: <timestamp>
 *   # Vertices: <count>
 *   # Edges: <count>
 *   #
 *   # VERTEX FORMAT: id x y z forest_coverage
 *   # EDGE FORMAT:   from_id to_id distance grade time fuel forest_coverage
 *   #
 *   VERTICES
 *   0 423.1200 817.0400 45.3300 0.0000
 *   1 425.8800 818.1200 46.1000 1.0000
 *   ...
 *   EDGES
 *   0 1 3.2451 -2.1100 1.0840 0.6910 0.5000
 *   ...
 *
 * The column order in each "FORMAT" comment line always matches the data
 * rows below it, so the file is self-documenting even without this class.
 *
 * NOTE on vertex forest_coverage: Vertex objects don't carry their own
 * coverage value (only edges do, from ForestClassifier). The value written
 * per vertex here is the average coverage across all edges touching that
 * vertex — a representative approximation, not a separately-measured
 * per-vertex score.
 *
 * Usage:
 * <pre>
 *   MapExporter.export(graph, mesh.getVertices(), "output/map.txt");
 * </pre>
 */

import src.graph.CostMetric;
import src.graph.Graph;
import src.model.Edge;
import src.model.Vertex;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class MapExporter {

    // Order of CostMetric values written per edge — keep this in sync
    // with the EDGE FORMAT comment line below.
    private static final CostMetric[] EDGE_METRICS = {
        CostMetric.DISTANCE,
        CostMetric.GRADE,
        CostMetric.TIME,
        CostMetric.FUEL,
        CostMetric.FOREST_COVERAGE
    };

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Writes all vertices and edges in the graph to a plain-text file.
     *
     * @param graph    the graph to export (provides edges via getNeighbors)
     * @param vertices full list of vertices in the graph — Graph doesn't
     *                 expose this directly, so pass the same list used to
     *                 build it (e.g. mesh.getVertices())
     * @param filePath destination file path, e.g. "output/map.txt"
     * @throws IOException if the file cannot be written
     */
    public static void export(
            Graph graph,
            List<Vertex> vertices,
            String filePath) throws IOException {

        int edgeCount = 0;
        for (Vertex v : vertices) {
            edgeCount += graph.getNeighbors(v.id).size();
        }

        try (BufferedWriter w = new BufferedWriter(new FileWriter(filePath))) {
            writeHeader(w, vertices.size(), edgeCount);
            writeVertices(w, graph, vertices);
            writeEdges(w, graph, vertices);
        }

        System.out.printf(
            "Exported %d vertices and %d edges to: %s%n",
            vertices.size(), edgeCount, filePath);
    }

    // -------------------------------------------------------------------------
    // Section writers
    // -------------------------------------------------------------------------

    private static void writeHeader(
            BufferedWriter w,
            int vertexCount,
            int edgeCount) throws IOException {

        String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        w.write("# MAP EXPORT");                              w.newLine();
        w.write("# Generated: " + timestamp);                 w.newLine();
        w.write("# Vertices: " + vertexCount);                w.newLine();
        w.write("# Edges: " + edgeCount);                     w.newLine();
        w.write("#");                                         w.newLine();
        w.write("# VERTEX FORMAT: id x y z forest_coverage"); w.newLine();
        w.write("# EDGE FORMAT:   from_id to_id distance grade time fuel forest_coverage");
        w.newLine();
        w.write("#");                                         w.newLine();
    }

    private static void writeVertices(
            BufferedWriter w,
            Graph graph,
            List<Vertex> vertices) throws IOException {

        w.write("VERTICES");
        w.newLine();

        for (Vertex v : vertices) {
            double coverage = averageVertexCoverage(v, graph);
            w.write(String.format(
                "%d %.4f %.4f %.4f %.4f%n",
                v.id, v.x, v.y, v.z, coverage));
        }

        w.newLine();
    }

    private static void writeEdges(
            BufferedWriter w,
            Graph graph,
            List<Vertex> vertices) throws IOException {

        w.write("EDGES");
        w.newLine();

        for (Vertex v : vertices) {
            for (Edge edge : graph.getNeighbors(v.id)) {
                StringBuilder line = new StringBuilder();
                line.append(edge.from.id).append(' ').append(edge.to.id);

                for (CostMetric metric : EDGE_METRICS) {
                    line.append(' ').append(String.format("%.4f", edge.getMetric(metric)));
                }

                w.write(line.toString());
                w.newLine();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Averages FOREST_COVERAGE across all edges touching this vertex
     * (its outgoing neighbors). Returns 0.0 if the vertex has no edges.
     */
    private static double averageVertexCoverage(Vertex v, Graph graph) {
        List<Edge> neighbors = graph.getNeighbors(v.id);
        if (neighbors.isEmpty()) return 0.0;

        double sum = 0.0;
        for (Edge edge : neighbors) {
            sum += edge.getMetric(CostMetric.FOREST_COVERAGE);
        }
        return sum / neighbors.size();
    }
}