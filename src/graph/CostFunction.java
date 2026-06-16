package src.graph;

import src.model.Edge;

/**
 * Functional interface for computing the traversal cost of an edge.
 *
 * <p>Implement directly for custom cost logic, or use one of the
 * named static factories below for the standard metrics.</p>
 *
 * <p>Usage examples:
 * <pre>
 *   // Single metric
 *   CostFunction byFuel = CostFunction.byFuel();
 *
 *   // Weighted blend
 *   CostFunction balanced = CostFunction.weighted(0.5, 0.5);
 *
 *   // Custom lambda
 *   CostFunction custom = edge -> edge.getMetric(CostMetric.DISTANCE) * myFactor;
 * </pre>
 * </p>
 */
@FunctionalInterface
public interface CostFunction {

    /**
     * Returns the cost of traversing the given edge.
     * Must return a non-negative value; Dijkstra's algorithm requires this.
     *
     * @param edge the edge being evaluated
     * @return traversal cost (non-negative)
     */
    double cost(Edge edge);


    // -------------------------------------------------------------------------
    // Named factories for standard metrics
    // -------------------------------------------------------------------------

    /** Minimise total travel time (seconds). */
    static CostFunction byTime() {
        return edge -> edge.getMetric(CostMetric.TIME);
    }

    /** Minimise total fuel consumption (mL). */
    static CostFunction byFuel() {
        return edge -> edge.getMetric(CostMetric.FUEL);
    }

    /** Minimise total 3-D distance (metres). */
    static CostFunction byDistance() {
        return edge -> edge.getMetric(CostMetric.DISTANCE);
    }

    /**
     * Minimise a weighted blend of time and fuel.
     *
     * <p>Both weights should be in [0, 1] and ideally sum to 1.0,
     * but any non-negative values work — the result is still admissible
     * for Dijkstra provided all edge costs remain non-negative.</p>
     *
     * @param timeWeight relative importance of travel time
     * @param fuelWeight relative importance of fuel used
     */
    static CostFunction weighted(double timeWeight, double fuelWeight) {
        if (timeWeight < 0 || fuelWeight < 0) {
            throw new IllegalArgumentException(
                "Weights must be non-negative for Dijkstra to be correct.");
        }
        return edge ->
            timeWeight * edge.getMetric(CostMetric.TIME)
          + fuelWeight * edge.getMetric(CostMetric.FUEL);
    }

    /**
     * Penalises steep grades on top of a base metric.
     *
     * <p>Useful when you want shortest-time or least-fuel routes that
     * also avoid severe inclines (e.g. for vehicle stability or safety).</p>
     *
     * @param base          the primary cost function
     * @param gradePenalty  extra cost multiplied by |grade %| per edge
     */
    static CostFunction withGradePenalty(CostFunction base, double gradePenalty) {
        if (gradePenalty < 0) {
            throw new IllegalArgumentException(
                "Grade penalty must be non-negative.");
        }
        return edge ->
            base.cost(edge)
          + gradePenalty * Math.abs(edge.getMetric(CostMetric.GRADE));
    }
}