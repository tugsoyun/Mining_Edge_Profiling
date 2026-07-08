package src.terrain;

import src.model.Vertex;
import java.util.List;

/**
 * Lightweight container that holds point cloud data between terrain
 * preprocessing steps within a single session.
 *
 * Each field is null until that step has been completed. Steps check
 * availability and prompt the user to run earlier steps if needed.
 * Passing this object between the static run() methods lets each class
 * act as both a standalone tool and a pipeline stage.
 */
public class SessionState {
    public List<Vertex> rawPoints;
    public List<Vertex> groundPoints;
    public List<Vertex> subsampledPoints;
    public String       lastMeshPath;

    /** Returns a one-line-per-stage summary of what has been completed. */
    public String summary() {
        return String.format(
            "  Raw points:        %s%n" +
            "  Ground points:     %s%n" +
            "  Subsampled points: %s%n" +
            "  Last mesh:         %s",
            rawPoints        == null ? "not loaded"  : rawPoints.size()        + " pts",
            groundPoints     == null ? "not run"     : groundPoints.size()     + " pts",
            subsampledPoints == null ? "not run"     : subsampledPoints.size() + " pts",
            lastMeshPath     == null ? "none"        : lastMeshPath);
    }
}