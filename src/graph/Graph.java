package src.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import src.model.*;

/**
 * Converts mesh edges into an adjacency list.
 *
 * Provides neighbor lookup for pathfinding algorithms.
 *
 * Border/outlier edges are filtered out automatically using a
 * statistical length threshold (mean + 2 * stddev). This removes
 * the long diagonal edges at mesh boundaries that would otherwise
 * create illegitimate shortest-path shortcuts.
 */
public class Graph {
    private List<Vertex> vertices;
    private List<Edge> edges;
    private Map<Integer, List<Edge>> adj;

    /** Build graph without forest classification. */
    public Graph(MeshData mesh) {
        this(mesh, null);
    }

    /**
     * Build graph with optional forest classification.
     *
     * @param mesh             the source mesh
     * @param forestClassifier pre-built classifier; pass null to skip
     */
    public Graph(MeshData mesh, ForestClassifier forestClassifier) {
        vertices = mesh.getVertices();
        buildEdges(vertices, mesh.getFaces(), forestClassifier);
        buildGraph(edges, forestClassifier);
    }

    // -------------------------------------------------------------------------
    // Edge building
    // -------------------------------------------------------------------------

    private void buildEdges(
            List<Vertex> vertices,
            List<int[]> faces,
            ForestClassifier forestClassifier) {

        edges = new ArrayList<>();
        Set<String> saved = new HashSet<>();

        for (int[] face : faces) {
            addEdge(face[0], face[1], vertices, saved, forestClassifier);
            addEdge(face[1], face[2], vertices, saved, forestClassifier);
            addEdge(face[2], face[0], vertices, saved, forestClassifier);
        }
    }

    private void addEdge(
            int a, int b,
            List<Vertex> vertices,
            Set<String> saved,
            ForestClassifier forestClassifier) {

        String k1 = a + " & " + b;
        String k2 = b + " & " + a;

        if (saved.contains(k1) || saved.contains(k2)) return;

        saved.add(k1);
        saved.add(k2);

        edges.add(new Edge(vertices.get(a), vertices.get(b), "paved", forestClassifier));
    }

    // -------------------------------------------------------------------------
    // Graph building with outlier edge filtering
    // -------------------------------------------------------------------------

    private void buildGraph(List<Edge> edges, ForestClassifier forestClassifier) {
        adj = new HashMap<>();

        double maxLength = computeLengthThreshold(edges);

        int total    = edges.size();
        int filtered = 0;

        for (Edge edge : edges) {
            double len = edge.getMetric(CostMetric.DISTANCE);

            if (len > maxLength) {
                filtered++;
                continue;  // skip border/outlier edge and its reverse
            }

            adj.computeIfAbsent(edge.from.id, k -> new ArrayList<>()).add(edge);

            Edge reverse = new Edge(edge.to, edge.from, edge.surfaceType, forestClassifier);
            adj.computeIfAbsent(edge.to.id, k -> new ArrayList<>()).add(reverse);
        }

        System.out.printf(
            "Graph: filtered %d / %d edges exceeding length threshold of %.4f m%n",
            filtered, total, maxLength);
    }

    /**
     * Computes a maximum edge length threshold as mean + 2 * stddev.
     *
     * <p>Interior mesh edges cluster tightly around the mean triangle
     * edge length. Border edges are statistical outliers well above this
     * threshold and get rejected.</p>
     *
     * <p>Increase the multiplier (e.g. to 3.0) if valid long edges are
     * being incorrectly filtered; decrease it (e.g. to 1.5) if border
     * edges are still slipping through.</p>
     */
    private double computeLengthThreshold(List<Edge> edges) {
        final double STDDEV_MULTIPLIER = 2.0;

        // Compute mean
        double sum = 0.0;
        for (Edge e : edges) {
            sum += e.getMetric(CostMetric.DISTANCE);
        }
        double mean = sum / edges.size();

        // Compute standard deviation
        double variance = 0.0;
        for (Edge e : edges) {
            double diff = e.getMetric(CostMetric.DISTANCE) - mean;
            variance += diff * diff;
        }
        double stddev = Math.sqrt(variance / edges.size());

        double threshold = mean + STDDEV_MULTIPLIER * stddev;

        System.out.printf(
            "Graph: edge length stats — mean: %.4f m, stddev: %.4f m, threshold: %.4f m%n",
            mean, stddev, threshold);

        return threshold;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public List<Edge> getNeighbors(int vertexId) {
        return adj.getOrDefault(vertexId, Collections.emptyList());
    }

    public Vertex getVertex(int id) {
        return vertices.get(id);
    }
}