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
     * Build graph with forest classification.
     *
     * @param mesh             the source mesh
     * @param forestClassifier pre-built classifier; pass null to skip
     */
    public Graph(MeshData mesh, ForestClassifier forestClassifier) {
        vertices = mesh.getVertices();
        buildEdges(vertices, mesh.getFaces(), forestClassifier);
        buildGraph(edges, forestClassifier);
    }

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

        Edge edge = new Edge(vertices.get(a), vertices.get(b), "paved", forestClassifier);
        edges.add(edge);
    }

    private void buildGraph(List<Edge> edges, ForestClassifier forestClassifier) {
        adj = new HashMap<>();

        for (Edge edge : edges) {
            adj.computeIfAbsent(edge.from.id, k -> new ArrayList<>()).add(edge);

            // Mesh edges are undirected — create reverse edge.
            Edge reverse = new Edge(edge.to, edge.from, edge.surfaceType, forestClassifier);
            adj.computeIfAbsent(edge.to.id, k -> new ArrayList<>()).add(reverse);
        }
    }

    public List<Edge> getNeighbors(int vertexId) {
        return adj.getOrDefault(vertexId, Collections.emptyList());
    }

    public Vertex getVertex(int id) {
        return vertices.get(id);
    }
}