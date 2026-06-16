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
        String fileName = "data/meshes/forest.ply";
        int start = 4385;
        int goal = 10000;

        MeshData mesh = PLYParser.load(fileName);

        ForestClassifier fc = new ForestClassifier(mesh);  // prints classification summary
        Graph graph = new Graph(mesh, fc);

        // Maximise forest coverage
        List<Vertex> path = Dijkstra.findPath(graph, start, goal,
            CostFunction.byForestCoverage());
        PathExporter.exportPathPLY(path, "data/paths/forest_path_with_tree_coverage.ply");

        //Or blend: 60% time, 40% forest preference
        List<Vertex> path2 = Dijkstra.findPath(graph, start, goal,
            CostFunction.withForestBonus(CostFunction.byTime(), 0.4, 0.6));
        PathExporter.exportPathPLY(path2, "data/paths/forest_path_blended.ply");
    }
}
