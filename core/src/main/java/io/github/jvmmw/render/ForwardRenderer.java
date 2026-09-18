package io.github.jvmmw.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
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
    private final int uAmbientLight;
    private final int uBase;
    private final int uDark;
    private final int uDetail;
    private final int uGlow;
    private final int uUseDark;
    private final int uUseDetail;
    private final int uUseGlow;
    private final int uAmbient;
    private final int uDiffuse;
    private final int uEmissive;
    private final int uMatAlpha;
    private final int uColorMode;
    private final int uAlphaTest;
    private final int uAlphaFunc;
    private final int uAlphaRef;
    private final FloatBuffer matBuf = BufferUtils.newFloatBuffer(16);
    private final Matrix4 mvp = new Matrix4();

    public ForwardRenderer() {
        program = compile(VERT, FRAG);
        uMvp = Gdx.gl.glGetUniformLocation(program, "u_mvp");
        uModel = Gdx.gl.glGetUniformLocation(program, "u_model");
        uLightDir = Gdx.gl.glGetUniformLocation(program, "u_lightDir");
        uAmbientLight = Gdx.gl.glGetUniformLocation(program, "u_ambientLight");
        uBase = Gdx.gl.glGetUniformLocation(program, "u_base");
        uDark = Gdx.gl.glGetUniformLocation(program, "u_dark");
        uDetail = Gdx.gl.glGetUniformLocation(program, "u_detail");
        uGlow = Gdx.gl.glGetUniformLocation(program, "u_glow");
        uUseDark = Gdx.gl.glGetUniformLocation(program, "u_useDark");
        uUseDetail = Gdx.gl.glGetUniformLocation(program, "u_useDetail");
        uUseGlow = Gdx.gl.glGetUniformLocation(program, "u_useGlow");
        uAmbient = Gdx.gl.glGetUniformLocation(program, "u_ambient");
        uDiffuse = Gdx.gl.glGetUniformLocation(program, "u_diffuse");
        uEmissive = Gdx.gl.glGetUniformLocation(program, "u_emissive");
        uMatAlpha = Gdx.gl.glGetUniformLocation(program, "u_matAlpha");
        uColorMode = Gdx.gl.glGetUniformLocation(program, "u_colorMode");
        uAlphaTest = Gdx.gl.glGetUniformLocation(program, "u_alphaTest");
        uAlphaFunc = Gdx.gl.glGetUniformLocation(program, "u_alphaFunc");
        uAlphaRef = Gdx.gl.glGetUniformLocation(program, "u_alphaRef");
    }

    public void render(PerspectiveCamera cam, SceneNode root) {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);
        Gdx.gl.glUseProgram(program);
        Gdx.gl.glUniform3f(uLightDir, 0.35f, 0.8f, 0.45f);
        Gdx.gl.glUniform1f(uAmbientLight, 0.35f);
        Gdx.gl.glUniform1i(uBase, 0);
        Gdx.gl.glUniform1i(uDark, 1);
        Gdx.gl.glUniform1i(uDetail, 2);
        Gdx.gl.glUniform1i(uGlow, 3);
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
                bindUnit(GL20.GL_TEXTURE0, mesh.baseTex, mesh.baseWrapS, mesh.baseWrapT);
                bindUnit(GL20.GL_TEXTURE1, mesh.darkTex, mesh.darkWrapS, mesh.darkWrapT);
                bindUnit(GL20.GL_TEXTURE2, mesh.detailTex, mesh.detailWrapS, mesh.detailWrapT);
                bindUnit(GL20.GL_TEXTURE3, mesh.glowTex, mesh.glowWrapS, mesh.glowWrapT);
                Gdx.gl.glUniform1i(uUseDark, mesh.useDark ? 1 : 0);
                Gdx.gl.glUniform1i(uUseDetail, mesh.useDetail ? 1 : 0);
                Gdx.gl.glUniform1i(uUseGlow, mesh.useGlow ? 1 : 0);
                Gdx.gl.glUniform3f(uAmbient, mesh.ambient[0], mesh.ambient[1], mesh.ambient[2]);
                Gdx.gl.glUniform3f(uDiffuse, mesh.diffuse[0], mesh.diffuse[1], mesh.diffuse[2]);
                Gdx.gl.glUniform3f(uEmissive, mesh.emissive[0], mesh.emissive[1], mesh.emissive[2]);
                Gdx.gl.glUniform1f(uMatAlpha, mesh.matAlpha);
                Gdx.gl.glUniform1i(uColorMode, mesh.colorMode);
                Gdx.gl.glUniform1i(uAlphaTest, mesh.alphaTest ? 1 : 0);
                Gdx.gl.glUniform1i(uAlphaFunc, mesh.alphaFunc);
                Gdx.gl.glUniform1f(uAlphaRef, mesh.alphaRef);
                if (mesh.cull) {
                    Gdx.gl.glEnable(GL20.GL_CULL_FACE);
                } else {
                    Gdx.gl.glDisable(GL20.GL_CULL_FACE);
                }
                if (mesh.depthTest || mesh.depthWrite) {
                    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
                    Gdx.gl.glDepthFunc(mesh.depthTest ? GL20.GL_LEQUAL : GL20.GL_ALWAYS);
                } else {
                    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
                }
                if (mesh.alphaBlend) {
                    Gdx.gl.glEnable(GL20.GL_BLEND);
                    Gdx.gl.glBlendFunc(mesh.blendSrc, mesh.blendDst);
                    Gdx.gl.glDepthMask(mesh.noSorter && mesh.depthWrite);
                } else {
                    Gdx.gl.glDisable(GL20.GL_BLEND);
                    Gdx.gl.glDepthMask(mesh.depthWrite);
                }
                Gdx.gl30.glBindVertexArray(mesh.vao);
                Gdx.gl.glDrawElements(GL20.GL_TRIANGLES, mesh.indexCount, GL20.GL_UNSIGNED_SHORT, 0);
            }
        }
        for (SceneNode child : node.children) {
            drawNode(cam, child, blendPass);
        }
    }

    private static void bindUnit(int unit, int tex, int wrapS, int wrapT) {
        Gdx.gl.glActiveTexture(unit);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, tex);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, wrapS);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, wrapT);
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
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
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
        layout(location = 3) in vec4 a_color;
        uniform mat4 u_mvp;
        uniform mat4 u_model;
        out vec3 v_normal;
        out vec2 v_uv;
        out vec4 v_color;
        void main() {
            v_uv = a_uv;
            v_color = a_color;
            v_normal = mat3(u_model) * a_normal;
            gl_Position = u_mvp * vec4(a_pos, 1.0);
        }
        """;

    private static final String FRAG = """
        #version 330
        in vec3 v_normal;
        in vec2 v_uv;
        in vec4 v_color;
        uniform sampler2D u_base;
        uniform sampler2D u_dark;
        uniform sampler2D u_detail;
        uniform sampler2D u_glow;
        uniform int u_useDark;
        uniform int u_useDetail;
        uniform int u_useGlow;
        uniform vec3 u_lightDir;
        uniform float u_ambientLight;
        uniform vec3 u_ambient;
        uniform vec3 u_diffuse;
        uniform vec3 u_emissive;
        uniform float u_matAlpha;
        uniform int u_colorMode;
        uniform int u_alphaTest;
        uniform int u_alphaFunc;
        uniform float u_alphaRef;
        out vec4 frag;
        bool alphaPass(float a, float r, int f) {
            if (f == 0) return true;
            if (f == 1) return a < r;
            if (f == 2) return abs(a - r) < 1e-5;
            if (f == 3) return a <= r;
            if (f == 4) return a > r;
            if (f == 5) return abs(a - r) >= 1e-5;
            if (f == 6) return a >= r;
            if (f == 7) return false;
            return a <= r;
        }
        void main() {
            vec4 tex = texture(u_base, v_uv);
            vec3 amb = u_ambient;
            vec3 diff = u_diffuse;
            vec3 emi = u_emissive;
            float matA = u_matAlpha;
            if (u_colorMode == 1) {
                emi = v_color.rgb;
            } else if (u_colorMode == 2) {
                amb = v_color.rgb;
                diff = v_color.rgb;
                matA = v_color.a;
            }
            tex.a *= matA;
            if (u_useDark != 0) {
                tex *= texture(u_dark, v_uv);
            }
            if (u_alphaTest != 0 && !alphaPass(tex.a, u_alphaRef, u_alphaFunc)) discard;
            if (u_useDetail != 0) {
                tex.rgb *= texture(u_detail, v_uv).rgb * 2.0;
            }
            vec3 n = normalize(v_normal);
            float ndl = max(dot(n, normalize(u_lightDir)), 0.0);
            // objects.frag: tex * (diffuseLight + ambientLight + emission), then +glow
            vec3 lighting = amb * u_ambientLight + diff * ndl + emi;
            tex.rgb *= lighting;
            if (u_useGlow != 0) {
                tex.rgb += texture(u_glow, v_uv).rgb;
            }
            frag = tex;
        }
        """;
}
