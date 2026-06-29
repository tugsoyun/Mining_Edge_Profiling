package src.graph;

import src.model.MeshData;
import src.model.Vertex;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Finds vertices within a bounding box of X/Y coordinates.
 *
 * <p>Used to select start and end points for pathfinding when you know
 * a rough area rather than an exact vertex ID.</p>
 *
 * <p>Usage:
 * <pre>
 *   // Find any vertex in a region
 *   Vertex start = VertexSelector.findNearest(mesh, 423.0, 817.0);
 *   Vertex end   = VertexSelector.findNearest(mesh, 1204.0, 2391.0);
 *
 *   // Find all vertices within a bounding box
 *   List&lt;Vertex&gt; candidates = VertexSelector.findInRange(mesh,
 *       xMin, xMax, yMin, yMax);
 *
 *   // Pick the one closest to a target point
 *   Vertex best = VertexSelector.findNearest(mesh, targetX, targetY);
 * </pre>
 * </p>
 */
public class VertexSelector {

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns all vertices whose X and Y coordinates fall within the given range.
     *
     * @param mesh the loaded mesh
     * @param xMin minimum X coordinate (inclusive)
     * @param xMax maximum X coordinate (inclusive)
     * @param yMin minimum Y coordinate (inclusive)
     * @param yMax maximum Y coordinate (inclusive)
     * @return list of matching vertices, possibly empty
     */
    public static List<Vertex> findInRange(
            MeshData mesh,
            double xMin, double xMax,
            double yMin, double yMax) {

        validateRange(xMin, xMax, "X");
        validateRange(yMin, yMax, "Y");

        List<Vertex> results = mesh.getVertices().stream()
            .filter(v -> v.x >= xMin && v.x <= xMax
                      && v.y >= yMin && v.y <= yMax)
            .collect(Collectors.toList());

        System.out.printf(
            "VertexSelector: found %d vertices in range x[%.2f, %.2f] y[%.2f, %.2f]%n",
            results.size(), xMin, xMax, yMin, yMax);

        return results;
    }

    /**
     * Returns the single vertex closest to (targetX, targetY) within a
     * bounding box, using 2-D Euclidean distance (Z is ignored).
     *
     * <p>Useful when you want exactly one start or end point from a region.</p>
     *
     * @param mesh    the loaded mesh
     * @param xMin    minimum X coordinate
     * @param xMax    maximum X coordinate
     * @param yMin    minimum Y coordinate
     * @param yMax    maximum Y coordinate
     * @param targetX X coordinate of the target point
     * @param targetY Y coordinate of the target point
     * @return the nearest vertex inside the bounding box
     * @throws IllegalArgumentException if no vertices exist in the range
     */
    public static Vertex findNearest(
            MeshData mesh,
            double xMin, double xMax,
            double yMin, double yMax,
            double targetX, double targetY) {

        List<Vertex> candidates = findInRange(mesh, xMin, xMax, yMin, yMax);

        if (candidates.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                "No vertices found in range x[%.2f, %.2f] y[%.2f, %.2f]. " +
                "Check your coordinates or widen the range.",
                xMin, xMax, yMin, yMax));
        }

        return candidates.stream()
            .min(Comparator.comparingDouble(
                v -> dist2D(v, targetX, targetY)))
            .get();
    }

    /**
     * Returns the vertex in the entire mesh nearest to (targetX, targetY),
     * with no bounding box — useful as a fallback when you only have an
     * approximate coordinate and no region in mind.
     *
     * @param mesh    the loaded mesh
     * @param targetX X coordinate of the target point
     * @param targetY Y coordinate of the target point
     * @return the nearest vertex in the mesh
     */
    public static Vertex findNearest(MeshData mesh, double targetX, double targetY) {
        return mesh.getVertices().stream()
            .min(Comparator.comparingDouble(v -> dist2D(v, targetX, targetY)))
            .orElseThrow(() -> new IllegalStateException("Mesh has no vertices."));
    }

    /**
     * Convenience method: finds the nearest vertex to the centroid of a
     * bounding box. Good when you want "roughly the middle of this region"
     * as your start or end point.
     *
     * @param mesh the loaded mesh
     * @param xMin minimum X coordinate=
     * @param xMax maximum X coordinate=
     * @param yMin minimum Y coordinate
     * @param yMax maximum Y coordinate=
     * @return the vertex nearest to the bounding box centre
     */
    public static Vertex findNearestToCentre(
            MeshData mesh,
            double xMin, double xMax,
            double yMin, double yMax) {

        double centreX = (xMin + xMax) / 2.0;
        double centreY = (yMin + yMax) / 2.0;
        return findNearest(mesh, xMin, xMax, yMin, yMax, centreX, centreY);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static double dist2D(Vertex v, double x, double y) {
        double dx = v.x - x;
        double dy = v.y - y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static void validateRange(double min, double max, String axis) {
        if (min > max) {
            throw new IllegalArgumentException(String.format(
                "%s range invalid: min (%.2f) is greater than max (%.2f).",
                axis, min, max));
        }
    }
}