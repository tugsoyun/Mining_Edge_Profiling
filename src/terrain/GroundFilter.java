package src.terrain;

import src.model.Vertex;

import java.util.ArrayList;
import java.util.List;

/**
 * Separates ground points from vegetation/objects in a LiDAR point cloud
 * using the Cloth Simulation Filter (CSF) algorithm.
 *
 * <h3>Algorithm overview (Zhang et al., 2016)</h3>
 * <ol>
 *   <li>Invert the point cloud vertically (flip Z so the ground is on top).</li>
 *   <li>Place a regular cloth grid above the inverted cloud.</li>
 *   <li>Let the cloth fall under simulated gravity. Each cloth particle moves
 *       downward each iteration unless it is blocked by a point in the
 *       inverted cloud directly below it (i.e. the ground is now a ceiling
 *       that stops the cloth from falling through).</li>
 *   <li>After the cloth settles, measure how far each original point is from
 *       the cloth surface at that XY position.</li>
 *   <li>Points within {@code classificationThreshold} of the cloth = ground;
 *       points farther away = non-ground (vegetation, buildings, etc.).</li>
 * </ol>
 *
 * <h3>Key parameters</h3>
 * <ul>
 *   <li><b>clothResolution</b> — grid spacing of the cloth (metres). Smaller
 *       values preserve more ground detail but are slower and may follow
 *       vegetation too closely. Typical range: 0.5–5.0 m.</li>
 *   <li><b>maxIterations</b> — simulation steps before the cloth is considered
 *       settled. More iterations = more accurate on rough terrain but slower.
 *       Typical range: 300–1000.</li>
 *   <li><b>classificationThreshold</b> — max distance from cloth (in original,
 *       non-inverted space) for a point to be labelled ground. Typical: 0.5 m
 *       for flat terrain, up to 1.5 m for steep or rough terrain.</li>
 *   <li><b>rigidness</b> — how strongly cloth particles resist bending
 *       relative to their neighbours (1 = flexible, 3 = very rigid). Higher
 *       values produce smoother ground surfaces and are better for steep
 *       slopes.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 *   GroundFilter csf = new GroundFilter.Builder()
 *       .clothResolution(1.0)
 *       .maxIterations(1000)
 *       .classificationThreshold(0.5)
 *       .rigidness(2)
 *       .build();
 *
 *   GroundFilter.Result result = csf.filter(points);
 *   List&lt;Vertex&gt; ground     = result.ground;
 *   List&lt;Vertex&gt; nonGround  = result.nonGround;
 * </pre>
 */
public class GroundFilter {

    // -------------------------------------------------------------------------
    // Parameters
    // -------------------------------------------------------------------------

    private final double clothResolution;
    private final int    maxIterations;
    private final double classificationThreshold;
    private final int    rigidness;

    private GroundFilter(Builder b) {
        this.clothResolution           = b.clothResolution;
        this.maxIterations             = b.maxIterations;
        this.classificationThreshold   = b.classificationThreshold;
        this.rigidness                 = b.rigidness;
    }

    // -------------------------------------------------------------------------
    // Result container
    // -------------------------------------------------------------------------

    /** Holds the two output point sets from a filter run. */
    public static class Result {
        /** Points classified as ground. */
        public final List<Vertex> ground;
        /** Points classified as non-ground (vegetation, buildings, etc.). */
        public final List<Vertex> nonGround;

        Result(List<Vertex> ground, List<Vertex> nonGround) {
            this.ground    = ground;
            this.nonGround = nonGround;
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Runs the CSF algorithm on the supplied point cloud.
     *
     * @param points raw LiDAR/point-cloud vertices (unclassified)
     * @return a {@link Result} containing ground and non-ground lists
     */
    public Result filter(List<Vertex> points) {
        if (points.isEmpty()) {
            return new Result(new ArrayList<>(), new ArrayList<>());
        }

        System.out.printf(
            "GroundFilter (CSF): %d points, cloth res=%.2f m, " +
            "maxIter=%d, threshold=%.2f m, rigidness=%d%n",
            points.size(), clothResolution, maxIterations,
            classificationThreshold, rigidness);

        // ── Step 1: find bounding box and invert Z ───────────────────────
        double[] bounds = boundingBox(points);
        double xMin = bounds[0], xMax = bounds[1];
        double yMin = bounds[2], yMax = bounds[3];
        double zMin = bounds[4], zMax = bounds[5];   // used for Z inversion

        // ── Step 2: build cloth grid ─────────────────────────────────────
        int cols = (int) Math.ceil((xMax - xMin) / clothResolution) + 1;
        int rows = (int) Math.ceil((yMax - yMin) / clothResolution) + 1;

        // cloth[r][c] = current height of each cloth particle, expressed
        // in the SAME inverted/relative frame as `ceiling` below (0 = the
        // highest point in the cloud, zMax - zMin = the lowest point) —
        // NOT absolute elevation. Starting the cloth at an absolute
        // elevation (the previous bug: startZ = zMax + 1.0) meant that on
        // real data with any non-trivial elevation offset, the cloth had
        // to fall the *entire absolute elevation* before reaching any
        // ceiling constraint — often thousands of units — which could
        // take far more than maxIterations to settle. The cloth never
        // reached the ground, so nearly every point was misclassified.
        double reliefRange = zMax - zMin;
        double startZ = reliefRange + 1.0;
        double[][] cloth    = new double[rows][cols];
        boolean[][] pinned  = new boolean[rows][cols]; // true = cloth is resting on a point

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                cloth[r][c] = startZ;
            }
        }

        // ── Step 3: build per-cell inverted-Z ceiling ────────────────────
        // For each cloth cell, find the highest Z of any point in that cell
        // in the INVERTED cloud (which is the lowest point in the original).
        // We store it as the "floor" the cloth cannot fall through.
        double[][] ceiling = new double[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                ceiling[r][c] = Double.NEGATIVE_INFINITY; // no point found yet
            }
        }

        for (Vertex v : points) {
            int c = (int) Math.floor((v.x - xMin) / clothResolution);
            int r = (int) Math.floor((v.y - yMin) / clothResolution);
            c = Math.min(c, cols - 1);
            r = Math.min(r, rows - 1);

            // Inverted Z: the highest inverted point = lowest original point
            // = the ground. In the inverted space the ground is a ceiling.
            double invertedZ = zMax - v.z;  // invert
            if (invertedZ > ceiling[r][c]) {
                ceiling[r][c] = invertedZ;
            }
        }

        // Cells with no points: fill with a very low ceiling (no constraint)
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (ceiling[r][c] == Double.NEGATIVE_INFINITY) {
                    ceiling[r][c] = 0.0; // will not constrain the cloth
                }
            }
        }

        // ── Step 4: simulate cloth falling ──────────────────────────────
        double timeStep  = 0.65;
        double gravity   = 0.3;

        for (int iter = 0; iter < maxIterations; iter++) {
            double[][] next = new double[rows][cols];

            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {

                    if (pinned[r][c]) {
                        next[r][c] = cloth[r][c];
                        continue;
                    }

                    // Gravity pulls the cloth down (decreasing Z in inverted space)
                    double newZ = cloth[r][c] - gravity * timeStep;

                    // Rigidness: pull toward the average of neighbours
                    if (rigidness > 1) {
                        double neighbourSum   = 0;
                        int    neighbourCount = 0;
                        int[][] offsets = {{-1,0},{1,0},{0,-1},{0,1}};
                        for (int[] off : offsets) {
                            int nr = r + off[0];
                            int nc = c + off[1];
                            if (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                                neighbourSum += cloth[nr][nc];
                                neighbourCount++;
                            }
                        }
                        if (neighbourCount > 0) {
                            double avgNeighbour = neighbourSum / neighbourCount;
                            // Weight toward neighbour average — higher rigidness =
                            // stronger pull, smoother cloth
                            double rigidWeight = (rigidness - 1) * 0.3;
                            newZ = newZ * (1.0 - rigidWeight) + avgNeighbour * rigidWeight;
                        }
                    }

                    // Collision: cloth cannot go below the ceiling (inverted ground)
                    if (newZ <= ceiling[r][c]) {
                        newZ = ceiling[r][c];
                        pinned[r][c] = true;
                    }

                    next[r][c] = newZ;
                }
            }

            // Check convergence: if cloth moved very little, stop early
            double maxMove = 0;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    maxMove = Math.max(maxMove, Math.abs(next[r][c] - cloth[r][c]));
                }
            }

            cloth = next;
            if (maxMove < 0.001) {
                System.out.printf("  CSF converged early at iteration %d (maxMove=%.5f)%n",
                        iter + 1, maxMove);
                break;
            }
        }

        // ── Step 5: classify points ──────────────────────────────────────
        // For each original point, interpolate the cloth height at its XY
        // position and compare the original Z to the un-inverted cloth Z.
        List<Vertex> ground    = new ArrayList<>();
        List<Vertex> nonGround = new ArrayList<>();

        for (Vertex v : points) {
            int c = (int) Math.floor((v.x - xMin) / clothResolution);
            int r = (int) Math.floor((v.y - yMin) / clothResolution);
            c = Math.min(c, cols - 1);
            r = Math.min(r, rows - 1);

            // Un-invert: cloth stores inverted Z, convert back to original space
            double clothZ = zMax - cloth[r][c];

            double distFromGround = v.z - clothZ;   // positive = above cloth = veg

            if (distFromGround <= classificationThreshold) {
                ground.add(v);
            } else {
                nonGround.add(v);
            }
        }

        System.out.printf(
            "  CSF result: %d ground points, %d non-ground points%n",
            ground.size(), nonGround.size());

        return new Result(ground, nonGround);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static class Builder {
        private double clothResolution         = 1.0;
        private int    maxIterations           = 1000;
        private double classificationThreshold = 0.5;
        private int    rigidness               = 2;

        /** Cloth grid spacing in metres. Smaller = more detail, slower. Default 1.0. */
        public Builder clothResolution(double v)         { clothResolution         = v; return this; }
        /** Number of simulation steps. Default 1000. */
        public Builder maxIterations(int v)              { maxIterations           = v; return this; }
        /** Max distance from cloth to be labelled ground (metres). Default 0.5. */
        public Builder classificationThreshold(double v) { classificationThreshold = v; return this; }
        /** Cloth stiffness 1–3. Higher = smoother result, better for steep slopes. Default 2. */
        public Builder rigidness(int v)                  { rigidness               = v; return this; }

        public GroundFilter build() { return new GroundFilter(this); }
    }
    // =========================================================================
    // Interactive entry point
    // =========================================================================

    /**
     * Interactive CLI entry point. Prompts for CSF parameters, runs the
     * filter on {@link SessionState#rawPoints}, and stores results in
     * {@link SessionState#groundPoints}.
     *
     * <p>Loops on failure (no ground points found) with parameter suggestions
     * rather than aborting. Offers to save the ground and vegetation clouds.</p>
     */
    public static void run(java.util.Scanner sc, SessionState state) {
        final String PATHS_DIR = "data/paths/";
        new java.io.File(PATHS_DIR).mkdirs();

        if (state.rawPoints == null) {
            System.out.println("  No point cloud loaded. Run 'Load point cloud' first.");
            return;
        }

        java.util.List<src.model.Vertex> source = state.rawPoints;
        System.out.printf("  Input: %d points.%n", source.size());

        while (true) {
            System.out.println("\n─────────────────────────────────────");
            System.out.println(" GROUND FILTER (CSF)");
            System.out.println("─────────────────────────────────────");
            System.out.println("Press Enter to accept defaults.");

            double res    = promptDefault(sc, "  Cloth resolution (m)            [default 1.0 ]: ", 1.0);
            double thresh = promptDefault(sc, "  Classification threshold (m)    [default 0.5 ]: ", 0.5);
            double rigD   = promptDefault(sc, "  Rigidness 1-3 (3=steep terrain) [default 2   ]: ", 2.0);
            int rigidness = Math.max(1, Math.min(3, (int) Math.round(rigD)));

            GroundFilter csf = new GroundFilter.Builder()
                    .clothResolution(res)
                    .classificationThreshold(thresh)
                    .rigidness(rigidness)
                    .build();

            Result result = csf.filter(source);
            System.out.printf("  Ground: %d pts  |  Non-ground: %d pts%n",
                    result.ground.size(), result.nonGround.size());

            if (result.ground.isEmpty()) {
                System.out.println("  No ground points found. Suggestions:");
                System.out.println("    - Increase threshold to 1.0-2.0");
                System.out.println("    - Increase cloth resolution to 2.0-5.0");
                System.out.println("    - Set rigidness to 3 for steep terrain");
                System.out.print("  [r] Retry  [b] Back: ");
                String c = sc.nextLine().trim().toLowerCase();
                if (c.equals("b") || c.equals("back")) return;
                continue;
            }

            state.groundPoints = result.ground;

            // Offer to save vegetation cloud for reference in CloudCompare
            System.out.print("  Export vegetation (non-ground) as PLY? [y/n]: ");
            if (sc.nextLine().trim().equalsIgnoreCase("y")) {
                String vegPath = PATHS_DIR + "vegetation_" + System.currentTimeMillis() + ".ply";
                try {
                    src.io.PathExporter.exportPathPLY(result.nonGround, vegPath);
                    System.out.printf("  Saved vegetation to %s%n", vegPath);
                } catch (java.io.IOException e) {
                    System.out.printf("  Save failed: %s%n", e.getMessage());
                }
            }

            // Offer to save ground points as XYZ
            System.out.print("  Save ground points as XYZ for reuse? [y/n]: ");
            if (sc.nextLine().trim().equalsIgnoreCase("y")) {
                String dir = "data/las/";
                String def = "ground_" + System.currentTimeMillis() + ".xyz";
                System.out.printf("  Filename [default: %s]: ", def);
                String name = sc.nextLine().trim();
                if (name.isEmpty()) name = def;
                if (!name.toLowerCase().endsWith(".xyz")) name += ".xyz";
                try (java.io.PrintWriter pw = new java.io.PrintWriter(dir + name)) {
                    for (src.model.Vertex v : result.ground)
                        pw.printf("%.6f %.6f %.6f%n", v.x, v.y, v.z);
                    System.out.printf("  Saved to %s%n", new java.io.File(dir + name).getAbsolutePath());
                } catch (java.io.IOException e) {
                    System.out.printf("  Save failed: %s%n", e.getMessage());
                }
            }
            return;
        }
    }

    private static double promptDefault(java.util.Scanner sc, String prompt, double def) {
        System.out.print(prompt);
        String input = sc.nextLine().trim();
        if (input.isEmpty()) return def;
        try { return Double.parseDouble(input); }
        catch (NumberFormatException e) {
            System.out.printf("  Invalid — using default (%.2f).%n", def);
            return def;
        }
    }


}