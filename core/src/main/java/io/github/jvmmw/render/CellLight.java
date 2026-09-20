package io.github.jvmmw.render;

import io.github.jvmmw.esm.EsmObject;

/**
 * One placed light: position, color, radius, flicker. Carry lights can be
 * taken; fixture lights stay.
 */
public final class CellLight {
    public static final int TYPE_NORMAL = 0;
    public static final int TYPE_FLICKER = 1;
    public static final int TYPE_FLICKER_SLOW = 2;
    public static final int TYPE_PULSE = 3;
    public static final int TYPE_PULSE_SLOW = 4;

    public final float[] pos = new float[3];
    public final float[] baseDiffuse = new float[3];
    public final float[] diffuse = new float[3];
    public float radius;
    public float constant;
    public float linear;
    public float quadratic;
    public int type = TYPE_NORMAL;
    public float phase;
    public float brightness = 1f;
    public float ticks;

    public static int typeFromFlags(int flags) {
        int type = TYPE_NORMAL;
        if ((flags & EsmObject.LIGH_FLICKER) != 0) {
            type = TYPE_FLICKER;
        }
        if ((flags & EsmObject.LIGH_FLICKER_SLOW) != 0) {
            type = TYPE_FLICKER_SLOW;
        }
        if ((flags & EsmObject.LIGH_PULSE) != 0) {
            type = TYPE_PULSE;
        }
        if ((flags & EsmObject.LIGH_PULSE_SLOW) != 0) {
            type = TYPE_PULSE_SLOW;
        }
        return type;
    }
}
