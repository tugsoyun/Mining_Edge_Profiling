package src.io;

/**
 * Generates a plain-text comparison report for one or more paths.
 *
 * Each path is summarised with:
 * - Name and cost function used
 * - Start/end vertex coordinates
 * - Total distance, travel time, fuel used, forest coverage %
 *
 * Multiple paths are printed side by side for easy comparison.
 *
 * Usage:
 * <pre>
 *   ReportGenerator report = new ReportGenerator(graph);
 *
 *   report.addPath("Fastest",          timePath,     CostFunction.byTime());
 *   report.addPath("Most Fuel Efficient", fuelPath,  CostFunction.byFuel());
 *   report.addPath("Max Forest",       forestPath,   CostFunction.byForestCoverage());
 *
 *   report.write("output/report.txt");
 * </pre>
 */

import src.graph.CostFunction;
import src.graph.CostMetric;
import src.graph.Graph;
import src.model.Edge;
import src.model.Vertex;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ReportGenerator {

    private static final int COL_WIDTH    = 28;  // width of each path column
    private static final int LABEL_WIDTH  = 22;  // width of the row label

    private final Graph graph;
    private final List<PathEntry> entries = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Inner record for a single path entry
    // -------------------------------------------------------------------------

    private static class PathEntry {
        final String       name;
        final List<Vertex> path;
        final CostFunction costFunction;

        PathEntry(String name, List<Vertex> path, CostFunction costFunction) {
            this.name         = name;
            this.path         = path;
            this.costFunction = costFunction;
        }
    }

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public ReportGenerator(Graph graph) {
        this.graph = graph;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Registers a path for inclusion in the report.
     *
     * @param name         display name for this path (e.g. "Fastest Route")
     * @param path         ordered vertex list from Dijkstra.findPath()
     * @param costFunction the cost function used to compute this path
     */
    public void addPath(String name, List<Vertex> path, CostFunction costFunction) {
        entries.add(new PathEntry(name, path, costFunction));
    }

    /**
     * Writes the report to a plain-text file.
     *
     * @param filePath destination file path, e.g. "output/report.txt"
     * @throws IOException              if the file cannot be written
     * @throws IllegalStateException    if no paths have been added
     */
    public void write(String filePath) throws IOException {
        if (entries.isEmpty()) {
            throw new IllegalStateException(
                "No paths added to report. Call addPath() before write().");
        }

        // Pre-compute stats for all paths
        List<PathStats> stats = new ArrayList<>();
        for (PathEntry entry : entries) {
            stats.add(new PathStats(entry, graph));
        }

        try (BufferedWriter w = new BufferedWriter(new FileWriter(filePath))) {
            writeHeader(w);
            writeSummaryTable(w, stats);
            writeStartEndTable(w, stats);
            writeFooter(w);
        }

        System.out.printf("Report written to: %s%n", filePath);
    }

    // -------------------------------------------------------------------------
    // Section writers
    // -------------------------------------------------------------------------

    private void writeHeader(BufferedWriter w) throws IOException {
        String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        w.write("=".repeat(LABEL_WIDTH + COL_WIDTH * entries.size() + entries.size() - 1));
        w.newLine();
        w.write(" ROUTE COMPARISON REPORT");
        w.newLine();
        w.write(" Generated: " + timestamp);
        w.newLine();
        w.write(" Paths compared: " + entries.size());
        w.newLine();
        w.write("=".repeat(LABEL_WIDTH + COL_WIDTH * entries.size() + entries.size() - 1));
        w.newLine();
        w.newLine();
    }

    private void writeSummaryTable(BufferedWriter w, List<PathStats> stats) throws IOException {
        w.write("PATH SUMMARY");
        w.newLine();
        w.write("-".repeat(LABEL_WIDTH + COL_WIDTH * entries.size() + entries.size() - 1));
        w.newLine();

        // Column headers (path names)
        w.write(padRight("", LABEL_WIDTH));
        for (PathStats s : stats) {
            w.write("| " + padRight(s.name, COL_WIDTH - 2));
        }
        w.newLine();
        w.write("-".repeat(LABEL_WIDTH + COL_WIDTH * entries.size() + entries.size() - 1));
        w.newLine();

        // Rows
        writeRow(w, stats, "Vertices in path",
            s -> String.valueOf(s.vertexCount));
        writeRow(w, stats, "Distance (m)",
            s -> String.format("%.2f", s.totalDistance));
        writeRow(w, stats, "Travel Time (s)",
            s -> String.format("%.2f", s.totalTime));
        writeRow(w, stats, "Fuel Used (mL)",
            s -> String.format("%.2f", s.totalFuel));
        writeRow(w, stats, "Forest Coverage (%)",
            s -> String.format("%.1f%%", s.forestCoveragePct));

        w.newLine();
    }

    private void writeStartEndTable(BufferedWriter w, List<PathStats> stats) throws IOException {
        w.write("START / END COORDINATES");
        w.newLine();
        w.write("-".repeat(LABEL_WIDTH + COL_WIDTH * entries.size() + entries.size() - 1));
        w.newLine();

        // Column headers
        w.write(padRight("", LABEL_WIDTH));
        for (PathStats s : stats) {
            w.write("| " + padRight(s.name, COL_WIDTH - 2));
        }
        w.newLine();
        w.write("-".repeat(LABEL_WIDTH + COL_WIDTH * entries.size() + entries.size() - 1));
        w.newLine();

        writeRow(w, stats, "Start vertex ID",
            s -> String.valueOf(s.startVertex.id));
        writeRow(w, stats, "Start X (m)",
            s -> String.format("%.4f", s.startVertex.x));
        writeRow(w, stats, "Start Y (m)",
            s -> String.format("%.4f", s.startVertex.y));
        writeRow(w, stats, "Start Z (m)",
            s -> String.format("%.4f", s.startVertex.z));
        writeRow(w, stats, "End vertex ID",
            s -> String.valueOf(s.endVertex.id));
        writeRow(w, stats, "End X (m)",
            s -> String.format("%.4f", s.endVertex.x));
        writeRow(w, stats, "End Y (m)",
            s -> String.format("%.4f", s.endVertex.y));
        writeRow(w, stats, "End Z (m)",
            s -> String.format("%.4f", s.endVertex.z));

        w.newLine();
    }

    private void writeFooter(BufferedWriter w) throws IOException {
        w.write("=".repeat(LABEL_WIDTH + COL_WIDTH * entries.size() + entries.size() - 1));
        w.newLine();
        w.write(" END OF REPORT");
        w.newLine();
        w.write("=".repeat(LABEL_WIDTH + COL_WIDTH * entries.size() + entries.size() - 1));
        w.newLine();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @FunctionalInterface
    private interface StatFormatter {
        String format(PathStats s);
    }

    private void writeRow(
            BufferedWriter w,
            List<PathStats> stats,
            String label,
            StatFormatter formatter) throws IOException {

        w.write(padRight(label, LABEL_WIDTH));
        for (PathStats s : stats) {
            w.write("| " + padRight(formatter.format(s), COL_WIDTH - 2));
        }
        w.newLine();
    }

    private static String padRight(String s, int width) {
        if (s.length() >= width) return s.substring(0, width);
        return s + " ".repeat(width - s.length());
    }

    // -------------------------------------------------------------------------
    // PathStats: pre-computes all metrics for one path
    // -------------------------------------------------------------------------

    private static class PathStats {
        final String name;
        final int    vertexCount;
        final Vertex startVertex;
        final Vertex endVertex;

        double totalDistance      = 0.0;
        double totalTime          = 0.0;
        double totalFuel          = 0.0;
        double forestCoveragePct  = 0.0;

        PathStats(PathEntry entry, Graph graph) {
            this.name        = entry.name;
            this.vertexCount = entry.path.size();
            this.startVertex = entry.path.get(0);
            this.endVertex   = entry.path.get(entry.path.size() - 1);

            int edgeCount         = 0;
            double coverageSum    = 0.0;

            for (int i = 0; i < entry.path.size() - 1; i++) {
                int fromId = entry.path.get(i).id;
                int toId   = entry.path.get(i + 1).id;

                Edge edge = graph.getNeighbors(fromId).stream()
                    .filter(e -> e.to.id == toId)
                    .findFirst()
                    .orElse(null);

                if (edge == null) continue;

                totalDistance += edge.getMetric(CostMetric.DISTANCE);
                totalTime     += edge.getMetric(CostMetric.TIME);
                totalFuel     += edge.getMetric(CostMetric.FUEL);
                coverageSum   += edge.getMetric(CostMetric.FOREST_COVERAGE);
                edgeCount++;
            }

            if (edgeCount > 0) {
                forestCoveragePct = 100.0 * coverageSum / edgeCount;
            }
        }
    }
}