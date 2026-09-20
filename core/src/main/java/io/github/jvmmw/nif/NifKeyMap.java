/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.nif;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

/**
 * Timed keys on an animation track (move, rotate, scale). Most Morrowind
 * idle clips are linear. Quaternion keys store WXYZ in four floats.
 */
public final class NifKeyMap {
    public static final int LINEAR = 1;
    public static final int QUADRATIC = 2;
    public static final int TCB = 3;
    public static final int XYZ = 4;
    public static final int CONSTANT = 5;

    public int interpolation;
    public float[] times = new float[0];
    public float[] values = new float[0];
    public float[] inTan = new float[0];
    public float[] outTan = new float[0];
    public int stride;

    public boolean empty() {
        return times.length == 0;
    }

    public static NifKeyMap read(NifStream nif, int valueFloats, boolean quat) {
        NifKeyMap map = new NifKeyMap();
        map.stride = valueFloats;
        int count = nif.getI32();
        if (count == 0) {
            return map;
        }
        map.interpolation = nif.getI32();
        if (map.interpolation == XYZ) {
            return map;
        }
        boolean tangents = !quat && map.interpolation == QUADRATIC;
        boolean tcb = map.interpolation == TCB;
        if (count != 0 && map.interpolation != LINEAR && map.interpolation != QUADRATIC
            && map.interpolation != TCB && map.interpolation != CONSTANT && map.interpolation != 0) {
            throw new IllegalArgumentException("Unhandled interpolation type " + map.interpolation);
        }
        map.times = new float[count];
        map.values = new float[count * valueFloats];
        if (tangents || tcb) {
            map.inTan = new float[count * valueFloats];
            map.outTan = new float[count * valueFloats];
        }
        float[] tcbA = tcb ? new float[count] : null;
        float[] tcbB = tcb ? new float[count] : null;
        float[] tcbC = tcb ? new float[count] : null;
        float[] tcbD = tcb ? new float[count] : null;
        for (int i = 0; i < count; i++) {
            map.times[i] = nif.getF32();
            int o = i * valueFloats;
            for (int k = 0; k < valueFloats; k++) {
                map.values[o + k] = nif.getF32();
            }
            if (tangents) {
                for (int k = 0; k < valueFloats; k++) {
                    map.inTan[o + k] = nif.getF32();
                }
                for (int k = 0; k < valueFloats; k++) {
                    map.outTan[o + k] = nif.getF32();
                }
            }
            if (tcb) {
                float tension = nif.getF32();
                float continuity = nif.getF32();
                float bias = nif.getF32();
                tcbA[i] = (1f - tension) * (1f - continuity) * (1f + bias);
                tcbB[i] = (1f - tension) * (1f + continuity) * (1f - bias);
                tcbC[i] = (1f - tension) * (1f + continuity) * (1f + bias);
                tcbD[i] = (1f - tension) * (1f - continuity) * (1f - bias);
            }
        }
        if (tcb && !quat && count > 1) {
            generateTcbTangents(map, tcbA, tcbB, tcbC, tcbD);
        }
        return map;
    }

    public float interpFloat(float time) {
        float[] out = new float[1];
        interp(time, out);
        return out[0];
    }

    public void interpVec3(float time, Vector3 out) {
        float[] v = new float[3];
        interp(time, v);
        out.set(v[0], v[1], v[2]);
    }

    public void interpQuat(float time, Quaternion out) {
        if (empty()) {
            out.idt();
            return;
        }
        if (time <= times[0]) {
            setQuat(out, 0);
            return;
        }
        int hi = upperIndex(time);
        if (hi >= times.length) {
            setQuat(out, times.length - 1);
            return;
        }
        int lo = hi - 1;
        if (times[hi] == times[lo]) {
            setQuat(out, lo);
            return;
        }
        float a = (time - times[lo]) / (times[hi] - times[lo]);
        if (interpolation == CONSTANT) {
            setQuat(out, a > 0.5f ? hi : lo);
            return;
        }
        Quaternion qb = new Quaternion();
        setQuat(out, lo);
        setQuat(qb, hi);
        out.slerp(qb, a);
    }

    private void interp(float time, float[] out) {
        int s = stride;
        if (empty()) {
            for (int k = 0; k < s; k++) {
                out[k] = 0;
            }
            return;
        }
        if (time <= times[0]) {
            System.arraycopy(values, 0, out, 0, s);
            return;
        }
        int hi = upperIndex(time);
        if (hi >= times.length) {
            System.arraycopy(values, (times.length - 1) * s, out, 0, s);
            return;
        }
        int lo = hi - 1;
        if (times[hi] == times[lo]) {
            System.arraycopy(values, lo * s, out, 0, s);
            return;
        }
        float t = (time - times[lo]) / (times[hi] - times[lo]);
        if (interpolation == CONSTANT) {
            System.arraycopy(values, (t > 0.5f ? hi : lo) * s, out, 0, s);
            return;
        }
        if ((interpolation == QUADRATIC || interpolation == TCB) && inTan.length == values.length) {
            float t2 = t * t;
            float t3 = t2 * t;
            float b1 = 2f * t3 - 3f * t2 + 1f;
            float b2 = -2f * t3 + 3f * t2;
            float b3 = t3 - 2f * t2 + t;
            float b4 = t3 - t2;
            int loO = lo * s;
            int hiO = hi * s;
            for (int k = 0; k < s; k++) {
                out[k] = values[loO + k] * b1 + values[hiO + k] * b2
                    + outTan[loO + k] * b3 + inTan[hiO + k] * b4;
            }
            return;
        }
        int loO = lo * s;
        int hiO = hi * s;
        for (int k = 0; k < s; k++) {
            out[k] = values[loO + k] + (values[hiO + k] - values[loO + k]) * t;
        }
    }

    private int upperIndex(float time) {
        int lo = 0;
        int hi = times.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (times[mid] < time) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private void setQuat(Quaternion q, int i) {
        int o = i * 4;
        q.set(values[o + 1], values[o + 2], values[o + 3], values[o]);
    }

    private static void generateTcbTangents(NifKeyMap map, float[] a, float[] b, float[] c, float[] d) {
        int n = map.times.length;
        int s = map.stride;
        float[] delta = new float[s];
        for (int k = 0; k < s; k++) {
            delta[k] = map.values[s + k] - map.values[k];
            map.inTan[k] = delta[k] * ((a[0] + b[0]) * 0.5f);
            map.outTan[k] = delta[k] * ((c[0] + d[0]) * 0.5f);
        }
        for (int i = 1; i < n - 1; i++) {
            float span = map.times[i + 1] - map.times[i - 1];
            if (span == 0f) {
                continue;
            }
            int o = i * s;
            int p = (i - 1) * s;
            int nx = (i + 1) * s;
            float inF = (map.times[i] - map.times[i - 1]) / span;
            float outF = (map.times[i + 1] - map.times[i]) / span;
            for (int k = 0; k < s; k++) {
                float prev = map.values[o + k] - map.values[p + k];
                float next = map.values[nx + k] - map.values[o + k];
                map.inTan[o + k] = (prev * a[i] + next * b[i]) * inF;
                map.outTan[o + k] = (prev * c[i] + next * d[i]) * outF;
            }
        }
        int last = (n - 1) * s;
        int prev = (n - 2) * s;
        for (int k = 0; k < s; k++) {
            float dlt = map.values[last + k] - map.values[prev + k];
            map.inTan[last + k] = dlt * ((a[n - 1] + b[n - 1]) * 0.5f);
            map.outTan[last + k] = dlt * ((c[n - 1] + d[n - 1]) * 0.5f);
        }
    }
}
