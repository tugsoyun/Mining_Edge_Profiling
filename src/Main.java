package src;

import java.util.List;

import src.graph.CostFunction;
import src.graph.Graph;
import src.graph.Dijkstra;
import src.io.*;
import src.model.*;

public class Main {
    public static void main(String[] args) throws Exception {
        String fileName = "data/meshes/forest.ply";

        MeshData mesh = PLYParser.load(fileName);

        Graph graph = new Graph(mesh);

        List<Vertex> path = Dijkstra.findPath(graph, 4385, 10000, CostFunction.byDistance());
        PathExporter.exportPathPLY(path, "data/paths/forest_distance_path.ply");
    }
}
