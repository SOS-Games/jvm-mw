/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.nif;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/** Maps to {@code Nif::NiTransform} / {@code Nif::Matrix3}. */
public final class NiTransform {
    public final float[] rotation = {
        1, 0, 0,
        0, 1, 0,
        0, 0, 1
    };
    public final Vector3 translation = new Vector3();
    public float scale = 1f;

    public void identity() {
        for (int i = 0; i < 9; i++) {
            rotation[i] = (i % 4 == 0) ? 1f : 0f;
        }
        translation.set(0, 0, 0);
        scale = 1f;
    }

    public void toMatrix(Matrix4 out) {
        out.idt();
        // OpenMW NiTransform::toMatrix: osg(j,i) = mRotation.mValues[i][j] * mScale
        float s = scale;
        out.val[Matrix4.M00] = rotation[0] * s;
        out.val[Matrix4.M10] = rotation[1] * s;
        out.val[Matrix4.M20] = rotation[2] * s;
        out.val[Matrix4.M01] = rotation[3] * s;
        out.val[Matrix4.M11] = rotation[4] * s;
        out.val[Matrix4.M21] = rotation[5] * s;
        out.val[Matrix4.M02] = rotation[6] * s;
        out.val[Matrix4.M12] = rotation[7] * s;
        out.val[Matrix4.M22] = rotation[8] * s;
        out.val[Matrix4.M03] = translation.x;
        out.val[Matrix4.M13] = translation.y;
        out.val[Matrix4.M23] = translation.z;
        out.val[Matrix4.M30] = 0;
        out.val[Matrix4.M31] = 0;
        out.val[Matrix4.M32] = 0;
        out.val[Matrix4.M33] = 1;
    }

    /** OpenMW {@code NiAVObject}: translation, then 3x3 rotation, then scale. */
    public void read(NifStream nif) {
        translation.set(nif.getF32(), nif.getF32(), nif.getF32());
        for (int i = 0; i < 9; i++) {
            rotation[i] = nif.getF32();
        }
        scale = nif.getF32();
    }
}
