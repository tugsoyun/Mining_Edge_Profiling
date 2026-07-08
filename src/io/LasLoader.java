package src.io;

import src.model.Vertex;

import com.github.mreutegg.laszip4j.LASReader;
import com.github.mreutegg.laszip4j.LASHeader;
import com.github.mreutegg.laszip4j.LASPoint;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads point clouds from LAS or LAZ files using laszip4j — a pure-Java
 * port of LASzip, so no PDAL install or native binaries are required.
 * Handles both compressed (.laz) and uncompressed (.las) transparently.
 *
 * <p><b>Important:</b> LAS stores X/Y/Z as scaled integers to save space.
 * {@link LASPoint#getX()} etc. return the *raw* integer record value —
 * the real-world coordinate is {@code raw * scaleFactor + offset}, with
 * scale/offset coming from the file header. Skipping this step silently
 * produces points in the wrong coordinate space (usually wildly wrong,
 * but sometimes just "off" in a way that's easy to miss until you overlay
 * outputs in a viewer like CloudCompare).</p>
 */
public class LasLoader {

    private LasLoader() {}

    public static List<Vertex> load(String filePath) {
        LASReader reader = new LASReader(new File(filePath));
        LASHeader header = reader.getHeader();

        double xScale = header.getXScaleFactor();
        double yScale = header.getYScaleFactor();
        double zScale = header.getZScaleFactor();
        double xOffset = header.getXOffset();
        double yOffset = header.getYOffset();
        double zOffset = header.getZOffset();

        List<Vertex> points = new ArrayList<>();
        int id = 0;
        for (LASPoint p : reader.getPoints()) {
            double x = p.getX() * xScale + xOffset;
            double y = p.getY() * yScale + yOffset;
            double z = p.getZ() * zScale + zOffset;
            points.add(new Vertex(id++, x, y, z));
        }

        System.out.printf("LasLoader: read %d points from %s%n", points.size(), filePath);
        System.out.printf("  scale=(%.6f, %.6f, %.6f) offset=(%.2f, %.2f, %.2f)%n",
                xScale, yScale, zScale, xOffset, yOffset, zOffset);
        return points;
    }
}