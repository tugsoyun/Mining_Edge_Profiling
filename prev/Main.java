package prev;
import java.io.*;
import java.util.*;

public class Main {
    static class Vehicle {
        // base values -> can be modified later
        static double BASESPEED = 25.0;        // in m/s
        static double BASEFUELRATE = 0.500;    // in mL/s

        public static void calculate(Edge edge) {
            // ---- Surface rolling resistance (%) ----
            double rollingResistance;

            switch (edge.surfaceType.toLowerCase()) {
                case "paved": rollingResistance = 2.0; break;
                case "gravel": rollingResistance = 4.0; break;
                case "dirt":
                case "soil": rollingResistance = 6.0; break;
                case "sand": rollingResistance = 12.0; break;
                default: rollingResistance = 5.0;
            }

            // positivem -> uphill, negative -> downhill
            double totalResistance = rollingResistance + edge.gradePercent;

            //----------------------------------------------------
            // SPEED MODEL
            //----------------------------------------------------
            double speed;

            if (totalResistance <= 0) {// downhill
                speed = BASESPEED * 
                        (1.0 + 0.15 * Math.tanh(-totalResistance / 8.0));
            } else { // uphill
                speed = BASESPEED *
                        Math.exp(-totalResistance / 18.0);
            }

            // realistic haul truck bounds
            speed = Math.max(8.0, Math.min(speed, 70.0));

            //----------------------------------------------------
            // FUEL MODEL
            //----------------------------------------------------
            double fuelRate;

            if (edge.gradePercent >= 0) {
                fuelRate =
                        BASEFUELRATE *
                        (1.0 +
                        0.03 * totalResistance +
                        0.0015 * totalResistance * totalResistance);

            } else {
                fuelRate =
                        BASEFUELRATE *
                        (0.65 +
                        0.01 * rollingResistance);
            }

            //----------------------------------------------------
            // TIME
            //----------------------------------------------------
            edge.travelTime = edge.distance3D / speed;

            //----------------------------------------------------
            // FUEL
            //----------------------------------------------------
            edge.fuelUsed = fuelRate * edge.travelTime;
        }
    }

    static class Vertex {
        int id;
        double x, y, z;

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

    static class Node implements Comparable<Node> {
        int vertexId;
        double cost;

        public Node(int vertexId, double cost) {
            this.vertexId = vertexId;
            this.cost = cost;
        }

        @Override
        public int compareTo(Node other) {
            return Double.compare(this.cost, other.cost);
        }
    }

    static class Edge {
        Vertex from;
        Vertex to;

        // properties of the path
        String surfaceType;
        double horizontalDistance;  // in m
        double distance3D;          // in m
        double gradePercent;        // 0.0-1.0

        // 2 weights for now
        double travelTime;  // in s
        double fuelUsed;    // in mL

        public Edge(Vertex from, Vertex to) {
            this.from = from;
            this.to = to;

            this.surfaceType = "paved";
            setWeights();
        }

        public Edge(Vertex from, Vertex to, String surfaceType) {
            this.from = from;
            this.to = to;

            this.surfaceType = surfaceType;
            setWeights();
        }

        private void setWeights() { 
            double dx = to.x - from.x;
            double dy = to.y - from.y;
            double dz = to.z - from.z;

            horizontalDistance = Math.sqrt(dx*dx + dy*dy);
            distance3D = Math.sqrt(dx*dx + dy*dy + dz*dz);
            gradePercent = 0.0;
            if (horizontalDistance > 0) {
                gradePercent = (dz / horizontalDistance) * 100.0;
            }
            
            Vehicle.calculate(this);
        }

        public double getCost() {
            return travelTime;
        }

        public double getCost(int costKey) {
            switch (costKey) {
                case 0: return gradePercent;
                case 1: return travelTime; 
                default: return 0;
            }
        }

        public String info() {
            return String.format(
                "Distance: %.2f m%n" +
                "Grade: %.2f%%%n" +
                "Travel Time: %.2f s%n" +
                "Fuel Used: %.2f mL%n",
                distance3D,
                gradePercent,
                travelTime,
                fuelUsed
            );
        }

        @Override
        public String toString() {
            return String.format(
                "%d -> %d | dist=%.2f m | grade=%.2f%%",
                from.id,
                to.id,
                distance3D,
                gradePercent
            );
        }
    }

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        System.out.println("Enter file name: ");
        String fileName = sc.nextLine();

        List<Vertex> vertices = new ArrayList<>();
        List<int[]> faces = new ArrayList<>();

        parsePLY(fileName, vertices, faces);
        
        List<Edge> edges = buildEdges(vertices, faces);

        System.out.println("Unique edges: " + edges.size());

        // Testing djikstra:
        Map<Integer, List<Edge>> graph = buildGraph(edges);


        System.out.print("\nEnter start vertex id: ");
        int startId = sc.nextInt();

        System.out.print("Enter goal vertex id: ");
        int goalId = sc.nextInt();

        List<Integer> path =
                shortestTimePath(
                    vertices.get(startId),
                    vertices.get(goalId),
                    vertices,
                    graph
                );

        System.out.println("\nPath:");

        for (int id : path) {
            System.out.print(id + " ");
        }

        System.out.println();


        exportPathPLY(path, vertices, "least_fuel_path.ply");

        sc.close();
    }

    private static void parsePLY(String fileName, List<Vertex> vertices, List<int[]> faces) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(fileName));
        String line;

        int vertexCount = 0;
        int faceCount = 0;

        while ((line = br.readLine()) != null) {
            if (line.startsWith("element vertex")) {
                vertexCount = Integer.parseInt(line.split("\\s+")[2]);
            } else if (line.startsWith(("element face"))) {
                faceCount = Integer.parseInt(line.split("\\s+")[2]);
            } else if (line.equals("end_header")) {
                break;
            }
        }

        System.out.println(vertexCount + " vertices");
        System.out.println("Reading vertices...");
        for (int i=0; i<vertexCount; i++) {
            line = br.readLine();
            String[] parts = line.trim().split("\\s+");

            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);

            vertices.add(new Vertex(i, x, y, z));
        }

        System.out.println(faceCount + " faces");
        System.out.println("Reading faces...");
        for (int i=0; i<faceCount; i++) {
            line = br.readLine();
            String[] parts = line.trim().split("\\s+");

            int to = Integer.parseInt(parts[1]);
            int from = Integer.parseInt(parts[2]);
            int v3 = Integer.parseInt(parts[3]);

            faces.add(new int[]{to, from, v3});
        }

        br.close();
    }

    private static List<Edge> buildEdges(List<Vertex> vertices, List<int[]> faces) {

        List<Edge> result = new ArrayList<>();
        Set<String> saved = new HashSet<>();

        for (int[] face: faces) {
            addEdge(face[0], face[1], vertices, saved, result);
            addEdge(face[1], face[2], vertices, saved, result);
            addEdge(face[2], face[0], vertices, saved, result);
        }

        return result;
    }

    private static void addEdge(int a, int b, List<Vertex> vertices, Set<String> saved, List<Edge> edges) {
        String k1 = a + " & " + b;
        String k2 = b + " & " + a;

        if (saved.contains(k1) || saved.contains(k2)) {
            return ;
        }

        saved.add(k1);
        saved.add(k2);

        Edge edge = new Edge(vertices.get(a), vertices.get(b));
        edges.add(edge);
    }

    private static Map<Integer, List<Edge>> buildGraph(List<Edge> edges) {
        Map<Integer, List<Edge>> graph = new HashMap<>();

        for (Edge edge : edges) {

            graph.computeIfAbsent(
                edge.from.id,
                k -> new ArrayList<>()
            ).add(edge);

            // Mesh edges are undirected.
            // Create reverse edge.

            Edge reverse =
                new Edge(edge.to, edge.from, edge.surfaceType);

            graph.computeIfAbsent(
                edge.to.id,
                k -> new ArrayList<>()
            ).add(reverse);
        }

        return graph;
    }
    private static List<Integer> shortestTimePath(
            Vertex start,
            Vertex goal,
            List<Vertex> vertices,
            Map<Integer, List<Edge>> graph) {

        int n = vertices.size();

        double[] dist = new double[n];
        int[] parent = new int[n];

        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        Arrays.fill(parent, -1);

        PriorityQueue<Node> pq =
                new PriorityQueue<>();

        dist[start.id] = 0;

        pq.add(new Node(start.id, 0));

        while (!pq.isEmpty()) {

            Node current = pq.poll();

            if (current.cost > dist[current.vertexId]) {
                continue;
            }

            if (current.vertexId == goal.id) {
                break;
            }

            List<Edge> neighbors =
                    graph.getOrDefault(
                            current.vertexId,
                            Collections.emptyList());

            for (Edge edge : neighbors) {

                double newCost =
                        dist[current.vertexId]
                        + edge.fuelUsed;

                if (newCost < dist[edge.to.id]) {

                    dist[edge.to.id] = newCost;
                    parent[edge.to.id] =
                            current.vertexId;

                    pq.add(
                        new Node(
                            edge.to.id,
                            newCost
                        )
                    );
                }
            }
        }

        if (dist[goal.id] ==
                Double.POSITIVE_INFINITY) {

            return Collections.emptyList();
        }

        List<Integer> path =
                new ArrayList<>();

        int cur = goal.id;

        while (cur != -1) {
            path.add(cur);
            cur = parent[cur];
        }

        Collections.reverse(path);

        System.out.printf(
            "%nLeast fuel consumed: %.2f liters%n",
            dist[goal.id]
        );

        return path;
    }

    private static void exportPathXYZ(
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

    private static void exportPathPLY(
            List<Integer> path,
            List<Vertex> vertices,
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

        for (int id : path) {

            Vertex v = vertices.get(id);

            out.printf(
                "%.6f %.6f %.6f 0 0 255%n",
                v.x,
                v.y,
                v.z
            );
        }

        out.close();
    }
}