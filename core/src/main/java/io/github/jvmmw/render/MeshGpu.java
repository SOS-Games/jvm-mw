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
    public final int vao;
    public final int vbo;
    public final int ebo;
    public final int indexCount;
    public int textureId;
    public boolean alphaBlend;
    public boolean alphaTest;
    public float alphaRef = 0.5f;
    public final float[] localMin = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
    public final float[] localMax = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
    private static final Vector3 corner = new Vector3();

    public MeshGpu(float[] interleaved, short[] indices) {
        indexCount = indices.length;
        for (int i = 0; i + 7 < interleaved.length; i += 8) {
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

        int stride = 8 * 4; // pos3 + n3 + uv2
        Gdx.gl.glEnableVertexAttribArray(0);
        Gdx.gl.glVertexAttribPointer(0, 3, GL20.GL_FLOAT, false, stride, 0);
        Gdx.gl.glEnableVertexAttribArray(1);
        Gdx.gl.glVertexAttribPointer(1, 3, GL20.GL_FLOAT, false, stride, 12);
        Gdx.gl.glEnableVertexAttribArray(2);
        Gdx.gl.glVertexAttribPointer(2, 2, GL20.GL_FLOAT, false, stride, 24);

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
