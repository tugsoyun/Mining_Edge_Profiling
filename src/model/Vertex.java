package src.model;

public class Vertex {
    public int id;
    public double x, y, z;

    public Vertex(int index, double xcoord, double ycoord, double zcoord) {
        this.id = index;
        this.x = xcoord;
        this.y = ycoord;
        this.z = zcoord;
    }

    @Override
    public String toString() {
        return "vertex " + id + " located at (" + x + ", " + y + ", " + z + ")";
    }
}
