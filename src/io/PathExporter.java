package src.io;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import src.model.Vertex;

/**
 * Exports computed routes.
 *
 * Supported formats:
 * - XYZ
 * - PLY
 *
 * Used for CloudCompare visualization.
*/

public class PathExporter {
    public static void exportPathXYZ(
            List<Integer> path,
            List<Vertex> vertices,
            String filename) throws IOException {

        PrintWriter out = new PrintWriter(filename);

        for (int id : path) {

            Vertex v = vertices.get(id);

            out.printf(
                "%.6f %.6f %.6f%n",
                v.x,
                v.y,
                v.z
            );
        }

        out.close();
    }

    public static void exportPathPLY(
            List<Vertex> path,
            String filename)
            throws IOException {

        PrintWriter out =
                new PrintWriter(filename);

        out.println("ply");
        out.println("format ascii 1.0");
        out.println("element vertex " + path.size());

        out.println("property float x");
        out.println("property float y");
        out.println("property float z");

        out.println("property uchar red");
        out.println("property uchar green");
        out.println("property uchar blue");

        out.println("end_header");

        int random_r = (int)(Math.random() * 256);
        int random_g = (int)(Math.random() * 256);
        int random_b = (int)(Math.random() * 256);

        for (Vertex v : path) {
            out.printf(
                "%.6f %.6f %.6f %d %d %d%n",
                v.x,
                v.y,
                v.z,
                random_r,
                random_g,
                random_b
            );
        }

        out.close();
    }
}
