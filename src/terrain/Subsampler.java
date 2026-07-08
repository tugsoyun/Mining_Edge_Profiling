package src.terrain;

import src.model.Vertex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Reduces a point cloud to a manageable size before meshing.
 *
 * <p>Uses voxel-grid subsampling: the bounding box of the cloud is
 * divided into a 3-D grid of cells of a chosen side length, and one
 * point is kept per occupied cell (the point closest to the cell
 * centroid). This preserves the spatial distribution of the cloud
 * while guaranteeing a minimum distance between any two output points
 * equal to the cell size, which directly controls mesh triangle size
 * downstream.</p>
 *
 * <p>Two modes are provided:
 * <ul>
 *   <li><b>By spacing</b> — caller specifies the desired minimum
 *       distance between output points directly.</li>
 *   <li><b>By count</b> — caller specifies an approximate target
 *       output count; the cell size is derived automatically so the
 *       expected number of occupied cells matches the target.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 *   // Keep one point every 2 metres
 *   List&lt;Vertex&gt; sparse = Subsampler.bySpacing(dense, 2.0);
 *
 *   // Keep approximately 5000 points
 *   List&lt;Vertex&gt; sparse = Subsampler.byCount(dense, 5000);
 * </pre>
 * </p>
 *
 * <p><b>Note on "approximate" count:</b> the output count is not
 * guaranteed to equal the target exactly. Each non-empty voxel
 * contributes one point, and the number of non-empty voxels depends
 * on the spatial distribution of the input cloud. Sparse or
 * unevenly-distributed clouds may produce fewer points than requested;
 * very dense uniform clouds will be close to exact.</p>
 */
public class Subsampler {

    private Subsampler() { /* utility class */ }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Subsample by specifying the minimum distance between output points.
     *
     * @param points  input point cloud (vertices from the LAS/PLY parser)
     * @param spacing desired minimum separation between kept points (metres)
     * @return subsampled point list; original list is unchanged
     * @throws IllegalArgumentException if spacing is not positive
     */
    public static List<Vertex> bySpacing(List<Vertex> points, double spacing) {
        if (spacing <= 0) {
            throw new IllegalArgumentException(
                "Spacing must be positive (got " + spacing + ").");
        }
        if (points.isEmpty()) return new ArrayList<>();

        System.out.printf("Subsampler: %d input points, target spacing %.3f m...%n",
                points.size(), spacing);

        List<Vertex> result = voxelGrid(points, spacing);

        System.out.printf("Subsampler: kept %d points (%.1f%% of input)%n",
                result.size(), 100.0 * result.size() / points.size());
        return result;
    }

    /**
     * Subsample to an approximate target point count.
     *
     * <p>Cell size is derived from the XY footprint of the cloud divided
     * by the target count, then square-rooted. Using XY area (not 3D
     * volume) is correct for terrain clouds where the Z range (elevation
     * relief) is far smaller than the XY extent — a volume-based estimate
     * would otherwise produce a cell size much too large and massively
     * under-sample the cloud.</p>
     *
     * <p>After the initial estimate, up to {@code MAX_REFINE} binary-search
     * refinement passes are run to bring the actual output count within
     * {@code TOLERANCE} of the target before returning.</p>
     *
     * @param points      input point cloud
     * @param targetCount desired approximate number of output points
     * @return subsampled point list
     * @throws IllegalArgumentException if targetCount is less than 1
     */
    public static List<Vertex> byCount(List<Vertex> points, int targetCount) {
        if (targetCount < 1) {
            throw new IllegalArgumentException(
                "Target count must be at least 1 (got " + targetCount + ").");
        }
        if (points.isEmpty()) return new ArrayList<>();
        if (targetCount >= points.size()) {
            System.out.println("Subsampler: target count >= input size, returning full cloud.");
            return new ArrayList<>(points);
        }

        // ── Initial cell-size estimate using XY area (not 3D volume) ─────
        // Terrain clouds have a large XY footprint but small Z range.
        // Area / targetCount gives area-per-cell; sqrt gives side length.
        double[] bounds = boundingBox(points);
        double dx = bounds[1] - bounds[0];
        double dy = bounds[3] - bounds[2];
        double xyArea = Math.max(dx * dy, 1e-6);

        double cellSize = Math.sqrt(xyArea / targetCount);
        cellSize = Math.max(cellSize, 1e-6);

        System.out.printf(
            "Subsampler: %d input points, target ~%d, initial spacing %.4f m%n",
            points.size(), targetCount, cellSize);

        // ── Binary-search refinement ──────────────────────────────────────
        // The voxel grid is not perfectly predictable (depends on point
        // distribution), so we iterate to get within TOLERANCE of target.
        final int    MAX_REFINE = 8;
        final double TOLERANCE  = 0.10; // accept within ±10% of target

        List<Vertex> result = voxelGrid(points, cellSize);
        double lo = 0, hi = Math.max(dx, dy);

        for (int i = 0; i < MAX_REFINE; i++) {
            int got = result.size();
            double ratio = (double) got / targetCount;

            if (Math.abs(ratio - 1.0) <= TOLERANCE) break; // close enough

            if (got > targetCount) {
                // Too many points → increase cell size
                lo = cellSize;
                cellSize = (hi == Math.max(dx, dy)) ? cellSize * 1.5 : (lo + hi) / 2.0;
            } else {
                // Too few points → decrease cell size
                hi = cellSize;
                cellSize = (lo + hi) / 2.0;
            }
            cellSize = Math.max(cellSize, 1e-6);
            result = voxelGrid(points, cellSize);
            System.out.printf("  Refine pass %d: spacing %.4f m → %d points%n",
                    i + 1, cellSize, result.size());
        }

        System.out.printf("Subsampler: kept %d points (%.1f%% of input, target was %d)%n",
                result.size(), 100.0 * result.size() / points.size(), targetCount);
        return result;
    }

    // -------------------------------------------------------------------------
    // Core voxel-grid algorithm
    // -------------------------------------------------------------------------

    /**
     * Assigns every point to a voxel cell of the given size, then keeps
     * the point in each cell that is closest to the cell centroid.
     *
     * <p>Cell key = (ix, iy, iz) where {@code ix = floor((x - xMin) / cellSize)},
     * and similarly for y and z. A {@code String} key is used for simplicity;
     * for very large clouds a {@code long} packed key would be faster.</p>
     */
    private static List<Vertex> voxelGrid(List<Vertex> points, double cellSize) {
        double[] bounds = boundingBox(points);
        double xMin = bounds[0];
        double yMin = bounds[2];
        double zMin = bounds[4];

        // Use a packed long key instead of String to avoid allocating millions
        // of String objects. ix/iy/iz are offset by 100_000 to handle negative
        // coordinates; values above 1M cells per axis are extremely unlikely
        // for any real terrain survey.
        final int OFFSET = 100_000;
        final long IY_MUL = 200_001L;
        final long IX_MUL = 200_001L * 200_001L;

        Map<Long, Vertex> bestPoint = new HashMap<>();
        Map<Long, Double> bestDist  = new HashMap<>();

        for (Vertex v : points) {
            int ix = (int) Math.floor((v.x - xMin) / cellSize) + OFFSET;
            int iy = (int) Math.floor((v.y - yMin) / cellSize) + OFFSET;
            int iz = (int) Math.floor((v.z - zMin) / cellSize) + OFFSET;

            long key = (long) ix * IX_MUL + (long) iy * IY_MUL + iz;

            double cx = xMin + (ix - OFFSET + 0.5) * cellSize;
            double cy = yMin + (iy - OFFSET + 0.5) * cellSize;
            double cz = zMin + (iz - OFFSET + 0.5) * cellSize;

            double dx = v.x - cx, dy = v.y - cy, dz = v.z - cz;
            double d2 = dx*dx + dy*dy + dz*dz;

            if (!bestPoint.containsKey(key) || d2 < bestDist.get(key)) {
                bestPoint.put(key, v);
                bestDist.put(key, d2);
            }
        }

        List<Vertex> result = new ArrayList<>(bestPoint.size());
        int newId = 0;
        for (Vertex v : bestPoint.values()) {
            result.add(new Vertex(newId++, v.x, v.y, v.z));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns [xMin, xMax, yMin, yMax, zMin, zMax] for the given points.
     */
    private static double[] boundingBox(List<Vertex> points) {
        double xMin = Double.MAX_VALUE, xMax = -Double.MAX_VALUE;
        double yMin = Double.MAX_VALUE, yMax = -Double.MAX_VALUE;
        double zMin = Double.MAX_VALUE, zMax = -Double.MAX_VALUE;

        for (Vertex v : points) {
            if (v.x < xMin) xMin = v.x;  if (v.x > xMax) xMax = v.x;
            if (v.y < yMin) yMin = v.y;  if (v.y > yMax) yMax = v.y;
            if (v.z < zMin) zMin = v.z;  if (v.z > zMax) zMax = v.z;
        }
        return new double[]{ xMin, xMax, yMin, yMax, zMin, zMax };
    }
    // =========================================================================
    // Interactive entry point
    // =========================================================================

    /**
     * Interactive CLI entry point. Prompts for subsampling mode and parameters,
     * runs the chosen method on ground points (preferred) or raw points if
     * ground filtering has not been run yet, and stores results in
     * {@link SessionState#subsampledPoints}.
     */
    public static void run(java.util.Scanner sc, SessionState state) {
        // Prefer ground-filtered points; fall back to raw if CSF hasn't run
        java.util.List<Vertex> source;
        if (state.groundPoints != null) {
            source = state.groundPoints;
            System.out.printf("  Using ground-filtered points (%d pts).%n", source.size());
        } else if (state.rawPoints != null) {
            System.out.println("  Ground filter has not been run — using raw points.");
            System.out.println("  (Consider running CSF first for better mesh quality.)");
            source = state.rawPoints;
        } else {
            System.out.println("  No point cloud loaded. Run 'Load point cloud' first.");
            return;
        }

        while (true) {
            System.out.println("\n─────────────────────────────────────");
            System.out.println(" SUBSAMPLE");
            System.out.println("─────────────────────────────────────");
            System.out.printf("  Input: %d points.%n", source.size());
            System.out.println("  1) By target count   (approx output points)");
            System.out.println("  2) By spacing        (min distance between points, metres)");
            System.out.println("  3) Skip              (use points as-is)");
            System.out.println("  b) Back");
            System.out.print("Choice: ");
            String choice = sc.nextLine().trim().toLowerCase();

            java.util.List<Vertex> result = null;
            switch (choice) {
                case "1": {
                    System.out.print("  Target point count: ");
                    String input = sc.nextLine().trim();
                    try {
                        int target = Integer.parseInt(input);
                        result = byCount(source, target);
                    } catch (NumberFormatException e) {
                        System.out.println("  Enter a whole number.");
                    }
                    break;
                }
                case "2": {
                    System.out.print("  Spacing (metres): ");
                    String input = sc.nextLine().trim();
                    try {
                        double spacing = Double.parseDouble(input);
                        result = bySpacing(source, spacing);
                    } catch (NumberFormatException e) {
                        System.out.println("  Enter a number (e.g. 1.5).");
                    }
                    break;
                }
                case "3":
                    System.out.println("  Skipping subsampling.");
                    state.subsampledPoints = new java.util.ArrayList<>(source);
                    return;
                case "b": case "back":
                    return;
                default:
                    System.out.println("  Enter 1, 2, 3, or b.");
            }

            if (result != null) {
                state.subsampledPoints = result;

                // Offer to save
                System.out.print("  Save subsampled points as XYZ for reuse? [y/n]: ");
                if (sc.nextLine().trim().equalsIgnoreCase("y")) {
                    String dir = "data/las/";
                    String def = "subsampled_" + System.currentTimeMillis() + ".xyz";
                    System.out.printf("  Filename [default: %s]: ", def);
                    String name = sc.nextLine().trim();
                    if (name.isEmpty()) name = def;
                    if (!name.toLowerCase().endsWith(".xyz")) name += ".xyz";
                    try (java.io.PrintWriter pw = new java.io.PrintWriter(dir + name)) {
                        for (Vertex v : result) pw.printf("%.6f %.6f %.6f%n", v.x, v.y, v.z);
                        System.out.printf("  Saved to %s%n", new java.io.File(dir + name).getAbsolutePath());
                    } catch (java.io.IOException e) {
                        System.out.printf("  Save failed: %s%n", e.getMessage());
                    }
                }
                return;
            }
        }
    }


}