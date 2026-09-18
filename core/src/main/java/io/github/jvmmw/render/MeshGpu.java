package io.github.jvmmw.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public final class MeshGpu {
    public static final int COLOR_NONE = 0;
    public static final int COLOR_EMISSION = 1;
    public static final int COLOR_AMB_DIFF = 2;

    public final int vao;
    public final int vbo;
    public final int ebo;
    public final int indexCount;

    public int baseTex;
    public int darkTex;
    public int detailTex;
    public int glowTex;
    public int baseWrapS = GL20.GL_REPEAT;
    public int baseWrapT = GL20.GL_REPEAT;
    public int darkWrapS = GL20.GL_REPEAT;
    public int darkWrapT = GL20.GL_REPEAT;
    public int detailWrapS = GL20.GL_REPEAT;
    public int detailWrapT = GL20.GL_REPEAT;
    public int glowWrapS = GL20.GL_REPEAT;
    public int glowWrapT = GL20.GL_REPEAT;
    public boolean useDark;
    public boolean useDetail;
    public boolean useGlow;

    public final float[] ambient = {1, 1, 1};
    public final float[] diffuse = {1, 1, 1};
    public final float[] emissive = {0, 0, 0};
    public float matAlpha = 1f;
    public int colorMode = COLOR_NONE;

    public boolean alphaBlend;
    public boolean alphaTest;
    public boolean noSorter;
    public int blendSrc = GL20.GL_SRC_ALPHA;
    public int blendDst = GL20.GL_ONE_MINUS_SRC_ALPHA;
    public int alphaFunc = 3;
    public float alphaRef = 0.5f;
    public boolean depthTest = true;
    public boolean depthWrite = true;
    public boolean cull = true;

    public int textureId;

    public final float[] localMin = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
    public final float[] localMax = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
    private static final Vector3 corner = new Vector3();
    static final int STRIDE_FLOATS = 12;

    public MeshGpu(float[] interleaved, short[] indices) {
        indexCount = indices.length;
        for (int i = 0; i + 2 < interleaved.length; i += STRIDE_FLOATS) {
            localMin[0] = Math.min(localMin[0], interleaved[i]);
            localMin[1] = Math.min(localMin[1], interleaved[i + 1]);
            localMin[2] = Math.min(localMin[2], interleaved[i + 2]);
            localMax[0] = Math.max(localMax[0], interleaved[i]);
            localMax[1] = Math.max(localMax[1], interleaved[i + 1]);
            localMax[2] = Math.max(localMax[2], interleaved[i + 2]);
        }
        FloatBuffer fb = ByteBuffer.allocateDirect(interleaved.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(interleaved).flip();
        ShortBuffer sb = ByteBuffer.allocateDirect(indices.length * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
        sb.put(indices).flip();

        IntBuffer ids = BufferUtils.newIntBuffer(1);
        Gdx.gl30.glGenVertexArrays(1, ids);
        vao = ids.get(0);
        ids.clear();
        Gdx.gl.glGenBuffers(1, ids);
        vbo = ids.get(0);
        ids.clear();
        Gdx.gl.glGenBuffers(1, ids);
        ebo = ids.get(0);

        Gdx.gl30.glBindVertexArray(vao);
        Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, vbo);
        Gdx.gl.glBufferData(GL20.GL_ARRAY_BUFFER, interleaved.length * 4, fb, GL20.GL_STATIC_DRAW);
        Gdx.gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, ebo);
        Gdx.gl.glBufferData(GL20.GL_ELEMENT_ARRAY_BUFFER, indices.length * 2, sb, GL20.GL_STATIC_DRAW);

        int stride = STRIDE_FLOATS * 4;
        Gdx.gl.glEnableVertexAttribArray(0);
        Gdx.gl.glVertexAttribPointer(0, 3, GL20.GL_FLOAT, false, stride, 0);
        Gdx.gl.glEnableVertexAttribArray(1);
        Gdx.gl.glVertexAttribPointer(1, 3, GL20.GL_FLOAT, false, stride, 12);
        Gdx.gl.glEnableVertexAttribArray(2);
        Gdx.gl.glVertexAttribPointer(2, 2, GL20.GL_FLOAT, false, stride, 24);
        Gdx.gl.glEnableVertexAttribArray(3);
        Gdx.gl.glVertexAttribPointer(3, 4, GL20.GL_FLOAT, false, stride, 32);

        Gdx.gl30.glBindVertexArray(0);
        Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, 0);
        Gdx.gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    public void expandWorldAabb(Matrix4 world, BoundingBox box) {
        if (localMin[0] > localMax[0]) {
            return;
        }
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    corner.set(
                        x == 0 ? localMin[0] : localMax[0],
                        y == 0 ? localMin[1] : localMax[1],
                        z == 0 ? localMin[2] : localMax[2]);
                    corner.mul(world);
                    box.ext(corner);
                }
            }
        }
    }

    public void dispose() {
        IntBuffer ids = BufferUtils.newIntBuffer(1);
        ids.put(0, vao);
        Gdx.gl30.glDeleteVertexArrays(1, ids);
        ids.put(0, vbo);
        Gdx.gl.glDeleteBuffers(1, ids);
        ids.put(0, ebo);
        Gdx.gl.glDeleteBuffers(1, ids);
    }
}
