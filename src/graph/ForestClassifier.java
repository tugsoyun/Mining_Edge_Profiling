package src.graph;

import src.model.MeshData;
import src.model.Vertex;

import java.util.HashMap;
import java.util.Map;

/**
 * Classifies vertices by forest coverage using LiDAR "Number of Returns".
 *
 * <p>In LiDAR data, a pulse that hits tree canopy produces multiple returns
 * (typically 2–9) as the beam bounces off branches and understory before
 * reaching the ground. A single return usually means bare ground or hard
 * surface. This classifier uses that signal to assign each vertex a
 * forest coverage score in [0.0, 1.0].</p>
 *
 * <p>Per-edge coverage is the mean of its two endpoint scores, so a fully
 * forested edge scores 1.0 and open ground scores 0.0.</p>
 *
 * <p>Usage:
 * <pre>
 *   MeshData mesh = PLYParser.load("data/forest.ply");
 *   ForestClassifier fc = new ForestClassifier(mesh);
 *   Graph graph = new Graph(mesh, fc);
 *
 *   List&lt;Vertex&gt; path = Dijkstra.findPath(graph, 0, 1921,
 *       CostFunction.byForestCoverage());
 * </pre>
 * </p>
 */
public class ForestClassifier {

    /**
     * Minimum number of LiDAR returns to classify a vertex as forest.
     * Returns >= threshold → forest (score 1.0), below → ground (score 0.0).
     * 2 is the standard threshold: any multi-return point indicates canopy.
     */
    public static final int FOREST_RETURN_THRESHOLD = 2;

    // vertexId -> forest score in [0.0, 1.0]
    private final Map<Integer, Double> coverageByVertexId = new HashMap<>();

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Builds forest coverage scores from a loaded MeshData object.
     *
     * <p>The PLY must have been parsed with scalar field support —
     * i.e. {@code mesh.hasReturnData()} must be true. This is automatic
     * when the PLY contains {@code scalar_Number_Of_Returns} and was
     * loaded via {@link src.io.PLYParser}.</p>
     *
     * @param mesh the mesh loaded by PLYParser
     * @throws IllegalArgumentException if the mesh has no LiDAR return data
     */
    public ForestClassifier(MeshData mesh) {
        if (!mesh.hasReturnData()) {
            throw new IllegalArgumentException(
                "MeshData has no LiDAR return data. " +
                "Ensure the PLY was exported from CloudCompare with " +
                "'scalar_Number_Of_Returns' and loaded via PLYParser.");
        }

        float[] returns = mesh.getNumberOfReturns();

        for (int i = 0; i < returns.length; i++) {
            double score = returns[i] >= FOREST_RETURN_THRESHOLD ? 1.0 : 0.0;
            coverageByVertexId.put(i, score);
        }

        long forestCount = coverageByVertexId.values().stream()
            .filter(v -> v > 0.0).count();

        System.out.printf(
            "ForestClassifier: %d / %d vertices classified as forest (%.1f%%)%n",
            forestCount,
            coverageByVertexId.size(),
            100.0 * forestCount / coverageByVertexId.size());
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the forest coverage score for a single vertex, in [0.0, 1.0].
     * Returns 0.0 if the vertex ID was not found (safe default).
     */
    public double getVertexCoverage(Vertex vertex) {
        return coverageByVertexId.getOrDefault(vertex.id, 0.0);
    }

    /**
     * Returns the forest coverage score for an edge, in [0.0, 1.0].
     * Defined as the mean of the two endpoint vertex scores.
     *
     * @param from source vertex of the edge
     * @param to   destination vertex of the edge
     */
    public double getEdgeCoverage(Vertex from, Vertex to) {
        double fromScore = coverageByVertexId.getOrDefault(from.id, 0.0);
        double toScore   = coverageByVertexId.getOrDefault(to.id,   0.0);
        return (fromScore + toScore) / 2.0;
    }

    /** Returns the total number of vertices classified. */
    public int size() {
        return coverageByVertexId.size();
    }
}