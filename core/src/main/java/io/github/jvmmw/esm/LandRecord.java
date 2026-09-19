/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.esm;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** TES3 {@code ESM::Land} heightfield. Rewrite of {@code LandRecordData} VHGT / VTEX. */
public final class LandRecord {
    public static final int SIZE = 65;
    public static final int NUM_VERTS = SIZE * SIZE;
    public static final int CELL_SIZE = 8192;
    public static final int HEIGHT_SCALE = 8;
    public static final float DEFAULT_HEIGHT = -2048f;
    public static final int TEXTURE_SIZE = 16;
    public static final int NUM_TEXTURES = TEXTURE_SIZE * TEXTURE_SIZE;

    public int gridX;
    public int gridY;
    public final float[] heights = new float[NUM_VERTS];
    public final int[] textures = new int[NUM_TEXTURES];
    public float minHeight = DEFAULT_HEIGHT;
    public float maxHeight = DEFAULT_HEIGHT;

    public static LandRecord flat(int gridX, int gridY) {
        LandRecord land = new LandRecord();
        land.gridX = gridX;
        land.gridY = gridY;
        Arrays.fill(land.heights, DEFAULT_HEIGHT);
        return land;
    }

    public static int cellGrid(float tes) {
        return (int) Math.floor(tes / CELL_SIZE);
    }

    public float height(int x, int y) {
        return heights[x + y * SIZE];
    }

    public int texture(int tx, int ty) {
        return textures[tx + ty * TEXTURE_SIZE];
    }

    public int uniqueVtex() {
        Set<Integer> ids = new HashSet<>();
        for (int t : textures) {
            ids.add(t);
        }
        return ids.size();
    }
}
