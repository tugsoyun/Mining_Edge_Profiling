package src.model;

import java.util.EnumMap;
import java.util.Map;

import src.graph.CostMetric;

public class Edge {
    // for graph structure
    public Vertex from, to;

    // terrain information
    public String surfaceType;

    // internal geometry values
    private double horizontalDistance;  // in m

    // expandable edge weight vector
    private Map<CostMetric, Double> metrics =
        new EnumMap<>(CostMetric.class);

    // 2 weights for now
    double travelTime;  // in s
    double fuelUsed;    // in mL

    // default constructor w/o specified terrain -> paved
    public Edge(Vertex from, Vertex to) {
        this(from, to, "paved");
    }

    public Edge(Vertex from, Vertex to, String surfaceType) {
        this.from = from;
        this.to = to;
        this.surfaceType = surfaceType;

        computeGeometry();

        Vehicle.calculate(this);
    }

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

    // METRICS METHODS
    public void setMetric(CostMetric metric, double value) {
        metrics.put(metric, value);
    }

    public double getMetric(CostMetric metric) {
        return metrics.getOrDefault(metric, 0.0);
    }

    public Map<CostMetric, Double> getAllMetrics() {
        return metrics;
    }

    // DISPLAY METHODS
    public String info() { 
        StringBuilder sb = new StringBuilder(); 
        
        sb.append(
            String.format( 
                "Edge %d -> %d%n", 
                from.id, 
                to.id)); 
        
        for (CostMetric metric : CostMetric.values()) { 
            if (metrics.containsKey(metric)) { 
                sb.append( 
                    String.format( 
                        "%-10s : %.4f %s%n", 
                        metric.getDisplayName(), 
                        metrics.get(metric),
                        metric.getUnit())); 
            } 
        } 
        
        return sb.toString(); 
    }

    @Override
    public String toString() {
        return String.format(
            "%d -> %d",
            from.id,
            to.id
        );
    }
}