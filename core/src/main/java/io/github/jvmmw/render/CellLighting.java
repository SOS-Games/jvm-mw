package io.github.jvmmw.render;

import java.util.ArrayList;
import java.util.List;

/** Interior mood + point lights for one cell. Rewrite of {@code configureAmbient} + LightManager feed. */
public final class CellLighting {
    public final float[] ambient = {0.35f, 0.35f, 0.35f};
    public final float[] sunDiffuse = {1f, 1f, 1f};
    public final float[] sunDir = {0.35f, 0.8f, 0.45f};
    public final List<CellLight> lights = new ArrayList<>();
}
