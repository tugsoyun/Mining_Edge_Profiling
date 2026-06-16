package src.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import src.model.Edge;
import src.model.Vertex;

/**
 * Dijkstra's shortest-path algorithm over a {@link Graph}.
 *
 * <p>Edge weights are supplied by a {@link CostFunction}, so the same
 * implementation handles time, fuel, distance, or any weighted blend
 * without modification.</p>
 *
 * <p><b>Correctness requirement:</b> {@code CostFunction.cost()} must
 * return non-negative values for every edge. Negative weights will
 * produce incorrect results (use Bellman-Ford instead).</p>
 */
public class Dijkstra {

    // -------------------------------------------------------------------------
    // Internal priority-queue node
    // -------------------------------------------------------------------------

    private static class Node implements Comparable<Node> {
        final int vertexId;
        final double cost;

        Node(int vertexId, double cost) {
            this.vertexId = vertexId;
            this.cost = cost;
        }

        @Override
        public int compareTo(Node other) {
            return Double.compare(this.cost, other.cost);
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Finds the least-cost path from {@code start} to {@code goal}.
     *
     * @param graph        the graph to search
     * @param start        id of the source vertex
     * @param goal         id of the destination vertex
     * @param costFunction edge-weight function (must be non-negative)
     * @return ordered list of vertices from start to goal (inclusive),
     *         or an empty list if no path exists
     */
    public static List<Vertex> findPath(
            Graph graph,
            int start,
            int goal,
            CostFunction costFunction) {

        // dist[v] = best known cost to reach v from start
        Map<Integer, Double> dist = new HashMap<>();

        // previous[v] = the vertex we arrived from on the best path to v
        Map<Integer, Integer> previous = new HashMap<>();

        PriorityQueue<Node> pq = new PriorityQueue<>();

        dist.put(start, 0.0);
        pq.add(new Node(start, 0.0));

        while (!pq.isEmpty()) {
            Node current = pq.poll();

            // Skip stale entries — the PQ may hold outdated (higher-cost)
            // copies of vertices that have already been settled.
            if (current.cost > dist.getOrDefault(current.vertexId, Double.MAX_VALUE)) {
                continue;
            }

            // Early exit: once we settle the goal, the shortest path is final.
            if (current.vertexId == goal) {
                break;
            }

            for (Edge edge : graph.getNeighbors(current.vertexId)) {
                double edgeCost = costFunction.cost(edge);

                if (edgeCost < 0) {
                    throw new IllegalStateException(String.format(
                        "Negative edge cost (%.4f) on edge %s. " +
                        "Dijkstra requires non-negative weights.",
                        edgeCost, edge));
                }

                int neighbourId = edge.to.id;
                double newCost = current.cost + edgeCost;

                if (newCost < dist.getOrDefault(neighbourId, Double.MAX_VALUE)) {
                    dist.put(neighbourId, newCost);
                    previous.put(neighbourId, current.vertexId);
                    pq.add(new Node(neighbourId, newCost));
                }
            }
        }

        return reconstructPath(graph, previous, start, goal);
    }

    /**
     * Convenience overload using {@link CostFunction#byTime()} as the default.
     *
     * @param graph the graph to search
     * @param start id of the source vertex
     * @param goal  id of the destination vertex
     * @return ordered list of vertices, or empty if no path exists
     */
    public static List<Vertex> findPath(Graph graph, int start, int goal) {
        return findPath(graph, start, goal, CostFunction.byTime());
    }

    /**
     * Returns the total cost of a path (sum of edge costs along the route).
     *
     * @param graph        the graph
     * @param path         ordered vertex list returned by {@code findPath}
     * @param costFunction the same cost function used to compute the path
     * @return total cost, or 0.0 for paths of length less than 2
     */
    public static double pathCost(
            Graph graph,
            List<Vertex> path,
            CostFunction costFunction) {

        double total = 0.0;
        for (int i = 0; i < path.size() - 1; i++) {
            int from = path.get(i).id;
            int to   = path.get(i + 1).id;

            Edge edge = graph.getNeighbors(from).stream()
                .filter(e -> e.to.id == to)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                    "Path contains non-existent edge: " + from + " -> " + to));

            total += costFunction.cost(edge);
        }
        return total;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Walks the {@code previous} map back from goal to start and reverses
     * the result to get a start-to-goal ordering.
     *
     * @return ordered path as Vertex objects, or empty list if goal was never reached
     */
    private static List<Vertex> reconstructPath(
            Graph graph,
            Map<Integer, Integer> previous,
            int start,
            int goal) {

        if (!previous.containsKey(goal) && start != goal) {
            return Collections.emptyList();
        }

        List<Vertex> path = new ArrayList<>();
        int current = goal;

        while (current != start) {
            path.add(graph.getVertex(current));
            current = previous.get(current);
        }
        path.add(graph.getVertex(start));

        Collections.reverse(path);
        return path;
    }
}