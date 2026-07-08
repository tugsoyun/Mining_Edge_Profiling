package src;

import src.terrain.GroundFilter;
import src.terrain.MeshGenerator;
import src.terrain.PointCloudLoader;
import src.terrain.SessionState;
import src.terrain.Subsampler;
import src.graph.CostFunction;
import src.graph.CostMetric;
import src.graph.Dijkstra;
import src.graph.ForestClassifier;
import src.graph.Graph;
import src.graph.VertexSelector;
import src.io.MapExporter;
import src.io.PathExporter;
import src.io.PLYParser;
import src.io.ReportGenerator;
import src.model.MeshData;
import src.model.Vertex;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Interactive CLI for the Mine Profile Edge pathfinding tool.
 *
 * Flow:
 *   1. Prompt for a PLY mesh filename (resolved under data/meshes/).
 *   2. Parse the file and build the graph; optionally export the map.
 *   3. Select start and end vertices (by ID, approximate XY, or XY range).
 *   4. Choose a cost metric (or weighted combination) and run Dijkstra.
 *   5. Optionally export the path as a PLY file.
 *   6. Repeat steps 4-5 until the user quits.
 *   7. If 2+ paths were generated, optionally write a comparison report.
 */
public class Main {

    private static final String MESH_DIR    = "data/meshes/";
    private static final String MAPS_DIR    = "data/maps/";
    private static final String PATHS_DIR   = "data/paths/";
    private static final String REPORTS_DIR = "data/reports/";

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        printBanner();

        // ── 1. Source selection: LAS pipeline or existing PLY ─────────────
        MeshData mesh = selectMeshSource(sc);
        if (mesh == null) { sc.close(); return; }

        // ── 2. Build graph ────────────────────────────────────────────────
        System.out.println("\nBuilding graph...");
        Graph graph = new Graph(mesh);
        System.out.printf("  Graph ready — %d vertices loaded.%n",
                mesh.getVertices().size());

        // ── 2b. Optional map export ───────────────────────────────────────
        offerMapExport(sc, graph, mesh);

        // ── 3. Select start / end vertices ───────────────────────────────
        System.out.println("\n─────────────────────────────────────");
        System.out.println(" VERTEX SELECTION");
        System.out.println("─────────────────────────────────────");

        Vertex start = selectVertex(sc, mesh, "START");
        if (start == null) { sc.close(); return; }

        Vertex end = selectVertex(sc, mesh, "END");
        if (end == null) { sc.close(); return; }

        System.out.printf("%nStart → vertex %d  (%.4f, %.4f, %.4f)%n",
                start.id, start.x, start.y, start.z);
        System.out.printf("End   → vertex %d  (%.4f, %.4f, %.4f)%n",
                end.id, end.x, end.y, end.z);

        // ── 4. Path-finding loop ──────────────────────────────────────────
        ReportGenerator report = new ReportGenerator(graph);
        int runNumber = 1;
        boolean anyPathFound = false;

        while (true) {
            System.out.println("\n─────────────────────────────────────");
            System.out.printf(" PATH RUN #%d%n", runNumber);
            System.out.println("─────────────────────────────────────");

            // Choose cost function
            String[] nameHolder = new String[1]; // lets chooseCostFunction pass back a label
            CostFunction costFn = chooseCostFunction(sc, nameHolder);
            if (costFn == null) break;            // user quit
            String pathName = nameHolder[0];

            // Run Dijkstra
            System.out.println("\nRunning Dijkstra...");
            List<Vertex> path = Dijkstra.findPath(graph, start.id, end.id, costFn);

            if (path.isEmpty()) {
                System.out.println("  ✗ No path found between those vertices.");
                System.out.println("    The graph may be disconnected in this region.");
            } else {
                double cost = Dijkstra.pathCost(graph, path, costFn);
                System.out.printf("  ✓ Path found — %d vertices, total cost %.4f%n",
                        path.size(), cost);

                report.addPath(pathName, path, costFn);
                anyPathFound = true;
                runNumber++;

                // Optional PLY export
                offerExport(sc, path, pathName);
            }

            // Continue?
            System.out.print("\nRun another path with a different weight? [y/n]: ");
            String cont = sc.nextLine().trim().toLowerCase();
            if (!cont.equals("y") && !cont.equals("yes")) break;
        }

        // ── 5. Optional report ────────────────────────────────────────────
        if (anyPathFound && runNumber > 2) { // runNumber increments per found path, starts at 1
            offerReport(sc, report);
        } else if (anyPathFound) {
            // Only one path — still offer report (useful single-path summary)
            System.out.print("\nGenerate a summary report for the path? [y/n]: ");
            String ans = sc.nextLine().trim().toLowerCase();
            if (ans.equals("y") || ans.equals("yes")) {
                offerReport(sc, report);
            }
        }

        System.out.println("\nSession complete. Goodbye.");
        sc.close();
    }

    // =========================================================================
    // Phase 1 — Mesh source selection
    // =========================================================================

    /**
     * Asks the user whether to run the full LAS → subsample → ground filter
     * → mesh pipeline, or to load an existing PLY mesh directly.
     */
    private static MeshData selectMeshSource(Scanner sc) {
        System.out.println("\n─────────────────────────────────────");
        System.out.println(" MESH SOURCE");
        System.out.println("─────────────────────────────────────");
        System.out.println("  1) Process a LAS/LAZ file into a new mesh");
        System.out.println("  2) Load an existing PLY mesh");
        System.out.println("  q) Quit");
        System.out.print("Choice: ");
        String choice = sc.nextLine().trim();

        switch (choice.toLowerCase()) {
            case "1": return runLASPipeline(sc);
            case "2": return loadMesh(sc);
            case "q": case "quit": case "exit": return null;
            default:
                System.out.println("  Please enter 1, 2, or q.");
                return selectMeshSource(sc);
        }
    }

    // ── LAS pipeline: terrain preprocessing menu ────────────────────────────────

    /**
     * Non-linear terrain preprocessing menu.
     *
     * Each step is independent — the user can jump to any step, re-run
     * individual stages with different parameters, or skip stages entirely.
     * Results are held in a {@link SessionState} and passed between steps.
     * The menu loops until the user chooses to proceed to pathfinding or quit.
     */
    private static MeshData runLASPipeline(Scanner sc) {
        SessionState state = new SessionState();

        while (true) {
            System.out.println("\n═════════════════════════════════════");
            System.out.println(" TERRAIN PREPROCESSING");
            System.out.println("═════════════════════════════════════");
            System.out.println(state.summary());
            System.out.println("─────────────────────────────────────");
            System.out.println("  1) Load point cloud   (LAS / LAZ / XYZ)");
            System.out.println("  2) Ground filter      (CSF — separate ground from vegetation)");
            System.out.println("  3) Subsample          (reduce point count for faster meshing)");
            System.out.println("  4) Generate mesh      (Delaunay triangulation → PLY)");
            System.out.println("  5) Use generated mesh → continue to pathfinding");
            System.out.println("  q) Quit");
            System.out.println("─────────────────────────────────────");
            System.out.print("Choice: ");
            String choice = sc.nextLine().trim().toLowerCase();

            switch (choice) {
                case "1":
                    PointCloudLoader.run(sc, state);
                    break;
                case "2":
                    GroundFilter.run(sc, state);
                    break;
                case "3":
                    Subsampler.run(sc, state);
                    break;
                case "4":
                    MeshGenerator.run(sc, state);
                    break;
                case "5":
                    if (state.lastMeshPath == null) {
                        System.out.println("  No mesh generated yet. Run step 4 first.");
                        break;
                    }
                    System.out.println("\nLoading generated mesh...");
                    try {
                        MeshData mesh = PLYParser.load(state.lastMeshPath);
                        System.out.printf("  Loaded %d vertices, %d faces.%n",
                                mesh.getVertices().size(), mesh.getFaces().size());
                        return mesh;
                    } catch (Exception e) {
                        System.out.printf("  Failed to load mesh: %s%n", e.getMessage());
                    }
                    break;
                case "q": case "quit": case "exit":
                    return null;
                default:
                    System.out.println("  Enter 1-5 or q.");
            }
        }
    }

    // =========================================================================
    // Phase 1b — Load existing mesh
    // =========================================================================

    private static MeshData loadMesh(Scanner sc) {
        while (true) {
            System.out.println("\n─────────────────────────────────────");
            System.out.println(" LOAD MESH");
            System.out.println("─────────────────────────────────────");
            System.out.printf("Mesh directory: %s%n",
                    new File(MESH_DIR).getAbsolutePath());
            System.out.print("Enter PLY filename (e.g. survey.ply) [or 'quit']: ");
            String input = sc.nextLine().trim();

            if (isQuit(input)) return null;
            if (!input.toLowerCase().endsWith(".ply")) input += ".ply";

            File f = new File(MESH_DIR + input);
            if (!f.exists()) {
                System.out.printf("  ✗ File not found: %s%n", f.getAbsolutePath());
                continue;
            }

            System.out.printf("  Parsing %s...%n", f.getName());
            try {
                MeshData mesh = PLYParser.load(f.getPath());
                System.out.printf("  ✓ Loaded %d vertices, %d faces.%n",
                        mesh.getVertices().size(), mesh.getFaces().size());
                return mesh;
            } catch (Exception e) {
                System.out.printf("  ✗ Parse error: %s%n", e.getMessage());
            }
        }
    }

    /**
     * Offers three selection modes for one vertex (start or end).
     * Returns null if the user quits.
     */
    private static Vertex selectVertex(Scanner sc, MeshData mesh, String label) {
        while (true) {
            System.out.printf("%nSelect %s vertex:%n", label);
            System.out.println("  1) Vertex ID");
            System.out.println("  2) Nearest to X, Y coordinates");
            System.out.println("  3) Nearest to centre of an X/Y range");
            System.out.println("  q) Quit");
            System.out.print("Choice: ");
            String choice = sc.nextLine().trim();

            switch (choice.toLowerCase()) {
                case "1": {
                    Vertex v = selectById(sc, mesh);
                    if (v != null) return v;
                    break;
                }
                case "2": {
                    Vertex v = selectByXY(sc, mesh);
                    if (v != null) return v;
                    break;
                }
                case "3": {
                    Vertex v = selectByRange(sc, mesh);
                    if (v != null) return v;
                    break;
                }
                case "q": case "quit": case "exit":
                    return null;
                default:
                    System.out.println("  Please enter 1, 2, 3, or q.");
            }
        }
    }

    private static Vertex selectById(Scanner sc, MeshData mesh) {
        int max = mesh.getVertices().size() - 1;
        System.out.printf("  Enter vertex ID (0 – %d): ", max);
        String input = sc.nextLine().trim();
        try {
            int id = Integer.parseInt(input);
            if (id < 0 || id > max) {
                System.out.printf("  ✗ ID out of range (0 – %d).%n", max);
                return null;
            }
            Vertex v = mesh.getVertices().get(id);
            System.out.printf("  ✓ Vertex %d  (%.4f, %.4f, %.4f)%n",
                    v.id, v.x, v.y, v.z);
            return v;
        } catch (NumberFormatException e) {
            System.out.println("  ✗ Enter a whole number.");
            return null;
        }
    }

    private static Vertex selectByXY(Scanner sc, MeshData mesh) {
        Double x = promptDouble(sc, "  X coordinate: ");
        if (x == null) return null;
        Double y = promptDouble(sc, "  Y coordinate: ");
        if (y == null) return null;

        Vertex v = VertexSelector.findNearest(mesh, x, y);
        System.out.printf("  ✓ Nearest vertex → ID %d  (%.4f, %.4f, %.4f)  [XY dist %.4f]%n",
                v.id, v.x, v.y, v.z, Math.hypot(v.x - x, v.y - y));
        return v;
    }

    private static Vertex selectByRange(Scanner sc, MeshData mesh) {
        System.out.println("  Enter the X range:");
        Double xMin = promptDouble(sc, "    X min: ");
        if (xMin == null) return null;
        Double xMax = promptDouble(sc, "    X max: ");
        if (xMax == null) return null;

        System.out.println("  Enter the Y range:");
        Double yMin = promptDouble(sc, "    Y min: ");
        if (yMin == null) return null;
        Double yMax = promptDouble(sc, "    Y max: ");
        if (yMax == null) return null;

        try {
            Vertex v = VertexSelector.findNearestToCentre(mesh, xMin, xMax, yMin, yMax);
            double cx = (xMin + xMax) / 2.0;
            double cy = (yMin + yMax) / 2.0;
            System.out.printf(
                    "  ✓ Centre (%.4f, %.4f) → vertex ID %d  (%.4f, %.4f, %.4f)%n",
                    cx, cy, v.id, v.x, v.y, v.z);
            return v;
        } catch (IllegalArgumentException e) {
            System.out.printf("  ✗ %s%n", e.getMessage());
            return null;
        }
    }

    // =========================================================================
    // Phase 3 — Cost function selection
    // =========================================================================

    /**
     * Prompts for a cost metric or combination.
     * Writes a human-readable label into nameHolder[0].
     * Returns null if the user quits.
     */
    private static CostFunction chooseCostFunction(Scanner sc, String[] nameHolder) {
        System.out.println("\nSelect cost metric:");
        System.out.println("  1) Distance      — shortest 3-D route");
        System.out.println("  2) Travel Time   — fastest route");
        System.out.println("  3) Fuel          — most fuel-efficient route");
        System.out.println("  4) Forest Cover  — maximise shade/cover");
        System.out.println("  5) Weighted blend of Time + Fuel");
        System.out.println("  6) Any metric with a grade penalty");
        System.out.println("  q) Quit");
        System.out.print("Choice: ");
        String input = sc.nextLine().trim();

        switch (input.toLowerCase()) {
            case "1":
                nameHolder[0] = "Shortest Distance";
                return CostFunction.byDistance();
            case "2":
                nameHolder[0] = "Fastest Time";
                return CostFunction.byTime();
            case "3":
                nameHolder[0] = "Least Fuel";
                return CostFunction.byFuel();
            case "4":
                nameHolder[0] = "Max Forest Cover";
                return CostFunction.byForestCoverage();
            case "5":
                return buildWeightedBlend(sc, nameHolder);
            case "6":
                return buildGradePenalty(sc, nameHolder);
            case "q": case "quit": case "exit":
                return null;
            default:
                System.out.println("  Please enter 1–6 or q.");
                return chooseCostFunction(sc, nameHolder);
        }
    }

    private static CostFunction buildWeightedBlend(Scanner sc, String[] nameHolder) {
        System.out.println("\n  Time + Fuel weighted blend:");
        Double tw = promptDouble(sc, "    Time weight  (e.g. 0.6): ");
        if (tw == null) tw = 0.5;
        Double fw = promptDouble(sc, "    Fuel weight  (e.g. 0.4): ");
        if (fw == null) fw = 0.5;

        if (tw < 0 || fw < 0) {
            System.out.println("  ✗ Weights must be non-negative. Using 0.5 / 0.5.");
            tw = 0.5; fw = 0.5;
        }
        nameHolder[0] = String.format("Time×%.2f + Fuel×%.2f", tw, fw);
        System.out.printf("  Using: %s%n", nameHolder[0]);
        return CostFunction.weighted(tw, fw);
    }

    private static CostFunction buildGradePenalty(Scanner sc, String[] nameHolder) {
        System.out.println("\n  Grade-penalised route:");
        System.out.println("  First choose the base metric:");
        System.out.println("    1) Distance   2) Time   3) Fuel");
        System.out.print("  Base choice: ");
        String baseChoice = sc.nextLine().trim();

        CostFunction base;
        String baseName;
        switch (baseChoice) {
            case "2":  base = CostFunction.byTime();     baseName = "Time";     break;
            case "3":  base = CostFunction.byFuel();     baseName = "Fuel";     break;
            default:   base = CostFunction.byDistance(); baseName = "Distance"; break;
        }

        Double penalty = promptDouble(sc, "  Grade penalty factor (e.g. 0.5): ");
        if (penalty == null || penalty < 0) {
            System.out.println("  ✗ Penalty must be non-negative. Using 0.5.");
            penalty = 0.5;
        }
        nameHolder[0] = String.format("%s + grade×%.2f", baseName, penalty);
        System.out.printf("  Using: %s%n", nameHolder[0]);
        return CostFunction.withGradePenalty(base, penalty);
    }

    // =========================================================================
    // Phase 4 — Export
    // =========================================================================

    private static void offerExport(Scanner sc, List<Vertex> path, String pathName) {
        System.out.print("\nExport this path as a PLY file? [y/n]: ");
        String ans = sc.nextLine().trim().toLowerCase();
        if (!ans.equals("y") && !ans.equals("yes")) return;

        String defaultName = sanitise(pathName) + ".ply";
        new File(PATHS_DIR).mkdirs();

        System.out.printf("  Output directory: %s%n", new File(PATHS_DIR).getAbsolutePath());
        System.out.printf("  Filename [default: %s]: ", defaultName);
        String name = sc.nextLine().trim();
        if (name.isEmpty()) name = defaultName;
        if (!name.toLowerCase().endsWith(".ply")) name += ".ply";

        String outPath = PATHS_DIR + name;
        try {
            PathExporter.exportPathPLY(path, outPath);
            System.out.printf("  ✓ Saved to %s%n", new File(outPath).getAbsolutePath());
        } catch (IOException e) {
            System.out.printf("  ✗ Export failed: %s%n", e.getMessage());
        }
    }

    // =========================================================================
    // Map export
    // =========================================================================

    private static void offerMapExport(Scanner sc, Graph graph, MeshData mesh) {
        System.out.print("\nExport graph as a map file? [y/n]: ");
        String ans = sc.nextLine().trim().toLowerCase();
        if (!ans.equals("y") && !ans.equals("yes")) return;

        String defaultName = "map_" + System.currentTimeMillis() + ".txt";
        new File(MAPS_DIR).mkdirs();

        System.out.printf("  Output directory: %s%n", new File(MAPS_DIR).getAbsolutePath());
        System.out.printf("  Filename [default: %s]: ", defaultName);
        String name = sc.nextLine().trim();
        if (name.isEmpty()) name = defaultName;
        if (!name.toLowerCase().endsWith(".txt")) name += ".txt";

        try {
            MapExporter.export(graph, mesh.getVertices(), MAPS_DIR + name);
            System.out.printf("  ✓ Map saved to %s%n",
                    new File(MAPS_DIR + name).getAbsolutePath());
        } catch (IOException e) {
            System.out.printf("  ✗ Map export failed: %s%n", e.getMessage());
        }
    }

    // =========================================================================
    // Phase 5 — Report
    // =========================================================================

    private static void offerReport(Scanner sc, ReportGenerator report) {
        System.out.println("\n─────────────────────────────────────");
        System.out.println(" COMPARISON REPORT");
        System.out.println("─────────────────────────────────────");
        System.out.print("Generate report? [y/n]: ");
        String ans = sc.nextLine().trim().toLowerCase();
        if (!ans.equals("y") && !ans.equals("yes")) return;

        String defaultName = "report_" + System.currentTimeMillis() + ".txt";
        new File(REPORTS_DIR).mkdirs();

        System.out.printf("  Output directory: %s%n", new File(REPORTS_DIR).getAbsolutePath());
        System.out.printf("  Filename [default: %s]: ", defaultName);
        String name = sc.nextLine().trim();
        if (name.isEmpty()) name = defaultName;
        if (!name.toLowerCase().endsWith(".txt")) name += ".txt";

        try {
            report.write(REPORTS_DIR + name);
            System.out.printf("  ✓ Report saved to %s%n",
                    new File(REPORTS_DIR + name).getAbsolutePath());
        } catch (IOException e) {
            System.out.printf("  ✗ Report failed: %s%n", e.getMessage());
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static void printBanner() {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Mine Profile Edge — Pathfinding Tool       ║");
        System.out.println("╚══════════════════════════════════════════════╝");
    }

    private static Double promptDouble(Scanner sc, String prompt) {
        System.out.print(prompt);
        String input = sc.nextLine().trim();
        if (isQuit(input)) return null;
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException e) {
            System.out.println("  ✗ Enter a number (e.g. 123.45).");
            return null;
        }
    }

    /** Like promptDouble but returns defaultValue when the user presses Enter. */
    private static double promptDoubleWithDefault(Scanner sc, String prompt, double defaultValue) {
        System.out.print(prompt);
        String input = sc.nextLine().trim();
        if (input.isEmpty()) return defaultValue;
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException e) {
            System.out.printf("  ✗ Invalid input — using default (%.2f).%n", defaultValue);
            return defaultValue;
        }
    }

    private static boolean isQuit(String s) {
        return s.equalsIgnoreCase("q")
            || s.equalsIgnoreCase("quit")
            || s.equalsIgnoreCase("exit");
    }

    private static String sanitise(String s) {
        return s.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}