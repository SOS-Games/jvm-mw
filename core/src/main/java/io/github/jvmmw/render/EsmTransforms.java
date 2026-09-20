/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.render;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;

/**
 * Places a cell-ref: move to its ESM position, then yaw / pitch / roll,
 * then scale. Yaw first (around −Z), then −Y, then −X. libGDX quaternion
 * multiply is the reverse of OSG’s, so the product is QX*QY*QZ here.
 * Copying OSG’s QZ*QY*QX token-for-token yaws after a 180° X, which
 * turns Zainsipilu halls around so they face backwards.
 */
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
        // OpenMW: osg::Quat(z,-Z) * osg::Quat(y,-Y) * osg::Quat(x,-X). OSG's *
        // applies left-to-right. libGDX mul is Hamilton (rightmost first).
        TMP.set(QX).mul(QY).mul(QZ);
        out.idt();
        out.translate(pos[0], pos[1], pos[2]);
        Matrix4 rotM = new Matrix4().set(TMP);
        out.mul(rotM);
        out.scale(scale, scale, scale);
    }

    /** Actors: yaw-only {@code makeActorOsgQuat} plus non-uniform race scale. */
    public static void setActorLocal(Matrix4 out, float[] pos, float yaw, float sx, float sy, float sz) {
        QZ.setFromAxisRad(0f, 0f, -1f, yaw);
        out.idt();
        out.translate(pos[0], pos[1], pos[2]);
        out.mul(new Matrix4().set(QZ));
        out.scale(sx, sy, sz);
    }
}
