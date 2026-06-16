package src.model;

/*
Container class holding:
  - vertices
  - faces 
 
Returned by PLYParser
*/

import java.util.List;

public class MeshData {
    private List<Vertex> vertices;
    private List<int[]> faces;

    public MeshData(
            List<Vertex> vertices,
            List<int[]> faces) {

        this.vertices = vertices;
        this.faces = faces;
    }

    public List<Vertex> getVertices() {
        return vertices;
    }

    public List<int[]> getFaces() {
        return faces;
    }

    public int getVertexCount() {
        return vertices.size();
    }

    public int getFaceCount() {
        return faces.size();
    }

    @Override
    public String toString() {
        return String.format(
                "MeshData: %d vertices, %d faces",
                vertices.size(),
                faces.size()
        );
    }
}