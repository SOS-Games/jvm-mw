package io.github.jvmmw.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.List;

public final class ForwardRenderer {
    public static final int MAX_LIGHTS = 8;

    private final int program;
    private final int uMvp;
    private final int uModel;
    private final int uView;
    private final int uLightDir;
    private final int uAmbientLight;
    private final int uSunDiffuse;
    private final int uFogEnabled;
    private final int uFogStart;
    private final int uFogScale;
    private final int uFogColor;
    private final int uPointCount;
    private final int uPointPos;
    private final int uPointDiffuse;
    private final int uPointAtten;
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
    private final FloatBuffer pointPosBuf = BufferUtils.newFloatBuffer(MAX_LIGHTS * 3);
    private final FloatBuffer pointDiffBuf = BufferUtils.newFloatBuffer(MAX_LIGHTS * 3);
    private final FloatBuffer pointAttenBuf = BufferUtils.newFloatBuffer(MAX_LIGHTS * 4);
    private final int[] pickIdx = new int[MAX_LIGHTS];
    private final float[] pickD2 = new float[MAX_LIGHTS];
    private final Vector3 meshCenter = new Vector3();
    private final Matrix4 mvp = new Matrix4();

    public ForwardRenderer() {
        program = compile(VERT, FRAG);
        uMvp = Gdx.gl.glGetUniformLocation(program, "u_mvp");
        uModel = Gdx.gl.glGetUniformLocation(program, "u_model");
        uView = Gdx.gl.glGetUniformLocation(program, "u_view");
        uLightDir = Gdx.gl.glGetUniformLocation(program, "u_lightDir");
        uAmbientLight = Gdx.gl.glGetUniformLocation(program, "u_ambientLight");
        uSunDiffuse = Gdx.gl.glGetUniformLocation(program, "u_sunDiffuse");
        uFogEnabled = Gdx.gl.glGetUniformLocation(program, "u_fogEnabled");
        uFogStart = Gdx.gl.glGetUniformLocation(program, "u_fogStart");
        uFogScale = Gdx.gl.glGetUniformLocation(program, "u_fogScale");
        uFogColor = Gdx.gl.glGetUniformLocation(program, "u_fogColor");
        uPointCount = Gdx.gl.glGetUniformLocation(program, "u_pointCount");
        uPointPos = Gdx.gl.glGetUniformLocation(program, "u_pointPos");
        uPointDiffuse = Gdx.gl.glGetUniformLocation(program, "u_pointDiffuse");
        uPointAtten = Gdx.gl.glGetUniformLocation(program, "u_pointAtten");
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
        render(cam, root, null);
    }

    public void render(PerspectiveCamera cam, SceneNode root, CellLighting lighting) {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);
        Gdx.gl.glUseProgram(program);
        upload(uView, cam.view);
        if (lighting == null) {
            Gdx.gl.glUniform3f(uLightDir, 0.35f, 0.8f, 0.45f);
            Gdx.gl.glUniform3f(uAmbientLight, 0.35f, 0.35f, 0.35f);
            Gdx.gl.glUniform3f(uSunDiffuse, 1f, 1f, 1f);
            Gdx.gl.glUniform1i(uPointCount, 0);
            Gdx.gl.glUniform1i(uFogEnabled, 0);
        } else {
            Gdx.gl.glUniform3f(uLightDir, lighting.sunDir[0], lighting.sunDir[1], lighting.sunDir[2]);
            Gdx.gl.glUniform3f(uAmbientLight, lighting.ambient[0], lighting.ambient[1], lighting.ambient[2]);
            Gdx.gl.glUniform3f(uSunDiffuse, lighting.sunDiffuse[0], lighting.sunDiffuse[1], lighting.sunDiffuse[2]);
            Gdx.gl.glUniform1i(uFogEnabled, lighting.fogEnabled ? 1 : 0);
            Gdx.gl.glUniform1f(uFogStart, lighting.fogStart);
            Gdx.gl.glUniform1f(uFogScale, lighting.fogScale);
            Gdx.gl.glUniform3f(uFogColor, lighting.fogColor[0], lighting.fogColor[1], lighting.fogColor[2]);
        }
        Gdx.gl.glUniform1i(uBase, 0);
        Gdx.gl.glUniform1i(uDark, 1);
        Gdx.gl.glUniform1i(uDetail, 2);
        Gdx.gl.glUniform1i(uGlow, 3);
        drawNode(cam, root, false, lighting);
        drawNode(cam, root, true, lighting);
        Gdx.gl.glUseProgram(0);
        Gdx.gl30.glBindVertexArray(0);
    }

    private void drawNode(PerspectiveCamera cam, SceneNode node, boolean blendPass, CellLighting lighting) {
        if (!node.skipMeshes) {
            for (MeshInstance inst : node.meshes) {
                MeshGpu mesh = inst.mesh;
                if (mesh.alphaBlend != blendPass) {
                    continue;
                }
                if (lighting != null) {
                    bindClosestLights(node, mesh, lighting.lights);
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
            drawNode(cam, child, blendPass, lighting);
        }
    }

    private void bindClosestLights(SceneNode node, MeshGpu mesh, List<CellLight> lights) {
        if (mesh.localMin[0] > mesh.localMax[0]) {
            node.world.getTranslation(meshCenter);
        } else {
            meshCenter.set(
                (mesh.localMin[0] + mesh.localMax[0]) * 0.5f,
                (mesh.localMin[1] + mesh.localMax[1]) * 0.5f,
                (mesh.localMin[2] + mesh.localMax[2]) * 0.5f);
            meshCenter.mul(node.world);
        }
        Arrays.fill(pickD2, Float.POSITIVE_INFINITY);
        Arrays.fill(pickIdx, -1);
        for (int i = 0; i < lights.size(); i++) {
            CellLight light = lights.get(i);
            float dx = light.pos[0] - meshCenter.x;
            float dy = light.pos[1] - meshCenter.y;
            float dz = light.pos[2] - meshCenter.z;
            float d2 = dx * dx + dy * dy + dz * dz;
            int worst = 0;
            for (int s = 1; s < MAX_LIGHTS; s++) {
                if (pickD2[s] > pickD2[worst]) {
                    worst = s;
                }
            }
            if (d2 < pickD2[worst]) {
                pickD2[worst] = d2;
                pickIdx[worst] = i;
            }
        }
        pointPosBuf.clear();
        pointDiffBuf.clear();
        pointAttenBuf.clear();
        int count = 0;
        for (int s = 0; s < MAX_LIGHTS; s++) {
            if (pickIdx[s] < 0) {
                continue;
            }
            CellLight light = lights.get(pickIdx[s]);
            pointPosBuf.put(light.pos);
            pointDiffBuf.put(light.diffuse);
            pointAttenBuf.put(light.constant).put(light.linear).put(light.quadratic).put(light.radius);
            count++;
        }
        Gdx.gl.glUniform1i(uPointCount, count);
        if (count > 0) {
            pointPosBuf.flip();
            pointDiffBuf.flip();
            pointAttenBuf.flip();
            Gdx.gl.glUniform3fv(uPointPos, count, pointPosBuf);
            Gdx.gl.glUniform3fv(uPointDiffuse, count, pointDiffBuf);
            Gdx.gl.glUniform4fv(uPointAtten, count, pointAttenBuf);
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
        uniform mat4 u_view;
        out vec3 v_normal;
        out vec3 v_worldPos;
        out float v_viewZ;
        out vec2 v_uv;
        out vec4 v_color;
        void main() {
            v_uv = a_uv;
            v_color = a_color;
            vec4 world = u_model * vec4(a_pos, 1.0);
            v_worldPos = world.xyz;
            v_viewZ = (u_view * world).z;
            v_normal = mat3(u_model) * a_normal;
            gl_Position = u_mvp * vec4(a_pos, 1.0);
        }
        """;

    private static final String FRAG = """
        #version 330
        in vec3 v_normal;
        in vec3 v_worldPos;
        in float v_viewZ;
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
        uniform vec3 u_ambientLight;
        uniform vec3 u_sunDiffuse;
        uniform int u_pointCount;
        uniform vec3 u_pointPos[8];
        uniform vec3 u_pointDiffuse[8];
        uniform vec4 u_pointAtten[8];
        uniform int u_fogEnabled;
        uniform float u_fogStart;
        uniform float u_fogScale;
        uniform vec3 u_fogColor;
        uniform vec3 u_ambient;
        uniform vec3 u_diffuse;
        uniform vec3 u_emissive;
        uniform float u_matAlpha;
        uniform int u_colorMode;
        uniform int u_alphaTest;
        uniform int u_alphaFunc;
        uniform float u_alphaRef;
        out vec4 frag;
        float quickstep(float x) {
            x = clamp(x, 0.0, 1.0);
            x = 1.0 - x * x;
            x = 1.0 - x * x;
            return x;
        }
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
            vec3 sunDir = normalize(u_lightDir);
            float ndl = max(dot(n, sunDir), 0.0);
            vec3 lighting = amb * u_ambientLight + diff * ndl * u_sunDiffuse + emi;
            for (int i = 0; i < u_pointCount; ++i) {
                vec3 lightPos = u_pointPos[i] - v_worldPos;
                float dist = length(lightPos);
                float radius = u_pointAtten[i].w;
                if (dist > radius * 2.0) {
                    continue;
                }
                vec3 lightDir = lightPos / max(dist, 1e-5);
                float illumination = 1.0 / (u_pointAtten[i].x + u_pointAtten[i].y * dist
                    + u_pointAtten[i].z * dist * dist);
                illumination *= 1.0 - quickstep(dist / radius - 1.0);
                lighting += diff * max(dot(n, lightDir), 0.0) * u_pointDiffuse[i] * illumination;
            }
            lighting = max(lighting, vec3(0.0));
            tex.rgb *= lighting;
            if (u_useGlow != 0) {
                tex.rgb += texture(u_glow, v_uv).rgb;
            }
            if (u_fogEnabled != 0) {
                float fogValue = clamp((abs(v_viewZ) - u_fogStart) * u_fogScale, 0.0, 1.0);
                tex.rgb = mix(tex.rgb, u_fogColor, fogValue);
            }
            frag = tex;
        }
        """;
}
