package io.github.jvmmw.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public final class ForwardRenderer {
    private final int program;
    private final int uMvp;
    private final int uModel;
    private final int uLightDir;
    private final int uTex;
    private final int uAlphaTest;
    private final int uAlphaRef;
    private final FloatBuffer matBuf = BufferUtils.newFloatBuffer(16);
    private final Matrix4 mvp = new Matrix4();

    public ForwardRenderer() {
        program = compile(VERT, FRAG);
        uMvp = Gdx.gl.glGetUniformLocation(program, "u_mvp");
        uModel = Gdx.gl.glGetUniformLocation(program, "u_model");
        uLightDir = Gdx.gl.glGetUniformLocation(program, "u_lightDir");
        uTex = Gdx.gl.glGetUniformLocation(program, "u_tex");
        uAlphaTest = Gdx.gl.glGetUniformLocation(program, "u_alphaTest");
        uAlphaRef = Gdx.gl.glGetUniformLocation(program, "u_alphaRef");
    }

    public void render(PerspectiveCamera cam, SceneNode root) {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);
        Gdx.gl.glUseProgram(program);
        Gdx.gl.glUniform3f(uLightDir, 0.35f, 0.8f, 0.45f);
        Gdx.gl.glUniform1i(uTex, 0);
        drawNode(cam, root, false);
        drawNode(cam, root, true);
        Gdx.gl.glUseProgram(0);
        Gdx.gl30.glBindVertexArray(0);
    }

    private void drawNode(PerspectiveCamera cam, SceneNode node, boolean blendPass) {
        if (!node.skipMeshes) {
            for (MeshInstance inst : node.meshes) {
                MeshGpu mesh = inst.mesh;
                if (mesh.alphaBlend != blendPass) {
                    continue;
                }
                mvp.set(cam.combined).mul(node.world);
                upload(uMvp, mvp);
                upload(uModel, node.world);
                Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
                Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, mesh.textureId);
                Gdx.gl.glUniform1i(uAlphaTest, mesh.alphaTest ? 1 : 0);
                Gdx.gl.glUniform1f(uAlphaRef, mesh.alphaRef);
                boolean twoSided = mesh.alphaTest || mesh.alphaBlend;
                if (twoSided) {
                    Gdx.gl.glDisable(GL20.GL_CULL_FACE);
                } else {
                    Gdx.gl.glEnable(GL20.GL_CULL_FACE);
                }
                if (mesh.alphaBlend) {
                    Gdx.gl.glEnable(GL20.GL_BLEND);
                    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                    Gdx.gl.glDepthMask(false);
                } else {
                    Gdx.gl.glDisable(GL20.GL_BLEND);
                    Gdx.gl.glDepthMask(true);
                }
                Gdx.gl30.glBindVertexArray(mesh.vao);
                Gdx.gl.glDrawElements(GL20.GL_TRIANGLES, mesh.indexCount, GL20.GL_UNSIGNED_SHORT, 0);
            }
        }
        for (SceneNode child : node.children) {
            drawNode(cam, child, blendPass);
        }
    }

    private void upload(int loc, Matrix4 m) {
        matBuf.clear();
        matBuf.put(m.val);
        matBuf.flip();
        Gdx.gl.glUniformMatrix4fv(loc, 1, false, matBuf);
    }

    public void dispose() {
        Gdx.gl.glDeleteProgram(program);
    }

    public static void resetForScene2d() {
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, 0);
        Gdx.gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, 0);
        if (Gdx.gl30 != null) {
            Gdx.gl30.glBindVertexArray(0);
        }
    }

    private static int compile(String vert, String frag) {
        int vs = Gdx.gl.glCreateShader(GL20.GL_VERTEX_SHADER);
        Gdx.gl.glShaderSource(vs, vert);
        Gdx.gl.glCompileShader(vs);
        checkShader(vs, "vertex");
        int fs = Gdx.gl.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        Gdx.gl.glShaderSource(fs, frag);
        Gdx.gl.glCompileShader(fs);
        checkShader(fs, "fragment");
        int p = Gdx.gl.glCreateProgram();
        Gdx.gl.glAttachShader(p, vs);
        Gdx.gl.glAttachShader(p, fs);
        Gdx.gl.glLinkProgram(p);
        checkProgram(p);
        Gdx.gl.glDeleteShader(vs);
        Gdx.gl.glDeleteShader(fs);
        return p;
    }

    private static void checkShader(int sh, String kind) {
        IntBuffer status = BufferUtils.newIntBuffer(1);
        Gdx.gl.glGetShaderiv(sh, GL20.GL_COMPILE_STATUS, status);
        if (status.get(0) == GL20.GL_FALSE) {
            throw new IllegalStateException(kind + " shader: " + Gdx.gl.glGetShaderInfoLog(sh));
        }
    }

    private static void checkProgram(int p) {
        IntBuffer status = BufferUtils.newIntBuffer(1);
        Gdx.gl.glGetProgramiv(p, GL20.GL_LINK_STATUS, status);
        if (status.get(0) == GL20.GL_FALSE) {
            throw new IllegalStateException("program: " + Gdx.gl.glGetProgramInfoLog(p));
        }
    }

    private static final String VERT = """
        #version 330
        layout(location = 0) in vec3 a_pos;
        layout(location = 1) in vec3 a_normal;
        layout(location = 2) in vec2 a_uv;
        uniform mat4 u_mvp;
        uniform mat4 u_model;
        out vec3 v_normal;
        out vec2 v_uv;
        void main() {
            v_uv = a_uv;
            v_normal = mat3(u_model) * a_normal;
            gl_Position = u_mvp * vec4(a_pos, 1.0);
        }
        """;

    private static final String FRAG = """
        #version 330
        in vec3 v_normal;
        in vec2 v_uv;
        uniform sampler2D u_tex;
        uniform vec3 u_lightDir;
        uniform int u_alphaTest;
        uniform float u_alphaRef;
        out vec4 frag;
        void main() {
            vec4 tex = texture(u_tex, v_uv);
            if (u_alphaTest != 0 && tex.a < u_alphaRef) discard;
            vec3 n = normalize(v_normal);
            float ndl = max(dot(n, normalize(u_lightDir)), 0.0);
            vec3 lit = tex.rgb * (0.35 + 0.65 * ndl);
            frag = vec4(lit, tex.a);
        }
        """;
}
