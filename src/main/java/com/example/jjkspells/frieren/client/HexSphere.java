package com.example.jjkspells.frieren.client;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A real hexagonal tiling of the unit sphere (a Goldberg-style polyhedron: 12 pentagons, the rest hexagons), built once.
 * Take an icosahedron, subdivide every face F times, and make each vertex of that mesh a cell whose corners are the
 * (normalised) centres of the triangles around it.
 */
public final class HexSphere {
    public static final int F = 5;
    /** Corner points of the cells (unit vectors). */
    public static final Vec3[] NODES;
    /** Cell centres (unit vectors). */
    public static final Vec3[] CENTERS;
    /** CELLS[i] = indices into NODES of cell i's corners, in order around the cell. */
    public static final int[][] CELLS;
    /** Unique edges as pairs of NODES indices (shared edges listed once). */
    public static final int[][] EDGES;

    static {
        double t = (1 + Math.sqrt(5)) / 2;
        double[][] iv = {{-1, t, 0}, {1, t, 0}, {-1, -t, 0}, {1, -t, 0}, {0, -1, t}, {0, 1, t}, {0, -1, -t}, {0, 1, -t}, {t, 0, -1}, {t, 0, 1}, {-t, 0, -1}, {-t, 0, 1}};
        int[][] fc = {{0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11}, {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
                {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9}, {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}};
        List<Vec3> verts = new ArrayList<>();
        Map<String, Integer> index = new HashMap<>();
        List<int[]> tris = new ArrayList<>();
        for (int[] f : fc) {
            Vec3 a = new Vec3(iv[f[0]][0], iv[f[0]][1], iv[f[0]][2]);
            Vec3 b = new Vec3(iv[f[1]][0], iv[f[1]][1], iv[f[1]][2]);
            Vec3 c = new Vec3(iv[f[2]][0], iv[f[2]][1], iv[f[2]][2]);
            int[][] g = new int[F + 1][F + 1];
            for (int i = 0; i <= F; i++) {
                for (int j = 0; j <= F - i; j++) {
                    Vec3 p = a.scale((double) (F - i - j) / F).add(b.scale((double) i / F)).add(c.scale((double) j / F)).normalize();
                    String key = Math.round(p.x * 1.0E4) + "_" + Math.round(p.y * 1.0E4) + "_" + Math.round(p.z * 1.0E4);
                    Integer id = index.get(key);
                    if (id == null) {
                        id = verts.size();
                        verts.add(p);
                        index.put(key, id);
                    }
                    g[i][j] = id;
                }
            }
            for (int i = 0; i < F; i++) {
                for (int j = 0; j < F - i; j++) {
                    tris.add(new int[]{g[i][j], g[i + 1][j], g[i][j + 1]});
                    if (i + j < F - 1) tris.add(new int[]{g[i + 1][j], g[i + 1][j + 1], g[i][j + 1]});
                }
            }
        }
        Vec3[] nodes = new Vec3[tris.size()];
        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < verts.size(); i++) adj.add(new ArrayList<>());
        for (int i = 0; i < tris.size(); i++) {
            int[] tr = tris.get(i);
            nodes[i] = verts.get(tr[0]).add(verts.get(tr[1])).add(verts.get(tr[2])).normalize();
            for (int v : tr) adj.get(v).add(i);
        }
        int[][] cells = new int[verts.size()][];
        Vec3[] centers = new Vec3[verts.size()];
        Set<Long> edgeKeys = new HashSet<>();
        List<int[]> edges = new ArrayList<>();
        for (int v = 0; v < verts.size(); v++) {
            Vec3 n = verts.get(v);
            Vec3 ref = Math.abs(n.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            Vec3 u = n.cross(ref).normalize();
            Vec3 w = n.cross(u);
            List<Integer> list = adj.get(v);
            list.sort(Comparator.comparingDouble(i -> Math.atan2(nodes[i].dot(w), nodes[i].dot(u))));
            int[] cell = new int[list.size()];
            for (int k = 0; k < cell.length; k++) cell[k] = list.get(k);
            cells[v] = cell;
            centers[v] = n;
            for (int k = 0; k < cell.length; k++) {
                int a = cell[k], b = cell[(k + 1) % cell.length];
                long key = ((long) Math.min(a, b) << 24) | Math.max(a, b);
                if (edgeKeys.add(key)) edges.add(new int[]{a, b});
            }
        }
        NODES = nodes;
        CENTERS = centers;
        CELLS = cells;
        EDGES = edges.toArray(new int[0][]);
    }

    private HexSphere() {
    }
}
