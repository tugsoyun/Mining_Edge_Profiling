package src.graph;

/**
 * Priority queue node used by Dijkstra.
 *
 * Stores:
 * - vertex ID
 * - current path cost
 */

class Node implements Comparable<Node> {
    int vertexId;
    double cost;

    public Node(int vertexId, double cost) {
        this.vertexId = vertexId;
        this.cost = cost;
    }

    @Override
    public int compareTo(Node other) {
        return Double.compare(this.cost, other.cost);
    }
}