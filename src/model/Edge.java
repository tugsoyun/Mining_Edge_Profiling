package src.model;

import java.util.EnumMap;
import java.util.Map;

import src.graph.CostMetric;
import src.graph.ForestClassifier;

public class Edge {
    // for graph structure
    public Vertex from, to;

    // terrain information
    public String surfaceType;

    // internal geometry values
    private double horizontalDistance;  // in m

    // expandable edge weight vector
    private Map<CostMetric, Double> metrics = new EnumMap<>(CostMetric.class);

    // 2 weights for now
    double travelTime;  // in s
    double fuelUsed;    // in mL

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Default constructor without terrain or forest data → paved, no coverage. */
    public Edge(Vertex from, Vertex to) {
        this(from, to, "paved", null);
    }

    /** Constructor with terrain type but no forest data. */
    public Edge(Vertex from, Vertex to, String surfaceType) {
        this(from, to, surfaceType, null);
    }

    /**
     * Full constructor with forest classifier.
     *
     * <p>Pass a {@link ForestClassifier} to populate
     * {@link CostMetric#FOREST_COVERAGE} on this edge automatically.
     * Pass {@code null} to skip forest classification (metric defaults to 0.0).</p>
     *
     * @param from             source vertex
     * @param to               destination vertex
     * @param surfaceType      terrain surface string (e.g. "paved", "dirt")
     * @param forestClassifier pre-built classifier, or null
     */
    public Edge(Vertex from, Vertex to, String surfaceType, ForestClassifier forestClassifier) {
        this.from = from;
        this.to = to;
        this.surfaceType = surfaceType;

        computeGeometry();
        Vehicle.calculate(this);

        if (forestClassifier != null) {
            double coverage = forestClassifier.getEdgeCoverage(from, to);
            setMetric(CostMetric.FOREST_COVERAGE, coverage);
        }
    }

    // -------------------------------------------------------------------------
    // Geometry
    // -------------------------------------------------------------------------

    private void computeGeometry() {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;

        horizontalDistance = Math.sqrt(dx*dx + dy*dy);
        double distance3D = Math.sqrt(dx*dx + dy*dy + dz*dz);
        double gradePercent = 0.0;
        if (horizontalDistance > 0) {
            gradePercent = (dz / horizontalDistance) * 100.0;
        }

        setMetric(CostMetric.DISTANCE, distance3D);
        setMetric(CostMetric.GRADE, gradePercent);
    }

    // -------------------------------------------------------------------------
    // Metrics
    // -------------------------------------------------------------------------

    public void setMetric(CostMetric metric, double value) {
        metrics.put(metric, value);
    }

    public double getMetric(CostMetric metric) {
        return metrics.getOrDefault(metric, 0.0);
    }

    public Map<CostMetric, Double> getAllMetrics() {
        return metrics;
    }

    // -------------------------------------------------------------------------
    // Cost helpers
    // -------------------------------------------------------------------------

    public double getCost() {
        return getMetric(CostMetric.TIME);
    }

    public double getCost(CostMetric metric) {
        return getMetric(metric);
    }

    // -------------------------------------------------------------------------
    // Display
    // -------------------------------------------------------------------------

    public String info() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Edge %d -> %d%n", from.id, to.id));
        for (CostMetric metric : CostMetric.values()) {
            if (metrics.containsKey(metric)) {
                sb.append(String.format(
                    "%-18s : %.4f %s%n",
                    metric.getDisplayName(),
                    metrics.get(metric),
                    metric.getUnit()));
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("%d -> %d", from.id, to.id);
    }
}