
package eu.mihosoft.jcsg.samples;

import eu.mihosoft.jcsg.CSG;
import eu.mihosoft.jcsg.FileUtil;
import eu.mihosoft.jcsg.Polygon;
import eu.mihosoft.jcsg.Vertex;
import eu.mihosoft.vvecmath.Vector3d;
import javafx.geometry.Point3D;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This is the user-provided Mesh class, now with self-contained STL export.
 */
class Mesh {
    public List<Point3D> verts = new ArrayList<>();
    public List<int[]> roofFaces = new ArrayList<>();
    public List<int[]> wallFaces = new ArrayList<>();
    public List<int[]> bottomFaces = new ArrayList<>();

    @Override
    public String toString() {
        return "Mesh{\n" +
                "  verts=" + verts.size() + ",\n" +
                "  totalFaces=" + (roofFaces.size() + wallFaces.size() + bottomFaces.size()) +
                " (roof=" + roofFaces.size() + ", wall=" + wallFaces.size() + ", bottom=" + bottomFaces.size() + ")\n" +
                '}'
    }

    /**
     * Converts this Mesh object into a CSG object.
     * It combines all face lists (roof, wall, bottom) into one geometry.
     *
     * @return a CSG object representing the mesh
     */
    public CSG toCSG() {
        List<Polygon> polygons = new ArrayList<>();

        // Combine all face lists into one for processing.
        List<int[]> allFaces = Stream.of(this.roofFaces, this.wallFaces, this.bottomFaces)
                .flatMap(List::stream)
                .collect(Collectors.toList());

        for (int[] faceIndices : allFaces) {
            List<Vertex> polygonVertices = new ArrayList<>();

            if (faceIndices.length < 3) continue;

            // Calculate the normal for this face using the first three vertices.
            Point3D p1 = this.verts.get(faceIndices[0]);
            Point3D p2 = this.verts.get(faceIndices[1]);
            Point3D p3 = this.verts.get(faceIndices[2]);
            Vector3d normal = UserMeshUnionExample.toVec3d(p2).minus(UserMeshUnionExample.toVec3d(p1)).crossed(UserMeshUnionExample.toVec3d(p3).minus(UserMeshUnionExample.toVec3d(p1))).normalized();

            for (int index : faceIndices) {
                Point3D p = this.verts.get(index);
                polygonVertices.add(new Vertex(UserMeshUnionExample.toVec3d(p), normal));
            }
            polygons.add(new Polygon(polygonVertices));
        }
        return CSG.fromPolygons(polygons);
    }

    /**
     * Saves the current mesh to an STL file.
     * @param filePath the path to save the file to
     * @throws IOException if an I/O error occurs
     */
    public void saveToStl(String filePath) throws IOException {
        System.out.println("Saving mesh to " + filePath + "...");
        // 1. Convert this mesh to a CSG object
        CSG csg = this.toCSG();
        // 2. Convert the CSG to an STL string and write it to a file
        FileUtil.write(Paths.get(filePath), csg.toStlString());
        System.out.println("...Done.");
    }
}

/**
 * An example demonstrating how to convert a user-defined Mesh class to JCSG,
 * perform a union operation, and convert the result back.
 */
public class UserMeshUnionExample {

    /**
     * Converts a CSG object back into a user-defined Mesh object.
     *
     * @param csg the CSG object to convert
     * @return a Mesh object
     */
    public static Mesh csgToMesh(CSG csg) {
        Mesh newMesh = new Mesh();
        Map<Vector3d, Integer> vertexMap = new HashMap<>();

        for (Polygon polygon : csg.getPolygons()) {
            int[] faceIndices = new int[polygon.vertices.size()];
            for (int i = 0; i < polygon.vertices.size(); i++) {
                Vertex vertex = polygon.vertices.get(i);
                Vector3d pos = vertex.pos;

                // Use a map to avoid creating duplicate vertices
                if (vertexMap.containsKey(pos)) {
                    faceIndices[i] = vertexMap.get(pos);
                } else {
                    int newIndex = newMesh.verts.size();
                    vertexMap.put(pos, newIndex);
                    newMesh.verts.add(toPoint3D(pos));
                    faceIndices[i] = newIndex;
                }
            }
            // NOTE: After a CSG operation, the original distinction between roof, wall,
            // and bottom faces is lost. All new polygons are added to the 'wallFaces' list.
            newMesh.wallFaces.add(faceIndices);
        }
        return newMesh;
    }

    public static void main(String[] args) throws IOException {
        // 1. Create two original mesh objects that overlap
        System.out.println("--- Creating Original Meshes ---");
        Mesh mesh1 = createHouseMesh(2, new Point3D(0, 0, 0));
        Mesh mesh2 = createHouseMesh(2, new Point3D(1, 0, 1));
        System.out.println("Mesh 1: " + mesh1);
        System.out.println("Mesh 2: " + mesh2);
        System.out.println();

        // 2. Convert the custom meshes to CSG objects using the new instance method
        System.out.println("--- Processing ---");
        System.out.println("1. Converting to CSG format...");
        CSG csg1 = mesh1.toCSG();
        CSG csg2 = mesh2.toCSG();

        // 3. Perform the union operation
        System.out.println("2. Performing union...");
        CSG unionResultCsg = csg1.union(csg2);

        // 4. Convert the resulting CSG back to our custom mesh format
        System.out.println("3. Converting result back to Mesh format...");
        Mesh finalMesh = csgToMesh(unionResultCsg);
        System.out.println();

        // 5. Print the result and save it to an STL file
        System.out.println("--- Result ---");
        System.out.println("Final combined mesh: " + finalMesh);
        finalMesh.saveToStl("union_result.stl");
    }

    // --- Helper methods for converting types ---

    public static Vector3d toVec3d(Point3D p) { return Vector3d.xyz(p.getX(), p.getY(), p.getZ()); }
    public static Point3D toPoint3D(Vector3d v) { return new Point3D(v.x(), v.y(), v.z()); }

    /**
     * Creates a simple house-like shape to populate all face lists.
     */
    private static Mesh createHouseMesh(double size, Point3D offset) {
        Mesh mesh = new Mesh();
        double s = size / 2.0;
        
        // Cube vertices
        mesh.verts.add(new Point3D(-s + offset.getX(), -s + offset.getY(), -s + offset.getZ())); // 0: bottom-left-front
        mesh.verts.add(new Point3D( s + offset.getX(), -s + offset.getY(), -s + offset.getZ())); // 1: bottom-right-front
        mesh.verts.add(new Point3D( s + offset.getX(), -s + offset.getY(),  s + offset.getZ())); // 2: bottom-right-back
        mesh.verts.add(new Point3D(-s + offset.getX(), -s + offset.getY(),  s + offset.getZ())); // 3: bottom-left-back
        mesh.verts.add(new Point3D(-s + offset.getX(),  s + offset.getY(), -s + offset.getZ())); // 4: top-left-front
        mesh.verts.add(new Point3D( s + offset.getX(),  s + offset.getY(), -s + offset.getZ())); // 5: top-right-front
        mesh.verts.add(new Point3D( s + offset.getX(),  s + offset.getY(),  s + offset.getZ())); // 6: top-right-back
        mesh.verts.add(new Point3D(-s + offset.getX(),  s + offset.getY(),  s + offset.getZ())); // 7: top-left-back
        // Roof peak vertex
        mesh.verts.add(new Point3D(0 + offset.getX(), s + s/2 + offset.getY(), 0 + offset.getZ())); // 8: roof peak

        // Bottom face
        mesh.bottomFaces.add(new int[]{3, 2, 1, 0});

        // Wall faces
        mesh.wallFaces.add(new int[]{0, 1, 5, 4}); // front
        mesh.wallFaces.add(new int[]{1, 2, 6, 5}); // right
        mesh.wallFaces.add(new int[]{2, 3, 7, 6}); // back
        mesh.wallFaces.add(new int[]{3, 0, 4, 7}); // left

        // Roof faces
        mesh.roofFaces.add(new int[]{4, 5, 8});    // front roof triangle
        mesh.roofFaces.add(new int[]{5, 6, 8});    // right roof triangle
        mesh.roofFaces.add(new int[]{6, 7, 8});    // back roof triangle
        mesh.roofFaces.add(new int[]{7, 4, 8});    // left roof triangle

        return mesh;
    }
}
