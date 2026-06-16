package src.model;

/*
Container class holding:
  - vertices
  - faces
  - numberOfReturns (LiDAR scalar, null if not present in PLY)

Returned by PLYParser
*/

import java.util.List;

public class MeshData {
    private List<Vertex> vertices;
    private List<int[]> faces;
    private float[] numberOfReturns;  // null if PLY had no return data

    /** Constructor without LiDAR return data (backwards compatible). */
    public MeshData(List<Vertex> vertices, List<int[]> faces) {
        this(vertices, faces, null);
    }

    /** Full constructor including LiDAR scalar field. */
    public MeshData(List<Vertex> vertices, List<int[]> faces, float[] numberOfReturns) {
        this.vertices = vertices;
        this.faces = faces;
        this.numberOfReturns = numberOfReturns;
    }

    public List<Vertex> getVertices()    { return vertices; }
    public List<int[]>  getFaces()       { return faces; }
    public float[]      getNumberOfReturns() { return numberOfReturns; }
    public boolean      hasReturnData()  { return numberOfReturns != null; }

    public int getVertexCount() { return vertices.size(); }
    public int getFaceCount()   { return faces.size(); }

    @Override
    public String toString() {
        return String.format(
            "MeshData: %d vertices, %d faces%s",
            vertices.size(),
            faces.size(),
            numberOfReturns != null ? " (LiDAR returns: yes)" : ""
        );
    }
}