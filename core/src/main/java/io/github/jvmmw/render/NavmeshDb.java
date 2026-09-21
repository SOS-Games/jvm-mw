package io.github.jvmmw.render;

import io.github.jvmmw.esm.EsmFile;
import io.github.jvmmw.esm.LandRecord;
import io.github.jvmmw.resource.TestData;

import net.jpountz.lz4.LZ4Factory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Reads OpenMW’s navmesh.db (umo / navmeshtool). Tiles stay in a process
 * cache so walking to a new 5×5 only fetches the ring we do not already
 * have. Recast verts are TES X/height/Y under the cell root. Do not commit
 * the database.
 */
public final class NavmeshDb {
    private static final float SCALE = NavmeshBaker.SCALE;
    private static final float LIFT = 6f;
    private static final float TILE_NAV = 128f * 0.2f;
    private static final float BORDER_NAV = 16f * 0.2f;
    private static final byte[] MAGIC = { 'p', 'n', 'a', 'v' };

    private static Connection conn;
    private static boolean tried;

    private NavmeshDb() {
    }

    public static Path dbFile() {
        return TestData.navmeshDb();
    }

    /** Recast tile XY range for TES cells, OpenMW border, extra=0. End indices exclusive. */
    public static int[] recastRange(int gx0, int gy0, int gx1, int gy1) {
        return recastRange(gx0, gy0, gx1, gy1, 0);
    }

    public static float tileSizeTes() {
        return TILE_NAV / SCALE;
    }

    public static synchronized Query queryExterior(int gx0, int gy0, int gx1, int gy1) throws Exception {
        Query q = new Query();
        q.world = "sys::default";
        int[] r = recastRange(gx0, gy0, gx1, gy1, 0);
        q.tx0 = r[0];
        q.tx1 = r[1];
        q.ty0 = r[2];
        q.ty1 = r[3];
        Connection db = open();
        if (db == null) {
            return q;
        }
        fetch(db, q, r);
        return q;
    }

    public static synchronized Query queryInterior(String world) throws Exception {
        Query q = new Query();
        q.world = world;
        Connection db = open();
        if (db == null) {
            return q;
        }
        String sql = """
            SELECT t.tile_position_x, t.tile_position_y, t.data FROM tiles t
             WHERE t.tile_id IN (
               SELECT MAX(tile_id) FROM tiles
                WHERE worldspace = ?
                GROUP BY tile_position_x, tile_position_y
             )
            """;
        try (PreparedStatement st = db.prepareStatement(sql)) {
            st.setString(1, world);
            try (ResultSet rs = st.executeQuery()) {
                ingest(rs, q);
            }
        }
        return q;
    }

    public static final class Query {
        public String world = "";
        public int tx0;
        public int tx1;
        public int ty0;
        public int ty1;
        public int blobs;
        public int decoded;
        public int fail;
        public final List<NavmeshCache.Tile> tiles = new ArrayList<>();
        public final Set<Long> keys = new HashSet<>();
    }

    private static int[] recastRange(int gx0, int gy0, int gx1, int gy1, int extra) {
        float minX = gx0 * (float) LandRecord.CELL_SIZE;
        float maxX = (gx1 + 1) * (float) LandRecord.CELL_SIZE;
        float minY = gy0 * (float) LandRecord.CELL_SIZE;
        float maxY = (gy1 + 1) * (float) LandRecord.CELL_SIZE;
        int tx0 = tileIndex(minX * SCALE - BORDER_NAV) - extra;
        int tx1 = tileIndex(maxX * SCALE + BORDER_NAV) + 1 + extra;
        int ty0 = tileIndex(minY * SCALE - BORDER_NAV) - extra;
        int ty1 = tileIndex(maxY * SCALE + BORDER_NAV) + 1 + extra;
        return new int[] {tx0, tx1, ty0, ty1};
    }

    private static void fetch(Connection db, Query q, int[] r) throws Exception {
        String sql = """
            SELECT t.tile_position_x, t.tile_position_y, t.data FROM tiles t
             WHERE t.tile_id IN (
               SELECT MAX(tile_id) FROM tiles
                WHERE worldspace = ?
                  AND tile_position_x >= ? AND tile_position_x < ?
                  AND tile_position_y >= ? AND tile_position_y < ?
                GROUP BY tile_position_x, tile_position_y
             )
            """;
        try (PreparedStatement st = db.prepareStatement(sql)) {
            st.setString(1, q.world);
            st.setInt(2, r[0]);
            st.setInt(3, r[1]);
            st.setInt(4, r[2]);
            st.setInt(5, r[3]);
            try (ResultSet rs = st.executeQuery()) {
                ingest(rs, q);
            }
        }
    }

    private static void ingest(ResultSet rs, Query q) throws Exception {
        while (rs.next()) {
            int tx = rs.getInt(1);
            int ty = rs.getInt(2);
            q.keys.add(NavmeshCache.key(tx, ty));
            q.blobs++;
            NavmeshCache.Tile tile = new NavmeshCache.Tile();
            tile.world = q.world;
            tile.x = tx;
            tile.y = ty;
            if (fillTile(tile, rs.getBytes(3))) {
                q.tiles.add(tile);
                q.decoded++;
            } else {
                q.fail++;
            }
        }
    }

    static synchronized int loadMissing(EsmFile.LoadedCell cell, java.util.function.LongPredicate have,
        java.util.function.Consumer<NavmeshCache.Tile> out) {
        Connection db = open();
        if (db == null || cell == null) {
            return 0;
        }
        String world = worldspace(cell);
        try {
            if (cell.interior) {
                return loadInterior(db, world, have, out);
            }
            int added = 0;
            if (cell.tiles.isEmpty()) {
                added += loadExterior(db, world, cell.gridX, cell.gridY, cell.gridX, cell.gridY, have, out);
            } else {
                for (EsmFile.GridTile tile : cell.tiles) {
                    added += loadExterior(db, world, tile.gridX, tile.gridY, tile.gridX, tile.gridY, have, out);
                }
            }
            return added;
        } catch (Exception e) {
            return 0;
        }
    }

    static String worldspace(EsmFile.LoadedCell cell) {
        if (cell.interior) {
            return cell.name.toLowerCase(Locale.ROOT);
        }
        return "sys::default";
    }

    private static int loadInterior(Connection db, String world, java.util.function.LongPredicate have,
        java.util.function.Consumer<NavmeshCache.Tile> out) throws Exception {
        String sql = """
            SELECT t.tile_position_x, t.tile_position_y, t.data FROM tiles t
             WHERE t.tile_id IN (
               SELECT MAX(tile_id) FROM tiles
                WHERE worldspace = ?
                GROUP BY tile_position_x, tile_position_y
             )
            """;
        int added = 0;
        try (PreparedStatement st = db.prepareStatement(sql)) {
            st.setString(1, world);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    added += accept(rs.getInt(1), rs.getInt(2), rs.getBytes(3), have, out);
                }
            }
        }
        return added;
    }

    private static int loadExterior(Connection db, String world, int gx0, int gy0, int gx1, int gy1,
        java.util.function.LongPredicate have, java.util.function.Consumer<NavmeshCache.Tile> out) throws Exception {
        int[] r = recastRange(gx0, gy0, gx1, gy1, 1);
        if (rangeHave(r[0], r[1], r[2], r[3], have)) {
            return 0;
        }
        Query q = new Query();
        q.world = world;
        fetch(db, q, r);
        int added = 0;
        for (NavmeshCache.Tile tile : q.tiles) {
            if (have.test(NavmeshCache.key(tile.x, tile.y))) {
                continue;
            }
            out.accept(tile);
            added++;
        }
        markRange(world, r[0], r[1], r[2], r[3]);
        return added;
    }

    private static boolean rangeHave(int tx0, int tx1, int ty0, int ty1, java.util.function.LongPredicate have) {
        for (int tx = tx0; tx < tx1; tx++) {
            for (int ty = ty0; ty < ty1; ty++) {
                if (!have.test(NavmeshCache.key(tx, ty))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void markRange(String world, int tx0, int tx1, int ty0, int ty1) {
        for (int tx = tx0; tx < tx1; tx++) {
            for (int ty = ty0; ty < ty1; ty++) {
                NavmeshCache.markSeen(world, tx, ty);
            }
        }
    }

    private static int accept(int tx, int ty, byte[] blob, java.util.function.LongPredicate have,
        java.util.function.Consumer<NavmeshCache.Tile> out) {
        if (have.test(NavmeshCache.key(tx, ty))) {
            return 0;
        }
        NavmeshCache.Tile tile = new NavmeshCache.Tile();
        tile.x = tx;
        tile.y = ty;
        if (!fillTile(tile, blob)) {
            return 0;
        }
        out.accept(tile);
        return 1;
    }

    private static int tileIndex(float nav) {
        return (int) Math.floor(nav / TILE_NAV);
    }

    private static boolean fillTile(NavmeshCache.Tile out, byte[] blob) {
        if (blob == null || blob.length < 12) {
            return false;
        }
        byte[] raw = lz4(blob);
        if (raw == null) {
            return false;
        }
        ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        if (b.remaining() < 8) {
            return false;
        }
        for (byte m : MAGIC) {
            if (b.get() != m) {
                return false;
            }
        }
        if (b.getInt() != 1) {
            return false;
        }
        b.getInt();
        b.getFloat();
        b.getFloat();
        int npolysMesh = skipPolyMesh(b);
        if (npolysMesh < 0) {
            return false;
        }
        if (b.remaining() < 4) {
            return false;
        }
        int nmeshes = b.getInt();
        if (nmeshes < 0 || nmeshes > 100_000 || b.remaining() < nmeshes * 16L + 4) {
            return false;
        }
        int[] meshes = new int[nmeshes * 4];
        for (int i = 0; i < meshes.length; i++) {
            meshes[i] = b.getInt();
        }
        int nverts = b.getInt();
        if (nverts < 0 || nverts > 1_000_000 || b.remaining() < nverts * 12L + 4) {
            return false;
        }
        float[] verts = new float[nverts * 3];
        for (int i = 0; i < verts.length; i++) {
            verts[i] = b.getFloat();
        }
        int ntris = b.getInt();
        if (ntris < 0 || ntris > 1_000_000 || b.remaining() < ntris * 4L) {
            return false;
        }
        int[] tris = new int[ntris * 4];
        for (int i = 0; i < tris.length; i++) {
            tris[i] = b.get() & 0xff;
        }
        float inv = 1f / SCALE;
        int added = 0;
        for (int m = 0; m < nmeshes; m++) {
            int vertBase = meshes[m * 4];
            int triBase = meshes[m * 4 + 2];
            int triCount = meshes[m * 4 + 3];
            for (int t = 0; t < triCount; t++) {
                int to = (triBase + t) * 4;
                float[] tri = new float[9];
                for (int k = 0; k < 3; k++) {
                    int vi = vertBase + tris[to + k];
                    if (vi < 0 || vi >= nverts) {
                        return false;
                    }
                    float tesX = verts[vi * 3] * inv;
                    float tesZ = verts[vi * 3 + 1] * inv;
                    float tesY = verts[vi * 3 + 2] * inv;
                    int o = k * 3;
                    tri[o] = tesX;
                    tri[o + 1] = tesY;
                    tri[o + 2] = tesZ + LIFT;
                }
                out.tris.add(tri);
                added++;
            }
        }
        if (added <= 0) {
            return false;
        }
        out.polys = npolysMesh > 0 ? npolysMesh : 1;
        return true;
    }

    private static int skipPolyMesh(ByteBuffer b) {
        if (b.remaining() < 4 * 4 + 12 + 12 + 4 + 4 + 4 + 4) {
            return -1;
        }
        int nverts = b.getInt();
        int npolys = b.getInt();
        int maxpolys = b.getInt();
        int nvp = b.getInt();
        if (nverts < 0 || npolys < 0 || maxpolys < 0 || nvp < 0) {
            return -1;
        }
        b.position(b.position() + 24);
        b.getFloat();
        b.getFloat();
        b.getInt();
        b.getFloat();
        long bytes = 2L * 3 * nverts + 2L * 2 * (long) maxpolys * nvp + 2L * maxpolys + 2L * npolys + maxpolys;
        if (nverts > 1_000_000 || maxpolys > 1_000_000 || bytes > b.remaining()) {
            return -1;
        }
        b.position(b.position() + (int) bytes);
        return npolys;
    }

    private static byte[] lz4(byte[] blob) {
        if (blob.length < 8) {
            return null;
        }
        ByteBuffer hdr = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
        long orig = hdr.getLong();
        if (orig <= 0 || orig > 16_000_000) {
            return null;
        }
        byte[] dest = new byte[(int) orig];
        try {
            LZ4Factory.fastestInstance().safeDecompressor().decompress(blob, 8, blob.length - 8, dest, 0, dest.length);
            return dest;
        } catch (Exception e) {
            return null;
        }
    }

    private static Connection open() {
        if (tried) {
            return conn;
        }
        tried = true;
        Path path = TestData.navmeshDb();
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            Class.forName("org.sqlite.JDBC");
            Properties p = new Properties();
            p.setProperty("open_mode", "1");
            String url = "jdbc:sqlite:" + path.toAbsolutePath().toString().replace('\\', '/');
            conn = DriverManager.getConnection(url, p);
            return conn;
        } catch (Exception e) {
            conn = null;
            return null;
        }
    }
}
