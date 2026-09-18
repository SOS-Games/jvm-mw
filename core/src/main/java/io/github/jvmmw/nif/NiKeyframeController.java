/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.nif;

/** Maps to {@code Nif::NiKeyframeController}. */
public final class NiKeyframeController extends NifRecord {
    public static final int EXTRA_CYCLE = 0;
    public static final int EXTRA_REVERSE = 2;
    public static final int EXTRA_CONSTANT = 4;
    public static final int EXTRA_MASK = 6;

    public int next = -1;
    public int flags;
    public float frequency = 1f;
    public float phase;
    public float timeStart;
    public float timeStop;
    public int target = -1;
    public int data = -1;

    public int extrapolation() {
        return flags & EXTRA_MASK;
    }

    public float sampleTime(float value) {
        float time = frequency * value + phase;
        if (time >= timeStart && time <= timeStop) {
            return time;
        }
        int mode = extrapolation();
        if (mode == EXTRA_CYCLE || mode == EXTRA_REVERSE) {
            float delta = timeStop - timeStart;
            if (delta <= 0f) {
                return timeStart;
            }
            float cycles = (time - timeStart) / delta;
            float remainder = (cycles - (float) Math.floor(cycles)) * delta;
            if (mode == EXTRA_CYCLE) {
                return timeStart + remainder;
            }
            if (((int) Math.abs(Math.floor(cycles)) % 2) == 0) {
                return timeStart + remainder;
            }
            return timeStop - remainder;
        }
        if (time < timeStart) {
            return timeStart;
        }
        if (time > timeStop) {
            return timeStop;
        }
        return time;
    }
}
