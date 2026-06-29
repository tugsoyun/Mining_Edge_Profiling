package src.io;

/**
 * Generates a plain-text comparison report for one or more paths.
 *
 * Each path is summarised with:
 * - Path summary totals (distance, time, fuel, forest coverage)
 * - Slope statistics (mean grade, std dev of grade, max grade)
 *
 * Start/end vertex coordinates are printed once at the top since all
 * compared paths share the same start and end points.
 *
 * Usage:
 * <pre>
 *   ReportGenerator report = new ReportGenerator(graph);
 *
 *   report.addPath("Fastest",           timePath,   CostFunction.byTime());
 *   report.addPath("Most Fuel Efficient", fuelPath,  CostFunction.byFuel());
 *   report.addPath("Max Forest",        forestPath, CostFunction.byForestCoverage());
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

    private static final int COL_WIDTH   = 28;
    private static final int LABEL_WIDTH = 26;

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
     * @throws IOException           if the file cannot be written
     * @throws IllegalStateException if no paths have been added
     */
    public void write(String filePath) throws IOException {
        if (entries.isEmpty()) {
            throw new IllegalStateException(
                "No paths added to report. Call addPath() before write().");
        }

        List<PathStats> stats = new ArrayList<>();
        for (PathEntry entry : entries) {
            stats.add(new PathStats(entry, graph));
        }

        try (BufferedWriter w = new BufferedWriter(new FileWriter(filePath))) {
            writeHeader(w, stats);
            writeStartEnd(w, stats.get(0));
            writeSummaryTable(w, stats);
            writeSlopeTable(w, stats);
            writeFooter(w);
        }

        System.out.printf("Report written to: %s%n", filePath);
    }

    // -------------------------------------------------------------------------
    // Section writers
    // -------------------------------------------------------------------------

    private void writeHeader(BufferedWriter w, List<PathStats> stats) throws IOException {
        String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        int lineWidth = LABEL_WIDTH + COL_WIDTH * entries.size() + entries.size() - 1;

        w.write("=".repeat(lineWidth)); w.newLine();
        w.write(" ROUTE COMPARISON REPORT");  w.newLine();
        w.write(" Generated : " + timestamp); w.newLine();
        w.write(" Paths     : " + entries.size()); w.newLine();
        w.write("=".repeat(lineWidth)); w.newLine();
        w.newLine();
    }

    /**
     * Prints start/end coordinates once — all paths share the same endpoints.
     */
    private void writeStartEnd(BufferedWriter w, PathStats s) throws IOException {
        w.write("START / END"); w.newLine();
        w.write("-".repeat(LABEL_WIDTH + COL_WIDTH * entries.size() + entries.size() - 1));
        w.newLine();
        w.write(String.format("  Start  vertex %-6d  (%.4f, %.4f, %.4f)%n",
            s.startVertex.id, s.startVertex.x, s.startVertex.y, s.startVertex.z));
        w.write(String.format("  End    vertex %-6d  (%.4f, %.4f, %.4f)%n",
            s.endVertex.id,   s.endVertex.x,   s.endVertex.y,   s.endVertex.z));
        w.newLine();
    }

    private void writeSummaryTable(BufferedWriter w, List<PathStats> stats) throws IOException {
        int lineWidth = LABEL_WIDTH + COL_WIDTH * entries.size() + entries.size() - 1;
        w.write("PATH SUMMARY"); w.newLine();
        w.write("-".repeat(lineWidth)); w.newLine();

        writeColumnHeaders(w, stats);
        w.write("-".repeat(lineWidth)); w.newLine();

        writeRow(w, stats, "Vertices in path",     s -> String.valueOf(s.vertexCount));
        writeRow(w, stats, "Distance (m)",          s -> String.format("%.2f",  s.totalDistance));
        writeRow(w, stats, "Travel Time (s)",        s -> String.format("%.2f",  s.totalTime));
        writeRow(w, stats, "Fuel Used (mL)",         s -> String.format("%.2f",  s.totalFuel));
        writeRow(w, stats, "Forest Coverage (%)",    s -> String.format("%.1f%%", s.forestCoveragePct));

        w.newLine();
    }

    private void writeSlopeTable(BufferedWriter w, List<PathStats> stats) throws IOException {
        int lineWidth = LABEL_WIDTH + COL_WIDTH * entries.size() + entries.size() - 1;
        w.write("SLOPE PROFILE"); w.newLine();
        w.write("-".repeat(lineWidth)); w.newLine();

        writeColumnHeaders(w, stats);
        w.write("-".repeat(lineWidth)); w.newLine();

        writeRow(w, stats, "Mean Grade (%)",
            s -> String.format("%.2f%%", s.meanGrade));
        writeRow(w, stats, "Grade Std Dev (%) [bumpiness]",
            s -> String.format("%.2f%%", s.stdDevGrade));
        writeRow(w, stats, "Max Uphill Grade (%)",
            s -> String.format("%.2f%%", s.maxGrade));
        writeRow(w, stats, "Max Downhill Grade (%)",
            s -> String.format("%.2f%%", s.minGrade));

        w.newLine();
    }

    private void writeFooter(BufferedWriter w) throws IOException {
        int lineWidth = LABEL_WIDTH + COL_WIDTH * entries.size() + entries.size() - 1;
        w.write("=".repeat(lineWidth)); w.newLine();
        w.write(" END OF REPORT"); w.newLine();
        w.write("=".repeat(lineWidth)); w.newLine();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @FunctionalInterface
    private interface StatFormatter {
        String format(PathStats s);
    }

    private void writeColumnHeaders(BufferedWriter w, List<PathStats> stats) throws IOException {
        w.write(padRight("", LABEL_WIDTH));
        for (PathStats s : stats) {
            w.write("| " + padRight(s.name, COL_WIDTH - 2));
        }
        w.newLine();
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

        double totalDistance     = 0.0;
        double totalTime         = 0.0;
        double totalFuel         = 0.0;
        double forestCoveragePct = 0.0;

        double meanGrade    = 0.0;
        double stdDevGrade  = 0.0;
        double maxGrade     = -Double.MAX_VALUE;
        double minGrade     =  Double.MAX_VALUE;

        PathStats(PathEntry entry, Graph graph) {
            this.name        = entry.name;
            this.vertexCount = entry.path.size();
            this.startVertex = entry.path.get(0);
            this.endVertex   = entry.path.get(entry.path.size() - 1);

            // Collect per-edge values in one pass
            List<Double> grades = new ArrayList<>();
            double coverageSum  = 0.0;

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

                double grade = edge.getMetric(CostMetric.GRADE);
                grades.add(grade);
                if (grade > maxGrade) maxGrade = grade;
                if (grade < minGrade) minGrade = grade;
            }

            int edgeCount = grades.size();
            if (edgeCount == 0) return;

            forestCoveragePct = 100.0 * coverageSum / edgeCount;

            // Mean grade
            double gradeSum = 0.0;
            for (double g : grades) gradeSum += g;
            meanGrade = gradeSum / edgeCount;

            // Std dev of grade (bumpiness)
            double variance = 0.0;
            for (double g : grades) {
                double diff = g - meanGrade;
                variance += diff * diff;
            }
            stdDevGrade = Math.sqrt(variance / edgeCount);
        }
    }
}