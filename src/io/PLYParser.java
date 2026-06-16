package src.io;

/**
 * Reads PLY terrain meshes.
 *
 * Extracts:
 * - vertices
 * - faces
 *
 * Converts mesh data into MeshData objects.
*/

import src.model.MeshData;
import src.model.Vertex;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class PLYParser {

    public static MeshData load(String filename) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(filename));

        String line;

        int vertexCount = 0;
        int faceCount = 0;

        //-----------------------------------
        // Read header
        //-----------------------------------
        while ((line = br.readLine()) != null) {

            if (line.startsWith("element vertex")) {
                vertexCount = Integer.parseInt(line.split("\\s+")[2]);

            } else if (line.startsWith("element face")) {
                faceCount = Integer.parseInt(line.split("\\s+")[2]);

            } else if (line.equals("end_header")) {
                break;

            }
        }

        //-----------------------------------
        // Read vertices
        //-----------------------------------
        List<Vertex> vertices = new ArrayList<>(vertexCount);

        System.out.println( "Reading " + vertexCount + " vertices...");

        for (int i = 0; i < vertexCount; i++) {
            line = br.readLine();

            String[] parts = line.trim().split("\\s+");

            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);

            vertices.add(new Vertex(i, x, y, z));
        }

        //-----------------------------------
        // Read faces
        //-----------------------------------
        List<int[]> faces = new ArrayList<>(faceCount);

        System.out.println( "Reading " + faceCount + " faces...");

        for (int i = 0; i < faceCount; i++) {
            line = br.readLine();

            String[] parts = line.trim().split("\\s+");

            int verticesInFace = Integer.parseInt(parts[0]);
            if (verticesInFace != 3) {
                throw new RuntimeException("Non-triangular face detected on line " + i);
            }

            int v1 = Integer.parseInt(parts[1]);
            int v2 = Integer.parseInt(parts[2]);
            int v3 = Integer.parseInt(parts[3]);

            faces.add(new int[] {v1, v2, v3});
        }

        br.close();

        MeshData mesh = new MeshData(vertices, faces);
        System.out.println("Loaded: " + mesh);
        return mesh;
    }
}