package io.github.jvmmw.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Interior mood + point lights for one cell. Rewrite of {@code configureAmbient} + LightManager feed. */
public final class CellLighting {
    public static final float VIEW_DISTANCE = 7168f;
    /** {@code Water_UnderwaterDayFog}. */
    public static final float UNDERWATER_DAY_FOG = 2.5f;
    /** {@code Water_UnderwaterColorWeight}. */
    public static final float UNDERWATER_WEIGHT = 0.85f;
    /** {@code Water_UnderwaterColor} 012,030,037 / 255. */
    public static final float[] UNDERWATER_COLOR = {12f / 255f, 30f / 255f, 37f / 255f};

    public final float[] ambient = {0.35f, 0.35f, 0.35f};
    public final float[] sunDiffuse = {1f, 1f, 1f};
    public final float[] sunDir = {0.35f, 0.8f, 0.45f};
    public final float[] fogColor = {0.08f, 0.09f, 0.12f};
    public boolean exterior;
    public float fogDensity;
    public boolean fogEnabled;
    public float fogStart;
    public float fogEnd = VIEW_DISTANCE;
    public float fogScale;
    public final List<CellLight> lights = new ArrayList<>();
    private final Random rng = new Random();
    private float simTime;
    private float startTime;
    private float lastTime;

    public void resetTime() {
        simTime = 0f;
        startTime = 0f;
        lastTime = 0f;
    }

    public float rollPhase() {
        return 0.25f + rng.nextFloat() * 0.75f;
    }

    public void configureFog(float[] color, float density) {
        System.arraycopy(color, 0, fogColor, 0, 3);
        fogDensity = density;
        if (density == 0f) {
            fogEnabled = false;
            fogStart = 0f;
            fogEnd = Float.MAX_VALUE;
            fogScale = 0f;
        } else {
            fogEnabled = true;
            fogStart = VIEW_DISTANCE * (1f - density);
            fogEnd = VIEW_DISTANCE;
            fogScale = 1f / (fogEnd - fogStart);
        }
    }

    public static float underwaterFogEnd() {
        return Math.min(VIEW_DISTANCE, 7168f);
    }

    public static float underwaterFogStart() {
        return underwaterFogEnd() * (1f - UNDERWATER_DAY_FOG);
    }

    public static float underwaterFogScale() {
        return 1f / (underwaterFogEnd() - underwaterFogStart());
    }

    public void underwaterFogColor(float[] out) {
        float w = UNDERWATER_WEIGHT;
        out[0] = UNDERWATER_COLOR[0] * w + fogColor[0] * (1f - w);
        out[1] = UNDERWATER_COLOR[1] * w + fogColor[1] * (1f - w);
        out[2] = UNDERWATER_COLOR[2] * w + fogColor[2] * (1f - w);
    }

    public void updateFlicker(float dt) {
        simTime += dt;
        if (startTime == 0f) {
            startTime = simTime;
        }
        float ticksAdvance = (simTime - startTime - lastTime) * 15f * 0.25f;
        lastTime = simTime - startTime;
        for (CellLight light : lights) {
            if (light.type == CellLight.TYPE_NORMAL) {
                System.arraycopy(light.baseDiffuse, 0, light.diffuse, 0, 3);
                continue;
            }
            light.ticks = ticksAdvance + light.ticks * 0.75f;
            float speed = (light.type == CellLight.TYPE_FLICKER || light.type == CellLight.TYPE_PULSE) ? 0.1f : 0.05f;
            if (light.brightness >= light.phase) {
                light.brightness -= light.ticks * speed;
            } else {
                light.brightness += light.ticks * speed;
            }
            if (Math.abs(light.brightness - light.phase) < speed) {
                if (light.type == CellLight.TYPE_FLICKER || light.type == CellLight.TYPE_FLICKER_SLOW) {
                    light.phase = 0.25f + rng.nextFloat() * 0.75f;
                } else {
                    light.phase = light.phase <= 0.5f ? 1f : 0.25f;
                }
            }
            float result = light.brightness;
            light.diffuse[0] = light.baseDiffuse[0] * result;
            light.diffuse[1] = light.baseDiffuse[1] * result;
            light.diffuse[2] = light.baseDiffuse[2] * result;
        }
    }
}
