package src;

import java.util.List;

import src.graph.CostFunction;
import src.graph.Graph;
import src.graph.Dijkstra;
import src.graph.ForestClassifier;
import src.io.*;
import src.model.*;

public class Main {
    public static void main(String[] args) throws Exception {
        String fileName = "data/meshes/valley_big_sample.ply";
        int start = 1221;
        int goal = 122221;

        MeshData mesh = PLYParser.load(fileName);

        ForestClassifier fc = new ForestClassifier(mesh);  // prints classification summary
        Graph graph = new Graph(mesh, fc);

        ReportGenerator report = new ReportGenerator(graph);

        List<Vertex> timePath = Dijkstra.findPath(graph, start, goal, CostFunction.byTime());
        List<Vertex> fuelPath = Dijkstra.findPath(graph, start, goal, CostFunction.byFuel());
        List<Vertex> forestPath = Dijkstra.findPath(graph, start, goal, CostFunction.byForestCoverage());
        List<Vertex> blendedPath = Dijkstra.findPath(graph, start, goal,
            CostFunction.withForestBonus(CostFunction.byTime(), 0.5, 0.5));

        PathExporter.exportPathPLY(timePath, "data/paths/valley_time_path.ply");
        PathExporter.exportPathPLY(fuelPath, "data/paths/valley_fuel_path.ply");
        PathExporter.exportPathPLY(forestPath, "data/paths/valley_forest_path.ply");
        PathExporter.exportPathPLY(blendedPath, "data/paths/valley_blended_path.ply");


        report.addPath("Fastest",       timePath,   CostFunction.byTime());
        report.addPath("Blended",    blendedPath,   CostFunction.byFuel());
        report.addPath("Max Forest",    forestPath, CostFunction.byForestCoverage());

        report.write("data/reports/valley_report.txt");

        // // Maximise forest coverage
        // List<Vertex> path = Dijkstra.findPath(graph, start, goal,
        //     CostFunction.byForestCoverage());
        // PathExporter.exportPathPLY(path, "data/paths/valley_path_with_tree_coverage.ply");

        // //Or blend: 60% time, 40% forest preference
        
    }
}
