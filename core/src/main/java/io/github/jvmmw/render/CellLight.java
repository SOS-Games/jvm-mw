package io.github.jvmmw.render;

/** One placed TES3 LIGH. Rewrite of {@code SceneUtil::LightSource} / {@code createLightSource}. */
public final class CellLight {
    public final float[] pos = new float[3];
    public final float[] diffuse = new float[3];
    public float radius;
    public float constant;
    public float linear;
    public float quadratic;
}
