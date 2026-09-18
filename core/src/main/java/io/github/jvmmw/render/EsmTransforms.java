/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.render;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;

/** Maps to {@code Misc::Convert::makeOsgQuat} for static placement. */
public final class EsmTransforms {
    private static final Quaternion QX = new Quaternion();
    private static final Quaternion QY = new Quaternion();
    private static final Quaternion QZ = new Quaternion();
    private static final Quaternion TMP = new Quaternion();

    private EsmTransforms() {
    }

    public static void setLocal(Matrix4 out, float[] pos, float[] rot, float scale) {
        QX.setFromAxisRad(-1f, 0f, 0f, rot[0]);
        QY.setFromAxisRad(0f, -1f, 0f, rot[1]);
        QZ.setFromAxisRad(0f, 0f, -1f, rot[2]);
        TMP.set(QZ).mul(QY).mul(QX);
        out.idt();
        out.translate(pos[0], pos[1], pos[2]);
        Matrix4 rotM = new Matrix4().set(TMP);
        out.mul(rotM);
        out.scale(scale, scale, scale);
    }
}
