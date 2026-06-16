package src.io;

/**
 * Reads PLY terrain meshes exported from CloudCompare.
 *
 * Extracts:
 * - vertices (x, y, z)
 * - faces
 * - scalar_Number_Of_Returns per vertex (if present)
 *
 * Property columns are resolved dynamically from the header, so the
 * parser is robust to any CloudCompare export order or extra scalar fields.
 *
 * Converts mesh data into MeshData objects.
 */

import src.model.MeshData;
import src.model.Vertex;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PLYParser {
    // Canonical property names we care about
    private static final String PROP_X               = "x";
    private static final String PROP_Y               = "y";
    private static final String PROP_Z               = "z";
    private static final String PROP_NUM_RETURNS      = "scalar_Number_Of_Returns";

    public static MeshData load(String filename) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(filename));

        String line;

        int vertexCount = 0;
        int faceCount   = 0;

        // column index for each property we care about (-1 = not present)
        Map<String, Integer> propIndex = new HashMap<>();
        int totalVertexProps = 0;
        boolean inVertexElement = false;

        //-----------------------------------
        // Read header
        //-----------------------------------
        while ((line = br.readLine()) != null) {
            line = line.trim();

            if (line.startsWith("element vertex")) {
                vertexCount = Integer.parseInt(line.split("\\s+")[2]);
                inVertexElement = true;

            } else if (line.startsWith("element face")) {
                faceCount = Integer.parseInt(line.split("\\s+")[2]);
                inVertexElement = false;  // moved past vertex block

            } else if (line.startsWith("property") && inVertexElement
                    && !line.startsWith("property list")) {
                // e.g. "property double x"  or  "property float scalar_Number_Of_Returns"
                String[] tokens = line.split("\\s+");
                String name = tokens[2];
                propIndex.put(name, totalVertexProps);
                totalVertexProps++;

            } else if (line.equals("end_header")) {
                break;
            }
        }

        // Validate required properties
        requireProperty(propIndex, PROP_X, filename);
        requireProperty(propIndex, PROP_Y, filename);
        requireProperty(propIndex, PROP_Z, filename);

        int xIdx          = propIndex.get(PROP_X);
        int yIdx          = propIndex.get(PROP_Y);
        int zIdx          = propIndex.get(PROP_Z);
        int numReturnsIdx = propIndex.getOrDefault(PROP_NUM_RETURNS, -1);

        boolean hasReturnData = numReturnsIdx != -1;
        if (!hasReturnData) {
            System.out.println(
                "PLYParser: 'scalar_Number_Of_Returns' not found — " +
                "forest coverage will not be available.");
        }

        //-----------------------------------
        // Read vertices
        //-----------------------------------
        List<Vertex> vertices   = new ArrayList<>(vertexCount);
        float[] numberOfReturns = hasReturnData ? new float[vertexCount] : null;

        System.out.println("Reading " + vertexCount + " vertices...");

        for (int i = 0; i < vertexCount; i++) {
            line = br.readLine();
            String[] parts = line.trim().split("\\s+");

            double x = Double.parseDouble(parts[xIdx]);
            double y = Double.parseDouble(parts[yIdx]);
            double z = Double.parseDouble(parts[zIdx]);

            vertices.add(new Vertex(i, x, y, z));

            if (hasReturnData) {
                numberOfReturns[i] = Float.parseFloat(parts[numReturnsIdx]);
            }
        }

        //-----------------------------------
        // Read faces
        //-----------------------------------
        List<int[]> faces = new ArrayList<>(faceCount);

        System.out.println("Reading " + faceCount + " faces...");

        for (int i = 0; i < faceCount; i++) {
            line = br.readLine();
            String[] parts = line.trim().split("\\s+");

            int verticesInFace = Integer.parseInt(parts[0]);
            if (verticesInFace != 3) {
                throw new RuntimeException("Non-triangular face detected at face index " + i);
            }

            int v1 = Integer.parseInt(parts[1]);
            int v2 = Integer.parseInt(parts[2]);
            int v3 = Integer.parseInt(parts[3]);

            faces.add(new int[]{v1, v2, v3});
        }

        br.close();

        MeshData mesh = new MeshData(vertices, faces, numberOfReturns);
        System.out.println("Loaded: " + mesh);
        return mesh;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static void requireProperty(
            Map<String, Integer> propIndex,
            String name,
            String filename) {
        if (!propIndex.containsKey(name)) {
            throw new IllegalArgumentException(String.format(
                "PLY file '%s' is missing required property '%s'.", filename, name));
        }
    }
}